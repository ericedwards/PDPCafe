//
// Copyright (c) 2001 Eric A. Edwards
//
// This file is part of PDPCafe.
//
// Permission is hereby granted, free of charge, to any person obtaining a copy
// of this software and associated documentation files (the "Software"), to deal
// in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
// copies of the Software, and to permit persons to whom the Software is
// furnished to do so, subject to the following conditions:

// The above copyright notice and this permission notice shall be included in all
// copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
// SOFTWARE.
//
// Unibus.java - The center of all evil, also implements main memory.
//

package PDPCafe;

import java.util.*;

public class Unibus implements UnibusDevice {

	private static final int MEMSIZE = 124;			// size in Kwords
	private static final int MAXEVENTS = 10;		// max events pending
	private static final int MAXINTERRUPTS = 10;	// max interrupts pending

	private short mem[];						// the memory array
	private static Unibus theInstance = null;	// the Unibus singleton
	private Vector devices;						// Unibus devices
	private UnibusEvent[] events;				// Unibus device events
	private UnibusInterrupt[] interrupts;		// Unibus device interrupts

	private Unibus() {
		mem = new short[MEMSIZE * 1024];
		for (int x = 0; x < (MEMSIZE * 1024); ++x) {
			mem[x] = (short) (x & 0177777);
		}
		devices = new Vector(10, 10);
		events = new UnibusEvent[MAXEVENTS];
		interrupts = new UnibusInterrupt[MAXINTERRUPTS];
	}

	public static final synchronized Unibus instance() {
		if (theInstance == null) {
			theInstance = new Unibus();
		}
		return theInstance;
	}

	public final void registerDevice(UnibusDeviceInfo deviceInfo) {
		devices.addElement(deviceInfo);
		// later check for overlap and any other conflicts
	}

	public final void reset() {
		for (int i = 0; i < devices.size(); ++i) {
			UnibusDeviceInfo d = (UnibusDeviceInfo) devices.elementAt(i);
			d.device.reset();
		}
		for (int i = 0; i < MAXEVENTS; ++i) {
				events[i] = null;
		}
		for (int i = 0; i < MAXINTERRUPTS; ++i) {
				interrupts[i] = null;
		}
	}

	public final short read(int addr) throws Trap {
		if (addr < (MEMSIZE * 2 * 1024)) {
			return mem[addr>>1];
		} else {
			for (int i = 0; i < devices.size(); ++i) {
				UnibusDeviceInfo d = (UnibusDeviceInfo) devices.elementAt(i);
				if ((addr >= d.base) && (addr < (d.base + (d.size * 2)))) {
					return d.device.read(addr);
				}
			}
			throw new Trap(Trap.UnibusTimeout);
		}
	}

	public final void write(int addr, short data) throws Trap {
		if (addr < (MEMSIZE * 2 * 1024)) {
			mem[addr>>1] = data;
		} else {
			for (int i = 0; i < devices.size(); ++i) {
				UnibusDeviceInfo d = (UnibusDeviceInfo) devices.elementAt(i);
				if ((addr >= d.base) && (addr < (d.base + (d.size * 2)))) {
					d.device.write(addr, data);
					return;
				}
			}
			throw new Trap(Trap.UnibusTimeout);
		}
	}

	public final void writebyte(int addr, byte data) throws Trap {
		if (addr < (MEMSIZE * 2 * 1024)) {
			int t = mem[addr >> 1];
			int s = data & 0377;
			if ((addr & 1) == 0) {
				t &= 0177400;
				t |= s;
			} else {
				t &= 0377;
				t |= s << 8;
			}
			mem[addr>>1] = (short) t;
		} else {
			for (int i = 0; i < devices.size(); ++i) {
				UnibusDeviceInfo d = (UnibusDeviceInfo) devices.elementAt(i);
				if ((addr >= d.base) && (addr < (d.base + (d.size * 2)))) {
					d.device.writebyte(addr, data);
					return;
				}
			}
			throw new Trap(Trap.UnibusTimeout);
		}
	}

	public void eventService(int data) {
		// do nothing
	}

	public void interruptService() {
	}

	public synchronized void scheduleEvent(UnibusDevice device,
		int delay, int data) {
		UnibusEvent e = new UnibusEvent(device, delay, data);
		for (int i = 0; i < MAXEVENTS; ++i) {
			if (events[i] == null) {
				events[i] = e;
				return;
			}
		}
		Thread.dumpStack();
		System.out.println("Out of events!");
	}

	public synchronized void cancelEvents(UnibusDevice device) {
		for (int i = 0; i < MAXEVENTS; ++i) {
			if ((events[i] != null) && (events[i].device == device)) {
				events[i] = null;
			}
		}
	}

	public synchronized void runEvents(int count) {
		for (int i = 0; i < MAXEVENTS; ++i) {
			if (events[i] != null) {
				if (events[i].delay < count) {
					events[i].device.eventService(events[i].data);
					events[i] = null;
				} else {
					events[i].delay -= count;
				}
			}
		}
	}

	public synchronized void scheduleInterrupt(UnibusDevice device,
	int level, int vector) {
		for (int i = 0; i < MAXINTERRUPTS; ++i) {
			if ((interrupts[i] != null) && (interrupts[i].device == device) && 
				(interrupts[i].level == level) && 
				(interrupts[i].vector == vector)) {
					return;
			}
		}
		UnibusInterrupt n = new UnibusInterrupt(device, level, vector);
		for (int i = 0; i < MAXINTERRUPTS; ++i) {
			if (interrupts[i] == null) {
				interrupts[i] = n;
				return;
			}
		}
		Thread.dumpStack();
		System.out.println("Out of interrupts!");
	}

	public synchronized void cancelInterrupt(UnibusDevice device,
	int level, int vector) {
		for (int i = 0; i < MAXINTERRUPTS; ++i) {
			if ((interrupts[i] != null) && (interrupts[i].device == device) && 
				(interrupts[i].level == level) && 
				(interrupts[i].vector == vector)) {
					interrupts[i] = null;
			}
		}
	}

	public synchronized UnibusInterrupt runInterrupts(int level) {
		UnibusInterrupt n;
		for (int i = 0; i < MAXINTERRUPTS; ++i) {
			if (interrupts[i] != null) {
				if (interrupts[i].level > level) {
					n = interrupts[i];
					interrupts[i] = null;
					return n;
				}
			}
		}
		return null;
	}

	public synchronized boolean waitingInterrupt(int level) {
		UnibusInterrupt n;
		for (int i = 0; i < MAXINTERRUPTS; ++i) {
			if (interrupts[i] != null) {
				if (interrupts[i].level > level) {
					return true;
				}
			}
		}
		return false;
	}

	public void dumpDevices() {
		for (int i = 0; i < devices.size(); ++i) {
			UnibusDeviceInfo d = (UnibusDeviceInfo) devices.elementAt(i);
			System.out.println(d.name + " "
				+ d.device.getClass().getName() + " "
				+ Integer.toOctalString(d.base) + " "
				+ Integer.toOctalString(d.size));
		}
	}
}

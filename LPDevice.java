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
// LPDevice - LP11 Printer Simulation
//

package PDPCafe;

import java.io.*;

public class LPDevice implements UnibusDevice {

	// Unibus interface definitions:

	private static final int LP_BASE = 0777514;	// default address
	private static final int LP_SIZE = 2;		// two registers
	private static final int LP_VECTOR = 0200;	// vector 200
	private static final int LP_BRLEVEL = 4;	// BR4 for interrupts
	private static final int LP_DELAY = 500;	// 1000 instructions

	// Control register definitions:

	private static final int LP_ERR = 0100000;
	private static final int LP_RDY = 0200;
	private static final int LP_IE = 0100;
	
	// Controller register images:
	
	private int lpcs;
	private int lpdr;

	// Internal controller information:

	private UnibusDeviceInfo info;
	private Unibus unibus;
	private FileWriter file;

	// LPDevice()

	public LPDevice() {
		this(LP_BASE, LP_SIZE, "");
	}

	// LPDevice()

	public LPDevice(int base, int size, String options) {
		info = new UnibusDeviceInfo(this, base, size, "LP11", false);
		lpcs = LP_ERR;
		file = null;
		unibus = Unibus.instance();
		unibus.registerDevice(info);
	}

	// assign()

	public void assign(String path) throws java.io.IOException {
		if (file != null) {
			file.close();
		}
		lpcs = LP_ERR;
		file = new FileWriter(path, true);		// just append
		lpcs = LP_RDY;
	}

	// reset()

	public void reset() {
		if (file != null) {
			lpcs = LP_RDY;
		} else {
			lpcs = LP_ERR;
		}
	}

	// read()

	public short read(int addr) throws Trap {
		int data = 0;
		switch (addr - info.base) {
		case 0:
			data = lpcs;
			break;
		case 2:
			data = 0;	// always reads zeros
			break;
		default:
			throw new Trap(Trap.UnibusTimeout);
		}
		return(short) data;
	}

	// write()

	public void write(int addr, short shortData) throws Trap {
		int data = ((int) shortData) & 0177777;
		switch (addr - info.base) {
		case 0:
			if (((lpcs & LP_IE) == 0) && ((data & LP_IE) != 0) &&
			((lpcs & (LP_RDY|LP_ERR)) != 0)) { 
				unibus.scheduleEvent(this, LP_DELAY, 0);
			}
			lpcs &= ~LP_IE;
			lpcs |= (data & LP_IE);
			break;
		case 2:
			if ((lpcs & LP_RDY) != 0) {
				try {
					file.write(data & 0177);
					file.flush();
				} catch (IOException e1) {
					try {
						file.close();
					} catch (IOException e2) {
						// ignore 2nd failure
					}
					file = null;
					lpcs |= LP_ERR;
				}
				if (file != null) {
					lpcs &= ~LP_RDY;
				}
				unibus.scheduleEvent(this, LP_DELAY, 0);
			} else {
				// overrun errors checked?
			}
			break;
		default:
			throw new Trap(Trap.UnibusTimeout);
		}
	}

	// writebyte()

	public void writebyte(int addr, byte data) throws Trap {
		if (( addr & 1) != 0) {
			return;
		}
		write(addr, (short) data);
	}

	// eventService()

	public void eventService(int data) {
		lpcs &= ~(LP_RDY|LP_ERR);
		if (file != null) {
			lpcs |= LP_RDY;
		} else {
			lpcs |= LP_ERR;
		}
		if ((lpcs & LP_IE) != 0) {
			unibus.scheduleInterrupt(this, LP_BRLEVEL, LP_VECTOR);
		}
	}

	// interruptService()

	public void interruptService() {
		// do nothing
	}
}

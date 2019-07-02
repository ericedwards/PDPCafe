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
// KWDevice - KW11 (Line Clock) Device Simulation.
//

package PDPCafe;

class KWDevice implements UnibusDevice {

	private static final int KW_BASE = 0777546;
	private static final int KW_SIZE = 1;
	private static final int KW_IE = 0100;
	private static final int KW_HERTZ = 60;
	private static final int KW_BRLEVEL = 6;
	private static final int KW_VECTOR = 0100;

	private static KWDevice theInstance = null;
	private int csr;
	private Unibus unibus;
	private long startTime;
	private int interrupts;

	private KWDevice() {
		csr = 0;
		startTime = System.currentTimeMillis();
		interrupts = 0;
		UnibusDeviceInfo info;
		unibus = Unibus.instance();
		info = new UnibusDeviceInfo(this, KW_BASE, KW_SIZE, "KW11L", true);
		unibus.registerDevice(info);
	}

	public static final synchronized KWDevice instance() {
		if (theInstance == null) {
			theInstance = new KWDevice();
		}
		return theInstance;
	}

	public void reset() {
		csr = 0;
	}

	public short read(int addr) throws Trap {
		return (short) csr;
	}

	public void write(int addr, short shortData) throws Trap {
		int data = ((int) shortData) & 0177777;
		csr &= ~(KW_IE);				// mask r/o
		csr |= (data & KW_IE);	// writeable bits only
	}

	public void writebyte(int addr, byte data) throws Trap {
		int t = ((int) read(addr & 0777776)) & 0177777;
		int s = ((int) data) & 0377;
		if ((addr & 1) == 0) {
			t &= 0177400;
			t |= s;
		} else {
			t &= 0377;
			t |= s << 8;
		}
		write(addr & 0777776, (short) t);
	}

	public void eventService(int data) {
		if ((csr & KW_IE) != 0) {
			csr &= ~KW_IE;
			Unibus u = Unibus.instance();
			u.scheduleInterrupt(this, KW_BRLEVEL, KW_VECTOR);
		}
	}

	public void interruptService() {
		// do nothing
	}

	public void pollClock() {
		long elapsedTime = System.currentTimeMillis() - startTime;
		if (interrupts < ((KW_HERTZ * elapsedTime) / 1000)) {
			++interrupts;
			unibus.scheduleEvent(this, 0, 0);
		}
	}
}

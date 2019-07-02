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
// BootDevice - Boot ROM device.
//

package PDPCafe;

public class BootDevice implements UnibusDevice {

	private static final int DEFAULT_BASE = 0773000;	// base address
	private static final int DEFAULT_SIZE = 256;		// number of registers

	private static final short tm_storage[] = {
		(short) 0012700,	// mov	#172526,r0
		(short) 0172526,
		(short) 0010040,	// mov	r0,-(r0)
		(short) 0012740,	// mov	#60003,-(r0)
		(short) 0060003,
		(short) 0012700,	// mov	#172522,r0
		(short) 0172522,
		(short) 0105710,	// tstb	(r0)
		(short) 0100376,	// bpl	-1
		(short) 0005000,	// clr	r0
		(short) 0000110		// jmp	(r0)
	};

	private static final short rl_storage[] = {
		(short) 0012737,	// mov	#10,174400
		(short) 0000010,
		(short) 0174400,
		(short) 0105737,	// tstb	174400
		(short) 0174400,
		(short) 0100375,	// bpl	-2
		(short) 0013700,	// mov	174406,r0
		(short) 0174406,
		(short) 0042700,	// bic	#177,r0
		(short) 0000177,
		(short) 0052700,	// bis	#1,r0
		(short) 0000001,
		(short) 0010037,	// mov	r0,174404
		(short) 0174404,
		(short) 0012737,	// mov	#6,174400
		(short) 0000006,
		(short) 0174400,
		(short) 0105737,	// tstb	174400
		(short) 0174400,
		(short) 0100375,	// bpl	-2
		(short) 0012700,	// mov	#174406,r0
		(short) 0174406,
		(short) 0012710,	// mov	#177400,(r0)
		(short) 0177400,
		(short) 0005040,	// clr	-(r0)
		(short) 0005040,	// clr	-(r0)
		(short) 0012740,	// mov	#14,-(r0)
		(short) 0000014,
		(short) 0105737,	// tstb	174400
		(short) 0174400,
		(short) 0100375,	// bpl	-2
		(short) 0005000,	// clr	r0
		(short) 0000110		// jmp	(r0)
	};

	private UnibusDeviceInfo info;
	private String options;

	public BootDevice() {
		this(DEFAULT_BASE, DEFAULT_SIZE, "");
	}

	public BootDevice(int base, int size, String options) {
		this.options = options;
		Unibus u = Unibus.instance();
		info = new UnibusDeviceInfo(this, base, size, "BOOTROMS", false);
		u.registerDevice(info);
	}

	public void reset() {
	}

	public short read(int addr) {
		int offset;

		offset = ((addr - info.base) & 0177) >> 1;
		switch (((addr - info.base) & 0600) >> 7) {
		case 0:
			return rl_storage[offset % rl_storage.length];
		case 1:
			return tm_storage[offset % tm_storage.length];
		default:
			return 0;
		}
	}

	public void write(int addr, short data) {
		// don't actually write
	}

	public void writebyte(int addr, byte data) {
		// don't actually write
	}

	public void eventService(int data) {
		// do nothing
	}

	public void interruptService() {
		// do nothing
	}
}

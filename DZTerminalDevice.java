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
// DZTerminalDevice.java - DZ-11 Terminal Mux Device.
//

package PDPCafe;

import java.io.*;
import java.net.*;
import java.util.*;

public class DZTerminalDevice implements UnibusDevice {

	private static final int DZ_BASE = 0760100;
	private static final int DZ_SIZE = 4;
	private static final int DZ_TELNET_PORT = 2002;

	private static final int DZ_BRLEVEL = 4;
	private static final int DZ_RVECTOR = 0310;
	private static final int DZ_TVECTOR = 0314;

	private static final int DZ_LINES = 8;
	private static final int DZ_MAXSILO = 64;

	private UnibusDeviceInfo info;
	private Unibus u;
	private String options;
	private Socket socket;

	private int csr;
	private int lpr[];
	private int tcr;
	private int msr;
	private int tdr;
	private Vector silo;

	public DZTerminalDevice() {
		this(DZ_BASE, DZ_SIZE, "");
	}

	public DZTerminalDevice(int base, int size, String options) {
		this.options = options;
		u = Unibus.instance();
		info = new UnibusDeviceInfo(this, base, size, "DZ11", false);
		u.registerDevice(info);
		lpr = new int[DZ_LINES];
		silo = new Vector(DZ_MAXSILO);
	}

	public void reset() {
	}

	public short read(int addr) throws Trap {
		int data;
		switch(addr - info.base) {
		case 0:
			data = 0;
			break;
		default:
			throw new Trap(Trap.UnibusTimeout);
		}
		return (short) data;
	}

	public void write(int addr, short data) throws Trap {
		int temp = ((int) data) & 0177777;
		switch (addr - info.base) {
		default:
			throw new Trap(Trap.UnibusTimeout);
		}
	}

	public void writebyte(int addr, byte data) throws Trap {
	}

	public void eventService(int data) {
	}

	public void interruptService() {
	}

}

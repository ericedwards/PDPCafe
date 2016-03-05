//
// Copyright (C) 2001  Eric A. Edwards
//
// This file is part of PDPCafe.
//
// PDPCafe is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; either version 2 of the License, or
// (at your option) any later version.
//
// PDPCafe is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with PDPCafe; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
//
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

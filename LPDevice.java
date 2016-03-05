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

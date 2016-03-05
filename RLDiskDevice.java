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
// RLDiskDevice - RL11/RL01/RL02 Disk Simulation.
//

package PDPCafe;

import java.io.*;

public class RLDiskDevice implements UnibusDevice {

	// Unibus interface definitions.

	private static final int RL_BASE = 0774400;		// default address
	private static final int RL_SIZE = 4;			// four registers
	private static final int RL_VECTOR = 0160;		// default interrupt vector
	private static final int RL_BRLEVEL = 5;		// default bus request level
	private static final int RL_DELAY = 100;		// in instructions

	// Control register definitions.

	private static final int RL_DRDY = 01;
	private static final int RL_NOP = 0;
	private static final int RL_WRCK = 02;
	private static final int RL_GSTAT = 04;
	private static final int RL_SEEK = 06;
	private static final int RL_RDHEAD = 010;
	private static final int RL_WCOM = 012;
	private static final int RL_RCOM = 014;
	private static final int RL_RDNOCK = 016;
	private static final int RL_IE = 0100;
	private static final int RL_CRDY = 0200;
	private static final int RL_OPI = 02000;
	private static final int RL_DCRC = 04000;
	private static final int RL_HNF = 010000;
	private static final int RL_NXM = 020000;
	private static final int RL_DE = 040000;
	private static final int RL_CE = 0100000;

	private static final int RL_VC = 01000;
	private static final int RL_BH = 010;
	private static final int RL_HO = 020;
	private static final int RL_CO = 040;
	private static final int RL_LOCK = 05;
	private static final int RL_02 = 0200;

	private static final int RL_RESET = 010;

	// Internal definitions.

	private static final int RL_TYPE_NORL = 0;	// no drive
	private static final int RL_TYPE_RL01 = 1;	// drive type is RL01
	private static final int RL_TYPE_RL02 = 2;	// drive type is RL02
	private static final int MAX_RL = 4;		// max drives per controller

	// Drive geometry definitions.

	private static final int RL_CYL_RL01 = 256;
	private static final int RL_CYL_RL02 = 512;
	private static final int RL_NUM_SECT = 40;
	private static final int RL_NUM_HEADS = 2;
	private static final int RL_WORDS_SECTOR = 128;
	private static final int RL_BYTES_SECTOR = (RL_WORDS_SECTOR * 2);
	private static final int RL_BYTES_TRACK = (RL_BYTES_SECTOR * RL_NUM_SECT);
	private static final int RL_BYTES_CYL = (RL_BYTES_TRACK * RL_NUM_HEADS);
	private static final int RL_SIZE_RL01 = (RL_BYTES_CYL * RL_CYL_RL01);
	private static final int RL_SIZE_RL02 = (RL_BYTES_CYL * RL_CYL_RL02);

	// Controller register images.

	private int csr;
	private int bar;
	private int dar;
	private int mpr;

	// Internal device data.

	private UnibusDeviceInfo info;	// generic device information
	private int drive;				// drive number for current operation
	private RLDiskDrive[] drives;	// per drive information
	private byte[] buffer;			// sector data buffer
	private Unibus unibus;			// the Unibus device

	// Code.

	public RLDiskDevice() {
		this(RL_BASE, RL_SIZE, "");
	}

	public RLDiskDevice(int base, int size, String options) {
		info = new UnibusDeviceInfo(this, base, size, "RL11", false);
		csr = RL_CRDY;
		bar = 0;
		dar = 0;
		mpr = 0;
		drive = 0;
		drives = new RLDiskDrive[MAX_RL];
		buffer = new byte[RL_BYTES_SECTOR];
		for (int i = 0; i < drives.length; ++i) {
			drives[i] = new RLDiskDrive();
			drives[i].exists = RL_TYPE_NORL;
			drives[i].cylinder = 0;
			drives[i].head = 0;
			drives[i].error = false;
			drives[i].file = null;					// force the issue
		}
		unibus = Unibus.instance();
		unibus.registerDevice(info);
	}

	public void assign(int unit, String path) throws java.io.IOException {
		if ((unit >= 0) && (unit < drives.length)) {
			RLDiskDrive drive = drives[unit];
			if (drive.file != null) {
				drive.file.close();
			}
			drive.exists = RL_TYPE_NORL;
			drive.file = new RandomAccessFile(path, "rw");
			if (drive.file.length() == RL_SIZE_RL01) {
				drive.exists = RL_TYPE_RL01;
			} else if (drive.file.length() == RL_SIZE_RL02) {
				drive.exists = RL_TYPE_RL02;
			} else {
				drive.file.close();
				throw new java.io.IOException();
			}
		} else {
			throw new java.io.IOException();
		}
	}

	public void makedisk(String path, String options) throws java.io.IOException {
		FileOutputStream f = new FileOutputStream(path, false);
		byte[] b = new byte[256];
		for (int i = 0; i < (RL_SIZE_RL01/RL_BYTES_SECTOR); ++i) {
			f.write(b);
		}
		f.close();
	}

	// read() - Handle the reading of an RL11 register.	This is
	// simple since the RL11 doesn't change the registers as a result
	// of the read.	The multi-purpose register actually does change,
	// but we'll cheat by not fully implementing it yet.

	public short read(int addr) throws Trap {
		int data = 0;
		switch (addr - info.base) {
		case 0:
			data = csr;
			break;
		case 2:
			data = bar;
			break;
		case 4:
			data = dar;
			break;
		case 6:
			data = mpr;
			break;
		default:
			throw new Trap(Trap.UnibusTimeout);
		}
		return (short) data;
	}

	// write() - Handle a write to one of the RL11
	// registers.	All registers except the csr are just
	// handled by saving the value.	If the csr is
	// written with the RL_CRDY bit cleared, rl_exec is
	// called to handle the command.

	public void write(int addr, short shortData) throws Trap {
		int data = ((int) shortData) & 0177777;
		switch(addr - info.base) {
		case 0:
			csr = data;
			if ((csr & RL_CRDY) == 0) {
				exec();
			}
			break;
		case 2:
			bar = data & 0177776;				// lowest bit always zero
			break;
		case 4:
			dar = data;
			break;
		case 6:
			mpr = data;
			break;
		default:
			throw new Trap(Trap.UnibusTimeout);
		}
	}

	// writebyte() - Ok, who's the retard?	Byte writes should
	// probably be allowed to the RL11, but I'm too lazy.

	public void writebyte(int addr, byte data) throws Trap {
		throw new Trap(Trap.Unimplemented);
	}

	// reset()

	public void reset() {
		csr = RL_CRDY;
		bar = 0;
		dar = 0;
		mpr = 0;
		drive = 0;
		for (int i = 0; i < drives.length; ++i) {
			drives[i].cylinder = 0;
			drives[i].head = 0;
			drives[i].error = false;
		}
	}

	private void exec() {
		int delay = RL_DELAY;						// default operation time
		drive = (csr >> 8 ) & 3;
		if (drives[drive].exists == RL_TYPE_NORL) {
			drives[drive].error = true;				// signal an error
			delay = 0;								// finish quickly
			if ((csr & 016 ) == RL_GSTAT ) {
				mpr = RL_CO|RL_BH;					// say the cover is open
			}
		} else {
			switch (csr & 016) {
			case RL_NOP:
				delay = 0;		// null command, do nothing and finish quickly
				break;
			case RL_GSTAT:
				mpr = RL_HO|RL_BH|RL_LOCK;				// drive is cool
				if (drives[drive].exists == RL_TYPE_RL02) {
					mpr |= RL_02;
				}
				if ((dar & RL_RESET) != 0) {			// clear drive error
					drives[drive].error = false;
				}
				delay = 0;								// finish quickly
				break;
			case RL_SEEK:
				drives[drive].error = doSeek();
				break;
			case RL_RDHEAD:
				mpr = drives[drive].cylinder << 7;
				mpr |= drives[drive].head << 6;
				drives[drive].error = false;
				break;
			case RL_RCOM:
				drives[drive].error = doReadWrite(false);
				break;
			case RL_WCOM:
				drives[drive].error = doReadWrite(true);
				break;
			case RL_WRCK:
			case RL_RDNOCK:
			default:
				delay = 0;								// unsupported command
				break;
			}
		}
		unibus.scheduleEvent(this, delay, 0);
	}

	// eventService() - Finish the current command.	Set the error bits and
	// mark the controller ready.

	public void eventService(int data) {
		if (drives[drive].error) {
			csr |= RL_DE;
			csr &= ~RL_DRDY;
		} else {
			csr &= ~RL_DE;
			csr |= RL_DRDY;
		}
		if ((csr & 0176000) != 0) {
			csr |= RL_CE;
		}
		csr |= RL_CRDY;
		if ((csr & RL_IE) != 0) {
			unibus.scheduleInterrupt(this, RL_BRLEVEL, RL_VECTOR);
		}
	}

	public void interruptService() {
	}

	// doSeek() - Do the mechanics of a controller seek command.	Return
	// non-zero on any kind of failure.

	private boolean doSeek() {
		int max;
		drives[drive].head = (dar >> 4) & 1;
		int diff = (dar >> 7);
		if (drives[drive].exists == RL_TYPE_RL01) {
			max = RL_CYL_RL01;
		} else {
			max = RL_CYL_RL02;
		}
		if ((dar & 04) != 0) {
			if ((drives[drive].cylinder + diff) >= max) {
				return true;
			} else {
				drives[drive].cylinder += diff;
			}
		} else {
			if (diff > drives[drive].cylinder) {
				return true;
			} else {
				drives[drive].cylinder -= diff;
			}
		}
		return false;
	}

	// doReadWrite() - Do a read or write by simulating DMA and doing
	// file I/O to the disk file.

	private boolean doReadWrite(boolean write) {
		if (drives[drive].cylinder != (dar >> 7)) {
			csr |= RL_HNF;
			return true;
		}
		if (drives[drive].head != ((dar >> 6) & 1)) {
			csr |= RL_HNF;
			return true;
		}
		int sector = dar & 077;
		int addr = bar + ((csr & 060) << 12);
		int count = (0177777 - mpr) + 1;
		if (sector >= RL_NUM_SECT) {
			csr |= RL_HNF;
			return true;
		}
		int offset = ((drives[drive].cylinder * RL_BYTES_CYL) +
			(drives[drive].head * RL_BYTES_TRACK) +
			(sector * RL_BYTES_SECTOR));
		try {
			drives[drive].file.seek(offset);
		} catch (IOException e) {
			csr |= RL_HNF;
			return true;
		}
		int temp;
		try {
			while (count != 0) {
				if (write) {
					for (int i = 0; (i < RL_BYTES_SECTOR) && (count != 0); count--) {
						temp = unibus.read(addr);
						buffer[i] = (byte)(temp & 0xff);
						buffer[i + 1] = (byte)(temp >> 8);
						addr += 2;
						i += 2;
					}
					drives[drive].file.write(buffer);
				} else {
					drives[drive].file.readFully(buffer);
					for (int i = 0; (i < RL_BYTES_SECTOR) && (count != 0); count--) {
						temp = buffer[i + 1] << 8;
						temp += ((int) buffer[i]) & 0xff;
						unibus.write(addr, (short) temp);
						addr += 2;
						i += 2;
					}
				}
			}
		} catch (Trap e) {
			csr |= RL_NXM;
			return true;
		} catch (IOException e) {
			csr |= RL_HNF;
			return true;
		}
		return false;
	}
}

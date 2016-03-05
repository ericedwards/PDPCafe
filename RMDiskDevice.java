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
// RMDiskDevice - RH11/RM02 Device simulator.
//


//
// TODO:	(** == done, .. == started)
//
//	**	clear ata via rmas
//	**	write to rmer1 clears bits
//	**	drive clear
//		controller clear (and sync) / proper unibus reset
//	..	other drive support (RM05?)
//		sc21 boot command support
//	..	all rmr errors
//		byte/word access proper response
//	**	state of cyl/head/sector at end of xfer
//		other commands
//		command issue logic
//		error checking on command issue
//		offset register and format bit
//		

package PDPCafe;

import java.io.*;

public class RMDiskDevice implements UnibusDevice {

	//
	// Unibus interface definitions.
	//

	private static final int RM_BASE = 0776700;	// default address
	private static final int RM_SIZE = 22;		// twenty two registers
	private static final int RM_VECTOR = 0254;	// default interrupt vector
	private static final int RM_BRLEVEL = 5;	// default bus request level
	private static final int RM_DELAY = 100;	// delay

	private static final int RM_EVENT_FLAG = 8;

	//
	// Control register definitions.
	//

	// RMCS1 bits in the controller

	private static final int RMCS1_IE = 0100;		// interrupt enable
	private static final int RMCS1_RDY = 0200;		// controller ready
	private static final int RMCS1_PSEL = 02000;	// port select
	private static final int RMCS1_MCPE = 020000;	// MASSBUS cntl parity error
	private static final int RMCS1_TRE = 040000;	// transfer error
	private static final int RMCS1_SC = 0100000;	// special condition (error)

	// RMCS1 bits in the drive

	private static final int RMCS1_GO = 01;
	private static final int RMCS1_DVA = 04000;

	// RMCS1 command bits in the drive (and in controller if 051-073)

	private static final int RMCS1_SEEK = 05;		// seek
	private static final int RMCS1_RECAL = 07;		// recalibrate
	private static final int RMCS1_CLEAR = 011;		// drive clear
	private static final int RMCS1_RELEASE = 013;	// dual port release
	private static final int RMCS1_OFFSET = 015;	// offset
	private static final int RMCS1_RETURN = 017;	// return to centerline
	private static final int RMCS1_PRESET = 021;	// read in preset
	private static final int RMCS1_PACK = 023;		// pack acknowledge
	private static final int RMCS1_SEARCH = 031;	// search
	private static final int RMCS1_WCHECK = 051;	// write check data
	private static final int RMCS1_WHCHECK = 053;	// write check header & data
	private static final int RMCS1_WRITE = 061;		// write
	private static final int RMCS1_WRITEH = 063;	// write header & data
	private static final int RMCS1_READ = 071;		// read
	private static final int RMCS1_READH = 073;		// read header & data

	// RMDS

	private static final int RMDS_OM = 01;			// offset mode
	private static final int RMDS_VV = 0100;		// volume valid
	private static final int RMDS_DRY = 0200;		// drive ready
	private static final int RMDS_DPR = 0400;		// drive present
	private static final int RMDS_PGM = 01000;		// programmable
	private static final int RMDS_LBT = 02000;		// last block transferred
	private static final int RMDS_WRL = 04000;		// write lock
	private static final int RMDS_MOL = 010000;		// medium online
	private static final int RMDS_PIP = 020000;		// positioning in progress
	private static final int RMDS_ERR = 040000;		// error
	private static final int RMDS_ATA = 0100000;	// attention active

	// RMCS2

	private static final int RMCS2_BAI = 010;
	private static final int RMCS2_PAT = 020;
	private static final int RMCS2_CLR = 040;
	private static final int RMCS2_IR = 0100;
	private static final int RMCS2_OR = 0200;
	private static final int RMCS2_MDPR = 0400;
	private static final int RMCS2_MXF = 01000;
	private static final int RMCS2_PGE = 02000;
	private static final int RMCS2_NEM = 04000;
	private static final int RMCS2_NED = 010000;
	private static final int RMCS2_UPE = 020000;
	private static final int RMCS2_WCE = 040000;
	private static final int RMCS2_DLT = 0100000;

	// RMER1

	private static final int RMER1_RMR = 04;
	private static final int RMER1_HCE = 0200;
	private static final int RMER1_AOE = 01000;
	private static final int RMER1_IAE = 02000;


	//
	// Internal definitions.
	//

	private static final int RM_TYPE_NORM = 0;	// no drive
	private static final int RM_TYPE_RM03 = 1;	// drive type is RM03
	private static final int RM_TYPE_RM05 = 2;	// drive type is RM05
	private static final int MAX_RM = 8;		// max drives per controller
	private static final int RM_CONTROLLER = 8;	// controller flag for finish
	private static final int RM_DEBUG_REG = 1;	// debug flag for registers
	private static final int RM_DEBUG_CMD = 1;	// debug flag for commands

	//
	// Generic drive geometry definitions.
	//

	private static final int RM_WORDS_SECTOR = 256;
	private static final int RM_BYTES_SECTOR = RM_WORDS_SECTOR * 2;

	//
	// RM02/RM03 drive geometry.
	//

	private static final int RM_CYLINDERS_RM03 = 823;
	private static final int RM_HEADS_RM03 = 5;
	private static final int RM_SECTORS_RM03 = 32;
	private static final int RM_SIZE_RM03 =
	  (RM_CYLINDERS_RM03 * RM_HEADS_RM03 * RM_SECTORS_RM03 * RM_BYTES_SECTOR);
	private static final int RM_DRIVETYPE_RM03 = 020024;

	//
	// RM05 drive geometry.
	//

	private static final int RM_CYLINDERS_RM05 = 823;
	private static final int RM_HEADS_RM05 = 19;
	private static final int RM_SECTORS_RM05 = 32;
	private static final int RM_SIZE_RM05 =
	  (RM_CYLINDERS_RM05 * RM_HEADS_RM05 * RM_SECTORS_RM05 * RM_BYTES_SECTOR);
	private static final int RM_DRIVETYPE_RM05 = 020027;

	//
	// Controller register images.
	//

	private int rmcs1;		// control and status #1
	private int rmwc;		// word count
	private int rmba;		// bus address
	private int rmcs2;		// control and status #2
	private int rmdb;		// data buffer

	//
	// Internal device data.
	//

	private UnibusDeviceInfo info;		// generic device information
	private int drive;					// drive number for current operation
	private RMDiskDrive[] drives;		// per drive information
	private byte[] buffer;				// sector data buffer
	private Unibus unibus;				// the Unibus device
	private int debug;					// debugging flags

	//
	// RMDiskDevice() - Constructor.
	//

	public RMDiskDevice() {
		this(RM_BASE, RM_SIZE, "");
	}

	//
	// RMDiskDevice() - Constructor.
	//

	public RMDiskDevice(int base, int size, String options) {
		info = new UnibusDeviceInfo(this, base, size, "RH11", false);
		drive = 0;
		drives = new RMDiskDrive[MAX_RM];
		buffer = new byte[RM_BYTES_SECTOR];
		debug = 0; // RM_DEBUG_REG|RM_DEBUG_CMD;
		rmcs1 = RMCS1_RDY;
		rmwc = 0;
		rmba = 0;
		rmcs2 = 0;
		rmdb = 0;
		for (int i = 0; i < drives.length; ++i) {
			drives[i] = new RMDiskDrive();
			if (i < 2) {
				drives[i].exists = RM_TYPE_RM03;	// hardcode for now @ 2
			} else {
				drives[i].exists = RM_TYPE_NORM;
			}
			drives[i].file = null;
			switch(drives[i].exists) {
			case RM_TYPE_RM03:
				drives[i].cylinders = RM_CYLINDERS_RM03;
				drives[i].heads = RM_HEADS_RM03;
				drives[i].sectors = RM_SECTORS_RM03;
				drives[i].rmcs1 = RMCS1_DVA;
				drives[i].rmds = RMDS_DPR|RMDS_DRY;
				drives[i].rmdt = RM_DRIVETYPE_RM03;
				drives[i].rmsn = i;					// stuff in drive #
				break;
			case RM_TYPE_RM05:
				drives[i].cylinders = RM_CYLINDERS_RM05;
				drives[i].heads = RM_HEADS_RM05;
				drives[i].sectors = RM_SECTORS_RM05;
				drives[i].rmcs1 = RMCS1_DVA;
				drives[i].rmds = RMDS_DPR|RMDS_DRY;
				drives[i].rmdt = RM_DRIVETYPE_RM05;
				drives[i].rmsn = i;					// stuff in drive #
				break;
			case RM_TYPE_NORM:
			default:
				drives[i].rmcs1 = 0;
				drives[i].rmds = 0;
				drives[i].rmdt = 0;
				drives[i].rmsn = 0;
				break;
			}
		}
		unibus = Unibus.instance();
		unibus.registerDevice(info);
	}

	//
	// assign()
	//

	public void assign(int unit, String path) throws java.io.IOException {
		int expectedSize;
		if ((unit >= 0) && (unit < drives.length)) {
			if (drives[unit].exists == RM_TYPE_NORM) {
				throw new java.io.IOException();
			}
			if (drives[unit].file != null) {
				drives[unit].file.close();
			}
			if ((drives[unit].rmds & RMDS_MOL) != 0) {
				drives[unit].rmds = RMDS_DRY|RMDS_DPR|RMDS_ATA;
				rmcs1 |= RMCS1_SC;
				// ZORK will interrupt
			} else {
				drives[unit].rmds = RMDS_DRY|RMDS_DPR;
			}
			drives[unit].rmcs1 = RMCS1_DVA;
			switch(drives[unit].exists) {
			case RM_TYPE_RM03:
				expectedSize = RM_SIZE_RM03;
				break;
			case RM_TYPE_RM05:
				expectedSize = RM_SIZE_RM05;
				break;
			default:
				throw new java.io.IOException();
			}
			drives[unit].file = new RandomAccessFile(path, "rw");
			if (drives[unit].file.length() == expectedSize) {
				drives[unit].rmds |= RMDS_MOL|RMDS_ATA;
				rmcs1 |= RMCS1_SC;
				// ZORK will interrupt
			} else {
				drives[unit].file.close();
				throw new java.io.IOException();
			}
		} else {
			throw new java.io.IOException();
		}
	}

	//
	// makedisk
	//

	public void makedisk(String path, String options)
	  throws java.io.IOException {
		FileOutputStream f = new FileOutputStream(path, false);
		byte[] b = new byte[RM_BYTES_SECTOR];
		for (int i = 0; i < (RM_SIZE_RM03/RM_BYTES_SECTOR); ++i) {
			f.write(b);
		}
		f.close();
	}

	//
	// read()
	//

	public short read(int addr) throws Trap {
		int data = 0;
		switch (addr - info.base) {
		case 0:				// rmcs1 (both)
			data = rmcs1 & 0163700;						// from rh11 rmcs1
			data |= (drives[drive].rmcs1 & 04077);		// from drive rmcs1
			break;
		case 02:			// rmwc (rh11)
			data = rmwc;
			break;
		case 04:			// rmba (rh11)
			data = rmba;
			break;
		case 06:			// rmda (drive) 
			data = drives[drive].rmda;
			break;
		case 010:			// rmcs2 (rh11)
			data = rmcs2;
			break;
		case 012:			// rmds (drive)
			data = drives[drive].rmds;
			break;
		case 014:			// rmer1 (drive)
			data = drives[drive].rmer1;
			break;
		case 016:			// rmas (bit per drive)
			for (int i = 0; i < MAX_RM; ++i) {
				if ((drives[i].rmds & RMDS_ATA) != 0) {
					data |= (1 << i);
				}
			}
			break;
		case 020:			// rmla (drive)
			data = drives[drive].rmla;
			break;
		case 022:			// rbdb (rh11)
			data = rmdb;
			break;
		case 024:			// rmmr1 (drive)
			data = drives[drive].rmmr1;
			break;
		case 026:			// rmdt (drive)
			data = drives[drive].rmdt;
			break;
		case 030:			// rmsn (drive)
			data = drives[drive].rmsn;
			break;
		case 032:			// rmof (drive)
			data = drives[drive].rmof;
			break;
		case 034:			// rmdc (drive)
			data = drives[drive].rmdc;
			break;
		case 036:			// rmhr (drive)
			data = drives[drive].rmhr;
			break;
		case 040:			// rmmr2 (drive)
			data = drives[drive].rmmr2;
			break;
		case 042:			// rmer2 (drive)
			data = drives[drive].rmer2;
			break;
		case 044:			// rmec1 (drive)
			data = drives[drive].rmec1;
			break;
		case 046:			// rmec2 (drive)
			data = drives[drive].rmec2;
			break;
		case 050:			// rmbae (rh70 only) - fall into trap
		case 052:			// rmcs3 (rm70 only) - fall into trap
		default:
			if ((debug & RM_DEBUG_REG) != 0) {
				System.out.println(this.getClass().getName() + ".read(): " +
					Integer.toOctalString(addr) + "=UnibusTimeout");
			}
			throw new Trap(Trap.UnibusTimeout);
		}
		if ((debug & RM_DEBUG_REG) != 0) {
			System.out.println(this.getClass().getName() + ".read(): " +
				Integer.toOctalString(addr) + "=" +
				Integer.toOctalString(data));
		}
		return (short) data;
	}

	//
	// write()
	//

	public void write(int addr, short shortData) throws Trap {
		int data = ((int) shortData) & 0177777;
		boolean anyAttention = false;
		if ((debug & RM_DEBUG_REG) != 0) {
			System.out.println(this.getClass().getName() + ".write(): " +
				Integer.toOctalString(addr) + "=" +
				Integer.toOctalString(data));
		}
		switch(addr - info.base) {
		case 0:				// rmcs1 (both)
			rmcs1 &= ~01500;
			rmcs1 |= (data & 01500);
			if ((data & RMCS1_TRE) != 0) {
				rmcs1 &= ~RMCS1_TRE;
				// ZORK also clear upper rmcs2?
				for (int i = 0; i < MAX_RM; ++i) {
					if ((drives[i].rmds & RMDS_ATA) != 0) {
						anyAttention = true;
					}
				}
				if (!anyAttention) {
					rmcs1 &= ~RMCS1_SC;
				}
			}
			if ((data & (RMCS1_IE|RMCS1_RDY)) == (RMCS1_IE|RMCS1_RDY)) {
				unibus.scheduleInterrupt(this, RM_BRLEVEL, RM_VECTOR);
			}
			if (drives[drive].exists == RM_TYPE_NORM) {
				// ZORK
				return;
			}
			if ((drives[drive].rmcs1 & RMCS1_GO) == 0) {
				drives[drive].rmcs1 &= ~077;
				drives[drive].rmcs1 |= (data & 077);
				if ((drives[drive].rmcs1 & RMCS1_GO) != 0) {
					exec();
				}
			} else {
				drives[drive].rmer1 |= RMER1_RMR;		// modification refused
				// ZORK
			}
			break;
		case 02:			// rmwc (rh11)
			rmwc = data;
			break;
		case 04:			// rmba (rh11)
			rmba = (data & 0177776);
			break;
		case 06:			// rmda (drive) 
			if ((drives[drive].rmcs1 & RMCS1_GO) == 0) {
				drives[drive].rmda = data;
			} else {
				drives[drive].rmer1 |= RMER1_RMR;		// modification refused
				// ZORK RMR
			}
			break;
		case 010:			// rmcs2 (rh11)
			rmcs2 &= ~07;
			rmcs2 |= (data & 07);
			drive = data & 07;
			if ((debug & RM_DEBUG_CMD) != 0) {
				System.out.println(this.getClass().getName() +
					".write(): select drive #" + drive);
			}
			// check for controller clear later ZORK
			if (drives[drive].exists == RM_TYPE_NORM) {
				rmcs2 |= RMCS2_NED;
			} else {
				rmcs2 &= ~RMCS2_NED;
			}
			break;
		case 012:			// rmds (drive) - no writes, but no error
			break;
		case 014:			// rmer1 (drive)
			if ((drives[drive].rmcs1 & RMCS1_GO) == 0) {
				drives[drive].rmer1 &= data;			// clear bits
			} else {
				drives[drive].rmer1 |= RMER1_RMR;		// modification refused
				// ZORK RMR
			}
			break;
		case 016:			// rmas (bit per drive)
				for (int i = 0; i < MAX_RM; ++i) {
					if (((data & (1 << i)) != 0) && (drives[i].rmer1 == 0) &&
					  (drives[i].rmer2 == 0)) {
						drives[i].rmds &= ~RMDS_ATA;
					}
					if ((drives[i].rmds & RMDS_ATA) != 0) {
						anyAttention = true;
					}
				}
				if (!anyAttention && ((rmcs1 & RMCS1_TRE) == 0)) {
					rmcs1 &= ~RMCS1_SC;
					// ZORK clear pending interrupts ?
				}
			break;
		case 020:			// rmla (drive)
			break;
		case 022:			// rbdb (rh11)
			break;
		case 024:			// rmmr1 (drive)
			break;
		case 026:			// rmdt (drive)
			break;
		case 030:			// rmsn (drive)
			break;
		case 032:			// rmof (drive)
			break;
		case 034:			// rmdc (drive)
			if ((drives[drive].rmcs1 & RMCS1_GO) == 0) {
				drives[drive].rmdc = data;
			} else {
				drives[drive].rmer1 |= RMER1_RMR;		// modification refused
				// ZORK RMR
			}
			break;
		case 036:			// rmhr (drive)
			break;
		case 040:			// rmmr2 (drive)
			break;
		case 042:			// rmer2 (drive)
			break;
		case 044:			// rmec1 (drive)
			break;
		case 046:			// rmec2 (drive)
			break;
		case 050:			// rmbae (rh70 only) - fall into trap
		case 052:			// rmcs3 (rm70 only) - fall into trap
		default:
			if ((debug & RM_DEBUG_REG) != 0) {
				System.out.println(this.getClass().getName() + ".write(): " +
					Integer.toOctalString(addr) + "=UnibusTimeout");
			}
			throw new Trap(Trap.UnibusTimeout);
		}
	}

	//
	// writebyte() - Handle byte writes for various registers.  Not all are
	// implemented.
	//

	public void writebyte(int addr, byte byteData) throws Trap {
		int upperData = (((int) byteData)  << 8) & 0177400;
		int lowerData = ((int) byteData) & 0377;
		if ((debug & RM_DEBUG_REG) != 0) {
			System.out.println(this.getClass().getName() + ".writebyte(): " +
				Integer.toOctalString(addr) + "=" +
			Integer.toOctalString(lowerData));
		}
		switch(addr - info.base) {
		case 0:				// rmcs1 (both) - low byte
			rmcs1 &= ~0100;
			rmcs1 |= (lowerData & 0100);
			if ((lowerData & (RMCS1_IE|RMCS1_RDY)) == (RMCS1_IE|RMCS1_RDY)) {
				unibus.scheduleInterrupt(this, RM_BRLEVEL, RM_VECTOR);
			}
			if (drives[drive].exists == RM_TYPE_NORM) {
				// ZORK
				return;
			}
			if ((drives[drive].rmcs1 & RMCS1_GO) == 0) {
				drives[drive].rmcs1 &= ~077;
				drives[drive].rmcs1 |= (lowerData & 077);
				if ((drives[drive].rmcs1 & RMCS1_GO) != 0) {
					exec();
				}
			} else {
				drives[drive].rmer1 |= RMER1_RMR;	// modification refused
				// ZORK
			}
			break;
		case 01:			// rmcs1 (both) - high byte
			rmcs1 &= ~01500;
			rmcs1 |= (upperData & 01500);
			if ((upperData & RMCS1_TRE) != 0) {
				rmcs1 &= ~RMCS1_TRE;
				// ZORK also clear upper rmcs2?
				boolean anyAttention = false;
				for (int i = 0; i < MAX_RM; ++i) {
					if ((drives[i].rmds & RMDS_ATA) != 0) {
						anyAttention = true;
					}
				}
				if (!anyAttention) {
					rmcs1 &= ~RMCS1_SC;
				}
			}
			break;
		case 010:			// rmcs2 (rh11) - low byte
			rmcs2 &= ~07;
			rmcs2 |= (lowerData & 07);
			drive = lowerData & 07;
			if ((debug & RM_DEBUG_CMD) != 0) {
				System.out.println(this.getClass().getName() +
					".writebyte(): select drive #" + drive);
			}
			// check for controller clear later ZORK
			if (drives[drive].exists == RM_TYPE_NORM) {
				rmcs2 |= RMCS2_NED;
			} else {
				rmcs2 &= ~RMCS2_NED;
			}
			break;
		default:
			if ((debug & RM_DEBUG_REG) != 0) {
				System.out.println(this.getClass().getName() +
					".writebyte(): " + Integer.toOctalString(addr) +
					"=UnibusTimeout");
			}
			throw new Trap(Trap.UnibusTimeout);
		}
	}

	//
	// reset()
	//

	public void reset() {
		drive = 0;
		for (int i = 0; i < drives.length; ++i) {
		}
		// a lot more later
	}

	//
	// eventService()
	//

	public void eventService(int data) {
		int finished = data & 07;
		drives[finished].rmds |= RMDS_DRY;
		drives[finished].rmcs1 &= ~RMCS1_GO;
		if ((data & 8) != 0) {
			rmcs1 |= RMCS1_RDY;
		} else {
			drives[finished].rmds |= RMDS_ATA;
		}
		if ((rmcs1 & RMCS1_IE) != 0) {
			unibus.scheduleInterrupt(this, RM_BRLEVEL, RM_VECTOR);
		}
	}

	//
	// interruptService()
	//

	public void interruptService() {
		rmcs1 &= ~RMCS1_IE;
	}

	//
	// exec()
	//

	private void exec() {

		boolean driveBusy = false;
		boolean controllerBusy = false;
		int cmd = (drives[drive].rmcs1 & 077);

		// previous error & !clear ? = reset go, set dry, set ata, assert attn
		// mol ? no = OPI abort
		// vv ? no & not PACK or PRESET = IVC abort

		if ((drives[drive].rmer1 == 0) && (drives[drive].rmer2 == 0)) {
			drives[drive].rmds &= ~RMDS_ATA;	// if not in error clear attn
		}
		drives[drive].rmds &= ~RMDS_DRY;		// drive now busy
		if ((cmd >= 051) && (cmd <= 073)) {
			rmcs1 &= ~RMCS1_RDY;				// controller also busy
			rmcs2 &= 0377;						// clear controller errors
		}

		switch (cmd) {
		case RMCS1_CLEAR:
			if ((debug & RM_DEBUG_CMD) != 0) {
				System.out.println(this.getClass().getName() +
					".exec(): drive clear");
			}
			drives[drive].rmer1 = 0;
			drives[drive].rmer2 = 0;
			drives[drive].rmof = 0;
			drives[drive].rmds &= ~(RMDS_ATA|RMDS_ERR);
			if ((rmcs1 & RMCS1_TRE) == 0) {
				rmcs1 &= ~RMCS1_SC;
			}
			break;
		case RMCS1_PACK:
			if ((debug & RM_DEBUG_CMD) != 0) {
				System.out.println(this.getClass().getName() +
					".exec(): pack acknowledge");
			}
			drives[drive].rmds |= RMDS_VV;
			break;
		case RMCS1_PRESET:
			if ((debug & RM_DEBUG_CMD) != 0) {
				System.out.println(this.getClass().getName() +
					".exec(): read in preset");
			}
			drives[drive].rmds |= RMDS_VV;
			drives[drive].rmda = 0;
			drives[drive].rmdc = 0;
			drives[drive].rmof = 0;
			break;
		case RMCS1_SEARCH:
			if ((debug & RM_DEBUG_CMD) != 0) {
				System.out.println(this.getClass().getName() +
					".exec(): search <" + drives[drive].rmdc + ">");
			}
			driveBusy = true;
			if (((drives[drive].rmda & 0177400) >> 8) >= drives[drive].heads) {
				drives[drive].rmer1 |= RMER1_IAE;
				break;
			}
			if ((drives[drive].rmda & 0377) >= drives[drive].sectors) {
				drives[drive].rmer1 |= RMER1_IAE;
				break;
			}
			if (drives[drive].rmdc >= drives[drive].cylinders) {
				drives[drive].rmer1 |= RMER1_IAE;
				break;
			}
			break;
		case RMCS1_SEEK:
			if ((debug & RM_DEBUG_CMD) != 0) {
				System.out.println(this.getClass().getName() +
					".exec(): seek <" + drives[drive].rmdc + ">");
			}
			driveBusy = true;
			if (drives[drive].rmdc >= drives[drive].cylinders) {
				drives[drive].rmer1 |= RMER1_IAE;
				break;
			}
			break;
		case RMCS1_READ:
			driveBusy = true;
			controllerBusy = true;
			doReadWrite(false);
			break;
		case RMCS1_WRITE:
			driveBusy = true;
			controllerBusy = true;
			doReadWrite(true);
			break;
		default:
			if ((debug & RM_DEBUG_CMD) != 0) {
				System.out.println(this.getClass().getName() +
					".exec(): illegal command");
			}
			// ZORK illegal command
			break;
		}

		if ((drives[drive].rmer1 != 0) || (drives[drive].rmer2 != 0)) {
			drives[drive].rmds |= RMDS_ATA|RMDS_ERR;
			rmcs1 |= RMCS1_SC;
			if (controllerBusy) {
				rmcs1 |= RMCS1_TRE;
			}
		}
		if (controllerBusy) {
			if ((rmcs2 & 0177400) != 0) {
				rmcs1 |= RMCS1_TRE;
			}
		}
		if (driveBusy) {
			if (controllerBusy) {
				unibus.scheduleEvent(this, RM_DELAY, drive + RM_EVENT_FLAG);
			} else {
				unibus.scheduleEvent(this, RM_DELAY, drive);
			}
		} else {
			drives[drive].rmds |= RMDS_DRY;
			drives[drive].rmcs1 &= ~RMCS1_GO;
		}
	}

	//
	// doReadWrite() - Do a read or write by simulating DMA and doing
	// file I/O to the disk file.  Returns true if any error indications
	// were set.
	//

	private boolean doReadWrite(boolean write) {

		int addr = rmba + ((rmcs1 & 01400) << 8);	// build DMA address
		int count = (0177777 - rmwc) + 1;			// extract word count
		int cylinder, head, sector;					// location on disk
		int offset;									// offset into file
		boolean ioError = false;					// actual i/o error occurred
		int temp;
		int aoeCount = 0;	// additional residual if xfer past end of drive

		cylinder = drives[drive].rmdc;
		if (cylinder >= drives[drive].cylinders) {
			drives[drive].rmer1 |= RMER1_IAE;
			return true;
		}
		head = (drives[drive].rmda & 0177400) >> 8;	// head in uper byte
		if (head >= drives[drive].heads) {
			drives[drive].rmer1 |= RMER1_IAE;
			return true;
		}
		sector = drives[drive].rmda & 0377;			// sector in lower byte
		if (sector >= drives[drive].sectors) {
			drives[drive].rmer1 |= RMER1_IAE;
			return true;
		}

		if ((debug & RM_DEBUG_CMD) != 0) {
			System.out.print(this.getClass().getName() + ".doReadWrite(): ");
			if (write) { 
				System.out.print("write");
			} else {
				System.out.print("read");
			}
 			System.out.println(" <" + cylinder + ":" + head + ":" +
				sector + "> " + (count * 2));
		}

		// find the offset into the file

		offset = (cylinder * drives[drive].heads * drives[drive].sectors
		  * RM_BYTES_SECTOR) + (head * drives[drive].sectors * RM_BYTES_SECTOR)
		  + (sector * RM_BYTES_SECTOR);

		try {
			drives[drive].file.seek(offset);		// seek into file
		} catch (IOException e) {
			drives[drive].rmer1 |= RMER1_HCE;
			return true;
		}

		// Find and check the end of the transfer. If past end of drive:
		// This is the AOE condition, trim back count and preset
		// error indications.  Proceed to read/write to end of drive.

		offset += (count * 2);
		if (offset > (drives[drive].cylinders * drives[drive].heads *
		  drives[drive].sectors * RM_BYTES_SECTOR)) {
			drives[drive].rmer1 |= RMER1_AOE;
			temp = offset;
			offset = (drives[drive].cylinders * drives[drive].heads *
			  drives[drive].sectors * RM_BYTES_SECTOR);
			count -= ((temp - offset) / 2);
			aoeCount = (temp - offset) / 2;
			ioError = true;							// but continue anyway
		}

		// Loop sector by sector, doing the actual transfer.

		try {
			while (count != 0) {
				if (write) {
					for (int i = 0; (i < RM_BYTES_SECTOR) && (count != 0);
					  count--) {
						temp = unibus.read(addr);
						buffer[i] = (byte)(temp & 0xff);
						buffer[i + 1] = (byte)(temp >> 8);
						addr += 2;
						i += 2;
					}
					drives[drive].file.write(buffer);
				} else {
					drives[drive].file.readFully(buffer);
					for (int i = 0; (i < RM_BYTES_SECTOR) && (count != 0);
					  count--) {
						temp = buffer[i + 1] << 8;
						temp += ((int) buffer[i]) & 0xff;
						unibus.write(addr, (short) temp);
						addr += 2;
						i += 2;
					}
				}
			}
		} catch (Trap e) {
			rmcs2 |= RMCS2_NEM;
			ioError = true;
		} catch (IOException e) {
			drives[drive].rmer1 |= RMER1_HCE;
			ioError = true;
		}

		// Trim back any residual from the end of the transfer and
		// update the drive address registers and word count.

		offset -= ((count * 2) - 1);	// offset of last work transferred
		count += aoeCount;				// add back any past end of drive
		cylinder = offset /
		  (drives[drive].heads * drives[drive].sectors * RM_BYTES_SECTOR);
		head = (offset / (drives[drive].sectors * RM_BYTES_SECTOR)) %
		  drives[drive].heads;
		sector = (offset / RM_BYTES_SECTOR) % drives[drive].sectors;
		drives[drive].rmdc = cylinder;
		drives[drive].rmda = (head << 8) | sector;
		rmwc = (0 - count) & 0177777;

		return ioError;
	}

}

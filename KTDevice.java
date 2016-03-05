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
// KTDevice - KT11 (Memory Management) Device Simulation.
//

package PDPCafe;

class KTDevice implements UnibusDevice {

	private static final int KT_MMR = 0777572;
	private static final int KT_MMR_SIZE = 3;
	private static final int KT_MMR0 = 0777572;
	private static final int KT_MMR1 = 0777574;
	private static final int KT_MMR2 = 0777576;
	private static final int KT_KISD = 0772300;
	private static final int KT_KISD_SIZE = 8;
	private static final int KT_KISA = 0772340;
	private static final int KT_KISA_SIZE = 8;
	private static final int KT_UISD = 0777600;
	private static final int KT_UISD_SIZE = 8;
	private static final int KT_UISA = 0777640;
	private static final int KT_UISA_SIZE = 8;

	private static KTDevice theInstance = null;

	public int mmr0;
	public int mmr2;
	private int[] kisd;
	private int[] kisa;
	private int[] uisd;
	private int[] uisa;

	private Unibus unibus;
	private CPUDevice cpu;

	private KTDevice() {
		mmr0 = 0;
		mmr2 = 0;
		kisd = new int[KT_KISD_SIZE];
		kisa = new int[KT_KISA_SIZE];
		uisd = new int[KT_UISD_SIZE];
		uisa = new int[KT_UISA_SIZE];
		UnibusDeviceInfo info;
		unibus = Unibus.instance();
		info = new UnibusDeviceInfo(this, KT_MMR, KT_MMR_SIZE, "MMR", true);
		unibus.registerDevice(info);
		info = new UnibusDeviceInfo(this, KT_KISD, KT_KISD_SIZE, "KISD", true);
		unibus.registerDevice(info);
		info = new UnibusDeviceInfo(this, KT_KISA, KT_KISA_SIZE, "KISA", true);
		unibus.registerDevice(info);
		info = new UnibusDeviceInfo(this, KT_UISD, KT_UISD_SIZE, "UISD", true);
		unibus.registerDevice(info);
		info = new UnibusDeviceInfo(this, KT_UISA, KT_UISA_SIZE, "UISA", true);
		unibus.registerDevice(info);
		cpu = CPUDevice.instance();
	}

	public static final synchronized KTDevice instance() {
		if (theInstance == null) {
			theInstance = new KTDevice();
		}
		return theInstance;
	}

	public void mmr2update(short addr) {
		if ((mmr0 & 0160000) == 0) {
			mmr2 = addr & 0177777;
		}
	}

	public void reset() {
		mmr0 = 0;
	}

	public short read(int addr) throws Trap {
		int data = 0;
		int i = (addr & 016) >> 1;
		switch (addr & 0777760) {
		case KT_KISD:
			data = kisd[i];
			break;
		case KT_KISA:
			data = kisa[i];
			break;
		case KT_UISD:
			data = uisd[i];
			break;
		case KT_UISA:
			data = uisa[i];
			break;
		default:
			switch (addr & 0777777) {
			case KT_MMR0:
				data = mmr0;
				break;
			case KT_MMR1:
				data = 0;
				break;
			case KT_MMR2:
				data = mmr2;
				break;
			default:
				throw new Trap(Trap.UnibusTimeout);
			}
			break;
		}
		return(short) data;
	}

	public void write(int addr, short shortData) throws Trap {
		int data = ((int) shortData) & 0177777;
		int i = (addr & 016) >> 1;
		switch (addr & 0777760) {
		case KT_KISD:
			kisd[i] &= ~(077516);		// mask r/o, and clear w-bit
			kisd[i] |= (data & 077416); // writeable bits only
			break;
		case KT_KISA:
			kisa[i] = data & 07777;		// PARs are 12 bit
			kisd[i] &= ~(0100);			// clear w-bit
			break;
		case KT_UISD:
			uisd[i] &= ~(077516);		// mask r/o, and clear w-bit
			uisd[i] |= (data & 077416); // writeable bits only
			break;
		case KT_UISA:
			uisa[i] = data & 07777;		// PARs are 12 bit
			uisd[i] &= ~(0100);			// clear w-bit
			break;
		default:
			switch (addr & 0777777) {
			case KT_MMR0:
				mmr0 &= ~(0160157);			// mask r/o bits
				mmr0 |= (data & 0160157);	// writeable bits only
				break;
			case KT_MMR1:
			case KT_MMR2:
				// don't accept writes, but no error
				break;
			default:
				throw new Trap(Trap.UnibusTimeout);
			}
			break;
		}
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
		// do nothing
	}

	public void interruptService() {
		// do nothing
	}

	public final int map(short shortAddr, boolean isWrite,
	boolean forceKernel, boolean forcePrevious) throws Trap {
		int addr = shortAddr & 0177777;
		int caddr;
		int mode;
		int pdr[];

		// First, check to see if the mmu is off, if so do
		// the simple mapping.  Adjust I/O page accesses to map
		// to the proper 18-bit UNIBUS address.

		if ((mmr0 & 1) == 0) {
			if (addr >= 0160000) {
				addr += 0600000;
			}
			return addr;
		} else {

			// MMU is on. Proceed with mapping.

			int index = (addr >> 13) & 07;
			int block = (addr >> 6) & 0177;

			// handle forced access to kernel or previous

			if (forceKernel) {
				mode = 0;
			} else if (!forcePrevious) {
				mode = (cpu.psw & 0140000) >> 14;
			} else {
				mode = (cpu.psw & 030000) >> 12;
			}

			// check the mode and select the proper
			// memory management info

			switch (mode) {
			case 0:
				caddr = kisa[index] << 6;
				pdr = kisd;
				mode = 0;
				break;
			case 3:
				caddr = uisa[index] << 6;
				pdr = uisd;
				mode = 0140;
				break;
			default:
				mode <<= 5;
				if ((mmr0 & 0160000) == 0) {
					mmr0 &= ~(0156);
					mmr0 |= 0100000;
					mmr0 |= mode;
					mmr0 |= index << 1;
				}
				throw new Trap(Trap.SegmentationError);
			}

			// check the length, this is the hardest case

			if ((pdr[index] & 010) != 0) {				// downward expanding
				if (block < ((pdr[index] >> 8) & 0177)) {
					if ((mmr0 & 0160000) == 0) {
						mmr0 &= ~(0156);
						mmr0 |= 040000;
						mmr0 |= mode;
						mmr0 |= index << 1;
						if ((pdr[index] & 2) == 0) {
							mmr0 |= 0100000;
						}
						if (isWrite && ((pdr[index] & 4) == 0)) {
							mmr0 |= 020000;
						}
					}
					throw new Trap(Trap.SegmentationError);
				}
			} else {									// upward expanding
				if (block > ((pdr[index] >> 8) & 0177)) {
					if ((mmr0 & 0160000) == 0) {
						mmr0 &= ~(0156);
						mmr0 |= 040000;
						mmr0 |= mode;
						mmr0 |= index << 1;
						if ((pdr[index] & 2) == 0) {
							mmr0 |= 0100000;
						}
						if (isWrite && ((pdr[index] & 4) == 0)) {
							mmr0 |= 020000;
						}
					}
					throw new Trap(Trap.SegmentationError);
				}
			}

			// now check if the segment is resident

			if ((pdr[index] & 2) == 0) {
				if ((mmr0 & 0160000) == 0) {
					mmr0 &= ~(0156);
					mmr0 |= 0100000;
					mmr0 |= mode;
					mmr0 |= index << 1;
					if (isWrite && ((pdr[index] & 4) == 0)) {
						mmr0 |= 020000;
					}
				}
				throw new Trap(Trap.SegmentationError);
			}

			// if a write, make sure it's ok

			if (isWrite) {
				if ((pdr[index] & 4) == 0) {
					if ((mmr0 & 0160000) == 0) {
						mmr0 &= ~(0156);
						mmr0 |= 020000;
						mmr0 |= mode;
						mmr0 |= index << 1;
					}
					throw new Trap(Trap.SegmentationError);
				}
				pdr[index] |= 0100;
			}

			return caddr + (addr & 017777);
		}
	}

	public final short logicalRead(short addr) throws Trap {
		if ((addr & 1) != 0) throw new Trap(Trap.OddAddress);
		return unibus.read(map(addr, false, false, false));
	}

	public final void logicalWrite(short addr, short data) throws Trap {
		if ((addr & 1) != 0) throw new Trap(Trap.OddAddress);
		unibus.write(map(addr, true, false, false), data);
	}

	public final byte logicalReadByte(short addr) throws Trap {
		short temp = (short)(addr & 0177776);
		short data;
		data = unibus.read(map(temp, false, false, false));
		if ((addr & 1) == 0) {
			return((byte) (data & 0377));
		} else {
			return((byte) ((data >> 8) & 0377));
		}
	}

	public final void logicalWriteByte(short addr, byte data) throws Trap {
		unibus.writebyte(map(addr, true, false, false), data);
	}

	public final short logicalReadPrevious(short addr) throws Trap {
		if ((addr & 1) != 0) throw new Trap(Trap.OddAddress);
		return unibus.read(map(addr, false, false, true));
	}

	public final void logicalWritePrevious(short addr, short data) throws Trap {
		if ((addr & 1) != 0) throw new Trap(Trap.OddAddress);
		unibus.write(map(addr, true, false, true), data);
	}

	public final short logicalReadKernel(short addr) throws Trap {
		if ((addr & 1) != 0) throw new Trap(Trap.OddAddress);
		return unibus.read(map(addr, false, true, false));
	}
}

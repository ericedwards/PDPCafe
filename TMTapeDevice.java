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
// TMTapeDevice - TM11 Tape Simulation.
//

package PDPCafe;

import java.io.*;

public class TMTapeDevice implements UnibusDevice {

	// Unibus interface definitions.

	private static final int TM_BASE = 0772520;	// default address
	private static final int TM_SIZE = 6;	 	// four registers
	private static final int TM_VECTOR = 0224;	// default interrupt vector
	private static final int TM_BRLEVEL = 5;	// default bus request level
	private static final int TM_DELAY = 100;	// in instructions

	// Control register definitions.

	private static final int TM_GO = 01;
	private static final int TM_RCOM = 02;
	private static final int TM_WCOM = 04;
	private static final int TM_WEOF = 06;
	private static final int TM_SFORW = 010;
	private static final int TM_SREV = 012;
	private static final int TM_WIRG = 014;
	private static final int TM_REW = 016;

	private static final int TM_CRDY = 0200;
	private static final int TM_IE = 0100;
	private static final int TM_CE = 0100000;

	// Error register definitions.

	private static final int TM_TUR = 01;		// tape unit ready
	private static final int TM_RWS = 02;		// rewind status
	private static final int TM_WRL = 04;		// write lock
	private static final int TM_SDWN = 010;		// tape settle down
	private static final int TM_7CH = 020;		// seven channel
	private static final int TM_BOT = 040;		// beginning of tape
	private static final int TM_SELR = 0100;	// select remote
	private static final int TM_NXM = 0200;		// non-existant memory
	private static final int TM_BAD = 0400;		// bad tape error
	private static final int TM_RLE = 01000;	// record length error
	private static final int TM_EOT = 02000;	// end of tape
	private static final int TM_LATE = 04000;	// bus grant late
	private static final int TM_PERR = 010000;	// parity error
	private static final int TM_CRC = 020000;	// crc error
	private static final int TM_EOF = 040000;	// end of file
	private static final int TM_ILC = 0100000;	// illegal command

	private static final int TM_ANYERR =
	(TM_NXM|TM_BAD|TM_RLE|TM_EOT|TM_LATE|TM_PERR|TM_CRC|TM_EOF|TM_ILC);

	// Controller register images.

	private int tmer;
	private int tmcs;
	private int tmbc;
	private int tmba;
	private int tmdb;
	private int tmrd;

	// Internal controller information

	private UnibusDeviceInfo info;			// generic device information
	private RandomAccessFile file;
	private Unibus unibus;

	public TMTapeDevice() {
		this(TM_BASE, TM_SIZE, "");
	}

	public TMTapeDevice(int base, int size, String options) {
		info = new UnibusDeviceInfo(this, base, size, "TM11", false);
		tmcs = TM_CRDY;
		tmer = 0;
		file = null;
		unibus = Unibus.instance();
		unibus.registerDevice(info);
	}

	public void assign(String path) throws java.io.IOException {
		tmer &= ~(TM_TUR|TM_SELR);
		if (file != null) {
			file.close();
		}
		file = null;
		file = new RandomAccessFile(path, "rw");
		file.seek(0);
		tmer = TM_TUR|TM_SELR|TM_BOT;
	}

	private byte[] encodeRecordSize(int recordSize) {
		byte[] encodedSize = new byte[4];
		encodedSize[3] = (byte) ((recordSize >> 24) & 0xff);
		encodedSize[2] = (byte) ((recordSize >> 16) & 0xff);
		encodedSize[1] = (byte) ((recordSize >> 8) & 0xff);
		encodedSize[0] = (byte) (recordSize & 0xff);
		return encodedSize;
	}

	private int decodeRecordSize(byte[] encodedSize) {
		int recordSize;
		recordSize = (((int) encodedSize[3]) & 0xff) << 24;
		recordSize += (((int) encodedSize[2]) & 0xff) << 16;
		recordSize += (((int) encodedSize[1]) & 0xff) << 8;
		recordSize += ((int) encodedSize[0]) & 0xff;
		return recordSize;
	}

	// read() - Handle the reading of an TM11 register.

	public short read(int addr) throws Trap {
		int data = 0;
		switch (addr - info.base) {
		case 0:
			data = tmer;
			break;
		case 2:
			data = tmcs;
			break;
		case 4:
			data = tmbc;
			break;
		case 6:
			data = tmba;
			break;
		case 010:
			data = tmdb;
			break;
		case 012:
			data = tmrd;
			break;
		default:
			throw new Trap(Trap.UnibusTimeout);
		}
		return(short) data;
	}

	// write() - Handle a write to one of the TM11
	// registers.  A command is started when the control
	// and status register is written with the TM_GO bit
	// set.

	public void write(int addr, short shortData) throws Trap {
		int data = ((int) shortData) & 0177777;
		switch (addr - info.base) {
		case 0:
			// don't allow writes error register, just ignore
			break;
		case 2:
			tmcs &= (TM_CRDY|TM_CE);
			tmcs |= data & ~(TM_CRDY|TM_CE);
			// Used to check for just TM_GO, but the 2.9 BSD
			// autoconfig routine just sets TM_IE to make
			// the TM interrupt.  Woo woo.
			if ((tmcs & (TM_GO|TM_IE)) != 0) {
				tmcs &= ~TM_CRDY;
				exec();
			}
			break;
		case 4:
			tmbc = data;
			break;
		case 6:
			tmba = data;
			break;
		case 010:
			tmdb = data;
			break;
		case 012:
			tmrd = data;
			break;
		default:
			throw new Trap(Trap.UnibusTimeout);
		}
	}

	// writebyte() - Ok, who's the retard?  Byte writes should
	// probably be allowed to the TM11, but I'm too lazy.

	public void writebyte(int addr, byte data) throws Trap {
		throw new Trap(Trap.Unimplemented);
	}

	// reset()

	public void reset() {
		// Unibus reset, later handle this properly.
	}

	// exec() - parse the command and call the proper handler.

	private void exec() {
		int delay = TM_DELAY;
		tmcs &= ~TM_CE;				 // clear error conditions
		tmer &= ~(TM_ANYERR);
		tmer &= ~TM_BOT;
		switch (tmcs & 016) {
		case TM_REW:
			doRewind();
			break;
		case TM_RCOM:							// read command
			doRead();
			break;
		case TM_SFORW:							// space forward
			doSpaceForward();
			break;
		case TM_WCOM:							// write command
		case TM_WIRG:							// maybe do this one too...
			doWrite();
			break;
		case TM_WEOF:							// write eof command
			doWriteEof();
			break;
		case TM_SREV:							// space reverse
			doSpaceReverse();
			break;
		default:
			if ((tmcs & TM_GO) != 0) {
				tmer |= TM_ILC;					// bad command, if go set
			}
			delay = 0;
			break;
		}
		unibus.scheduleEvent(this, delay, 0);
	}

	// eventService() - Finish the current command.  Set the error bits and
	// mark the controller ready.

	public void eventService(int data) {
		if ((tmer & TM_ANYERR) != 0) {
			tmcs |= TM_CE;
		}
		tmcs |= TM_CRDY;
		if ((tmcs & TM_IE) != 0) {
			tmcs &= ~TM_IE;
			unibus.scheduleInterrupt(this, TM_BRLEVEL, TM_VECTOR);
		}
	}

	public void interruptService() {
		// nothing for now - move clear of IE & RDY here later ?
	}

	private void doRewind() {
		if (file != null) {
			try {
				file.seek(0);
				tmer |= TM_BOT;
			} catch (IOException e) {
				tmer |= TM_ILC;
			}
		} else {
			tmer |= TM_ILC;
		}
	}

	private void doRead() {
		if (file != null) {
			try {
				byte[] recInfo = new byte[4];
				int i;
				int temp;
				int addr = tmba + ((tmcs & 060) << 12);
				int count = (0177777 - tmbc) + 1;
				file.readFully(recInfo);
				byte[] buffer = new byte[decodeRecordSize(recInfo)];
				file.readFully(buffer);
				file.readFully(recInfo);
				if (decodeRecordSize(recInfo) == 0) {
					tmer |= TM_EOF;
					return;
				}
				for (i = 0; (i < buffer.length) && (count > 0); count -= 2) {
					temp = ((int) buffer[i + 1]) << 8;
					temp += ((int) buffer[i]) & 0xff;
					unibus.write(addr, (short) temp);
					addr += 2;
					i += 2;
				}
				tmbc = ((0177777 - count) + 1) & 0177777;
				if ((count != 0) || (i != buffer.length)) {
					tmer |= TM_RLE;
				}
			} catch (Trap e) {
				tmer |= TM_NXM;
				return;
			} catch (IOException e) {
				tmer |= TM_CRC;
				return;
			}
		} else {
			tmer |= TM_ILC;
		}
	}

	private void doWrite() {
		if (file != null) {
			try {
				int i;
				int temp;
				int addr = tmba + ((tmcs & 060) << 12);
				int count = (0177777 - tmbc) + 1;
				byte[] recInfo = encodeRecordSize(count);
				byte[] buffer = new byte[count];
				for (i = 0; (i < buffer.length) && (count > 0); count -= 2) {
					temp = ((int) unibus.read(addr)) & 0177777;
					buffer[i] = (byte)(temp & 0xff);
					buffer[i + 1] = (byte)((temp >> 8) & 0xff);
					addr += 2;
					i += 2;
				}
				file.write(recInfo);
				file.write(buffer);
				file.write(recInfo);
				tmbc = ((0177777 - count) + 1) & 0177777;
				if ((count != 0) || (i != buffer.length)) {
					tmer |= TM_RLE;
				}
			} catch (Trap e) {
				tmer |= TM_NXM;
				return;
			} catch (IOException e) {
				tmer |= TM_CRC;
				return;
			}
		} else {
			tmer |= TM_ILC;
		}
	}

	private void doWriteEof() {
		if (file != null) {
			try {
				byte[] recInfo = encodeRecordSize(0);
				file.write(recInfo);
				file.write(recInfo);
			} catch (IOException e) {
				tmer |= TM_CRC;
				return;
			}
		} else {
			tmer |= TM_ILC;
		}
	}

	private void doSpaceForward() {
		if (file != null) {
			try {
				byte[] recInfo = new byte[4];
				int count = (0177777 - tmbc) + 1;
				while (count > 0) {
					file.readFully(recInfo);
					byte[] buffer = new byte[decodeRecordSize(recInfo)];
					file.readFully(buffer);
					file.readFully(recInfo);
					if (decodeRecordSize(recInfo) == 0) {
						tmer |= TM_EOF;
						break;
					}
					count--;
				}
				tmbc = (0177777 - count) + 1;
			} catch (IOException e) {
				tmer |= TM_CRC;
				return;
			}
		} else {
			tmer |= TM_ILC;
		}
	}

	private void doSpaceReverse() {
		if (file != null) {
			try {
				long temp1;
				int temp2;
				byte[] recInfo = new byte[4];
				int count = (0177777 - tmbc) + 1;
				tmbc = (0177777 - count) + 1;
				while (count > 0) {
					temp1 = file.getFilePointer();
					if (temp1 == 0) {
						tmer |= TM_BOT;
						break;
					}
					file.seek(temp1 - 4);
					file.readFully(recInfo);
					temp2 = decodeRecordSize(recInfo);
					file.seek(temp1 - (8 + temp2));
					count--;
				}
			} catch (IOException e) {
				tmer |= TM_CRC;
				return;
			}
		} else {
			tmer |= TM_ILC;
		}
	}
	
}

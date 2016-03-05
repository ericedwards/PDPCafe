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
// KLConsoleDevice.java - KL11/DL11 Console.
//

package PDPCafe;

import java.net.*;
import java.io.*;

public class KLConsoleDevice extends Thread implements UnibusDevice {

	private static final int DEFAULT_BASE = 0777560;
	private static final int DEFAULT_SIZE = 4;
	private static final int DEFAULT_TELNET_PORT = 2000;
	private static final int DEFAULT_VECTOR = 060;

	private static final int READY = 0200;	// ready bit
	private static final int IE = 0100;		// interrupt enable bit
	private static final int DELAY = 100;	// delay until ready after send
	private static final int BRLEVEL = 4;	// br (interrupt) level

	private UnibusDeviceInfo info;
	private Unibus u;
	private String options;
	private int rsr;
	private int rdr;
	private int tsr;
	private int tdr;
	private int port;
	private Socket socket;
	private int rvector;
	private int tvector;

	public KLConsoleDevice() {
		this(DEFAULT_BASE, DEFAULT_SIZE, "");
	}

	public KLConsoleDevice(int base, int size, String options) {
		this.options = options;
		rsr = 0;
		rdr = 0177;
		tsr = READY;
		tdr = 0;
		u = Unibus.instance();
		if (options == "1") {
			port = DEFAULT_TELNET_PORT + 1;
			rvector = 0300;
			tvector = 0304;
			info = new UnibusDeviceInfo(this, base, size, "KL11 #2", false);
		} else {
			port = DEFAULT_TELNET_PORT;
			rvector = DEFAULT_VECTOR;
			tvector = DEFAULT_VECTOR + 4;
			info = new UnibusDeviceInfo(this, base, size, "KL11", false);
		}
		u.registerDevice(info);
		this.start();
	}

	public void reset() {
		synchronized(this) {
			rsr = 0;
			rdr = 0177;
			tsr = READY;
			tdr = 0;
			u.cancelInterrupt(this, BRLEVEL, tvector);
			u.cancelInterrupt(this, BRLEVEL, rvector);
			u.cancelEvents(this);
		}
	}

	public short read(int addr) throws Trap {
		int data;
		switch(addr - info.base) {
		case 0:
			data = rsr;
			break;
		case 2:
			data = rdr;
			synchronized(this) {
				rsr &= ~READY;
				this.interrupt();		// wake up the receiver
			}
			break;
		case 4:
			data = tsr;
			break;
		case 6:
			data = tdr;
			break;
		default:
			throw new Trap(Trap.UnibusTimeout);
		}
		return (short) data;
	}

	public void write(int addr, short data) throws Trap {
		int temp = ((int) data) & 0177777;
		switch (addr - info.base) {
		case 0:
			if (((rsr & IE) == 0) && ((temp & IE) != 0) &&
			  ((rsr & READY) != 0)) {
				u.scheduleInterrupt(this, BRLEVEL, rvector);
			}
			rsr &= ~IE;
			rsr |= (temp & IE);
			break;
		case 2:
			break;
		case 4:
			if (((tsr & IE) == 0) && ((temp & IE) != 0) &&
			  ((tsr & READY) != 0)) {
				u.scheduleInterrupt(this, BRLEVEL, tvector);
			}
			tsr &= ~IE;
			tsr |= (temp & IE);
			break;
		case 6:
			if ((tsr & READY) != 0) {
				try {
					if (socket != null) {
						socket.getOutputStream().write(data & 0177);
					}
				} catch (Exception e) {
					// ignore any exceptions
				}
				tsr &= ~READY;
				u.scheduleEvent(this, DELAY, 0);
			}
			break;
		default:
			throw new Trap(Trap.UnibusTimeout);
		}
	}

	public void writebyte(int addr, byte data) throws Trap {
		if (( addr & 1) != 0) {
			return;
		}
		write(addr, (short) data);
	}

	public void eventService(int data) {
		tsr |= READY;
		if ((tsr & IE) != 0) {
			u.scheduleInterrupt(this, BRLEVEL, tvector);
		}
	}

	public void interruptService() {
	}

	public void run() {
		while (true) {
			try {
				ServerSocket s = new ServerSocket(port, 1);
				socket = s.accept();
				s.close();
				try {
					while (true) {
						int r = socket.getInputStream().read();
						if (r == -1) break;				// socket closed
						synchronized(this) {
							if ((rsr & READY) == 0) {	// dump char if not rdy
								rsr |= READY;			// set ready
								rdr = r & 0377;			// mask to 8 bits
								if ((rsr & IE) != 0) {	// make interrupt
									u.scheduleInterrupt(this, BRLEVEL, rvector);
								}
							}
						}
						// sleep for a second or until data register is read
						// keeps us fom overunning the recevier data
						try {
							Thread.sleep(1000);
						} catch (InterruptedException e2) {
							// ignore the exception, probably the data
							// register got read
						}
					}
					socket.close();
				} catch (IOException e) {
					socket.close();
				}
			} catch (IOException e) {
				try {
					// can't open socket, pause and try again
					Thread.sleep(1000);
				} catch (InterruptedException e2) {
					// ignore the exception
				}
			}
		}
	}

}

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
// CPUDevice.java - PDP-11/34 Processor simulation.
//

package PDPCafe;

public class CPUDevice extends Thread implements UnibusDevice {

	private static final int CPU_PSW = 0777776;
	private static final int CPU_PSW_SIZE = 1;

	private static final int PC = 7;
	private static final int SP = 6;
	private static final int R5 = 5;
	private static final int C_BIT = 01;
	private static final int V_BIT = 02;
	private static final int Z_BIT = 04;
	private static final int N_BIT = 010;
	private static final int T_BIT = 020;

	private static final int MNI = 0100000;
	private static final int LNI = 0177777;
	private static final int MPI = 077777;
	private static final int LPI = 1;
	private static final int CARRY = 0200000;

	private static final int MNB = 0200;
	private static final int LNB = 0377;
	private static final int MPB = 0177;
	private static final int LPB = 1;
	private static final int CARRYB = 0400;

	private static final int MNL = 0x80000000;

	// LOOK_COUNT and WAIT_SLEEP should be configurable through options
	//   LOOK_COUNT = 0 is required for cpu trap tests to pass
	//   LOOK_COUNT = 50 is works well for most things
	//   WAIT_SLEEP should be chnaged based on platform support

	private static final int LOOK_COUNT = 50;	// instructions between each
	//private static final int LOOK_COUNT = 0;	// instructions between each
	private static final int WAIT_SLEEP = 10;	// # of msec to give up cpu
	private static final int SYNC_SLEEP = 1000;	// # of msec to wait for sync
	private static final int SYNC_LOOP = 3;		// # of loops to wait for sync

	private static CPUDevice theInstance = null;

	public short[] regs;
	public short[] stacks;
	public int psw;
	public long lastExecuted = 0;

	private int ir;
	private boolean stackCheck;
	private short savedAddress;
	private Unibus unibus;
	private KTDevice mmu;
	private KWDevice kw;

	private boolean runRequest;
	private boolean runStatus;
	private boolean runRequestSingle;

	private CPUDevice() {
		psw = 0340;
		regs = new short[8];
		for (int i = 0; i < 8; ++i) {
			regs[i] = 0;
		}
		stacks = new short[4];
		UnibusDeviceInfo info;
		unibus = Unibus.instance();
		info = new UnibusDeviceInfo(this, CPU_PSW, CPU_PSW_SIZE, "PSW", true);
		unibus.registerDevice(info);
		kw = KWDevice.instance();
		runRequest = false;
		runStatus = false;
	}

	public static final synchronized CPUDevice instance() {
		if (theInstance == null) {
			theInstance = new CPUDevice();
			theInstance.start();
		}
		return theInstance;
	}

	public void reset() {
	}

	public short read(int addr) throws Trap {
		return(short) psw;
	}

	public void write(int addr, short data) throws Trap {
		int oldmode = (psw & 0140000) >>> 14;
		psw = (((int) data) & LNI) & ~T_BIT;
		int newmode = (psw & 0140000) >>> 14;
		stacks[oldmode] = regs[SP];
		regs[SP] = stacks[newmode];
	}

	public void writebyte(int addr, byte data) throws Trap {
		int t = ((int) read(addr & 0777776)) & LNI;
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

	public synchronized boolean startExecution(boolean singleStep) {
		if (!runStatus) {
			runRequestSingle = singleStep;
			lastExecuted = 0;
			runRequest = true;
			this.interrupt();
			for (int i = 0; (i < SYNC_LOOP) &&
			(!runStatus) && (lastExecuted == 0); ++i) {
				try {
					sleep(SYNC_SLEEP);
				} catch (InterruptedException e) {
					// do nothing
				}
			}
			if (runStatus || (lastExecuted > 0))
				return true;
		}
		return false;
	}

	public synchronized boolean stopExecution() {
		if (runStatus) {
			runRequest = false;
			this.interrupt();
			for (int i = 0; (i < SYNC_LOOP) && runStatus; ++i) {
				try {
					sleep(SYNC_SLEEP);
				} catch (InterruptedException e) {
					// do nothing
				}
			}
			if (!runStatus)
				return true;
		}
		return false;
	}

	public synchronized boolean isExecuting() {
		return runStatus;
	}

	public void run() {
		long start, stop;
		while (true) {
			while (!runRequest) {
				try {
					sleep(SYNC_SLEEP);
				} catch (InterruptedException e) {
					// do nothing
				}
			}
			runStatus = true;
			start = System.currentTimeMillis();
			lastExecuted = run2(runRequestSingle);
			stop = System.currentTimeMillis();
			if (!runRequestSingle) {
				System.out.println("\nProcessor Halted: " +
				lastExecuted + " instructions executed in " +
				((stop - start) / 1000) + " seconds");
			}
			runStatus = false;
			runRequest = false;
		}
	}

	public final long run2(boolean singleStep) {
		boolean flag = !singleStep;
		short tempPC;
		long total = 0;	 // make class member ?
		int look = 0;	 // make class member ?
		int rtt = 0;
		mmu = KTDevice.instance();
		do {
			stackCheck = false;
			mmu.mmr2update(regs[PC]);
			try {
				ir = ((int) mmu.logicalRead(regs[PC])) & 0177777;
				regs[PC] += 2;
				decodeAndExecute();
			} catch (Trap trap) {
				try {
					if (trap.vector >= 0) {
						service(trap.vector);
					} else {
						switch (trap.vector) {
						case Trap.RTTInstruction:
							rtt = 1;
							break;
						case Trap.WaitInstruction:
							while (!unibus.waitingInterrupt((psw & 0340) >> 5)) {
								kw.pollClock();
								unibus.runEvents(LOOK_COUNT + 50);
								//unibus.runEvents(LOOK_COUNT); GROK GROK
								// give the cpu back for a bit if
								// there are no interrupts waiting
								if (!unibus.waitingInterrupt((psw & 0340) >> 5)) {
									try {
										sleep(WAIT_SLEEP);
									} catch (InterruptedException e) {
										// do nothing
									}
								}
								if (!runRequest) {
									flag = false;
									break;
								}
							}
							look = LOOK_COUNT;
							break;
						default:
							System.out.println("\nTrap=" + trap.vector +
								" pc=" + regs[PC] + " ir=" + ir);
							flag = false;
							break;
						}
					}
				} catch (Trap doubleTrap) {
					System.out.println("\nDouble Trap");
					flag = false;
				}
			}
			if (stackCheck) {
				try {
					service(Trap.StackLimit);
					stackCheck = false;
				} catch (Trap doubleTrap) {
					System.out.println("\nDouble Trap");
					flag = false;
				}
			}
			if (((psw & T_BIT) != 0) && (rtt == 0 )) {
				try {
					service(Trap.BreakpointTrap);
					if (stackCheck) {
						service(Trap.StackLimit);
						stackCheck = false;
					}
				} catch (Trap doubleTrap) {
					System.out.println("\nDouble Trap");
					flag = false;
				}
			}
			rtt = 0;
			++total;
			++look;
			if (look > LOOK_COUNT) {
				UnibusInterrupt ie;
				kw.pollClock();
				unibus.runEvents(look);
				try {
					while ((ie = unibus.runInterrupts((psw & 0340) >> 5)) != null) {
						service(ie.vector);
						ie.device.interruptService();
						if (stackCheck) {
							service(Trap.StackLimit);
							stackCheck = false;
						}
					}
				} catch (Trap doubleTrap) {
					System.out.println("\nDouble Trap");
					flag = false;
				}
				if (!runRequest) {
					flag = false;
				}
				look = 0;
			}
		} while (flag);
		return total;
	}

	private final boolean isKernel() {
		if (((psw & 0140000) >>> 14) == 0)
			return true;
		else
			return false;
	}

	private final boolean stackLimit() {
		int temp = ((int) regs[SP]) & LNI;
		if (temp < 0400)
			return true;
		else
			return false;
	}

	private final void service(int vector) throws Trap {
		int oldmode = (psw & 0140000) >>> 14;
		int oldpsw = psw;
		short oldpc = regs[PC];
		regs[PC] = mmu.logicalReadKernel((short) vector);
		psw = ((int)mmu.logicalReadKernel((short) (vector + 2))) & LNI;
		int newmode = (psw & 0140000) >>> 14;
		stacks[oldmode] = regs[SP];
		regs[SP] = stacks[newmode];
		psw = psw & 0147777;
		psw |= (oldmode << 12);
		push((short) oldpsw);
		push(oldpc);
	}

	private final short loadSource() throws Trap {
		int sourceMode = (ir & 07000) >> 9;
		int sourceReg = (ir & 0700) >> 6;
		short temp1;
		switch (sourceMode) {
		case 0:
			return regs[sourceReg];
		case 1:
			return mmu.logicalRead(regs[sourceReg]);
		case 2:
			temp1 = mmu.logicalRead(regs[sourceReg]);
			regs[sourceReg] += 2;
			return temp1;
		case 3:
			temp1 = mmu.logicalRead(regs[sourceReg]);
			regs[sourceReg] += 2;
			return mmu.logicalRead(temp1);
		case 4:
			regs[sourceReg] -= 2;
			if ((sourceReg == SP) && isKernel() && stackLimit()) {
				stackCheck = true;
			}
			return mmu.logicalRead(regs[sourceReg]);
		case 5:
			regs[sourceReg] -= 2;
			if ((sourceReg == SP) && isKernel() && stackLimit()) {
				stackCheck = true;
			}
			temp1 = mmu.logicalRead(regs[sourceReg]);
			return mmu.logicalRead(temp1);
		case 6:
			temp1 = mmu.logicalRead(regs[PC]);
			regs[PC] += 2;
			temp1 = (short)(regs[sourceReg] + temp1);
			return mmu.logicalRead(temp1);
		case 7:
			temp1 = mmu.logicalRead(regs[PC]);
			regs[PC] += 2;
			temp1 = (short)(regs[sourceReg] + temp1);
			temp1 = mmu.logicalRead(temp1);
			return mmu.logicalRead(temp1);
		}
		throw new Trap(Trap.Unimplemented);
	}

	private final void storeDest(short data) throws Trap {
		int destMode = (ir & 070) >> 3;
		int destReg = ir & 07;
		short temp1;
		switch (destMode) {
		case 0:
			regs[destReg] = data;
			break;
		case 1:
			mmu.logicalWrite(regs[destReg], data);
			break;
		case 2:
			mmu.logicalWrite(regs[destReg], data);
			regs[destReg] += 2;
			break;
		case 3:
			temp1 = mmu.logicalRead(regs[destReg]);
			regs[destReg] += 2;
			mmu.logicalWrite(temp1, data);
			break;
		case 4:
			regs[destReg] -= 2;
			if ((destReg == SP) && isKernel() && stackLimit()) {
				stackCheck = true;
			}
			mmu.logicalWrite(regs[destReg], data);
			break;
		case 5:
			regs[destReg] -= 2;
			if ((destReg == SP) && isKernel() && stackLimit()) {
				stackCheck = true;
			}
			temp1 = mmu.logicalRead(regs[destReg]);
			mmu.logicalWrite(temp1, data);
			break;
		case 6:
			temp1 = mmu.logicalRead(regs[PC]);
			regs[PC] += 2;
			temp1 = (short)(regs[destReg] + temp1);
			mmu.logicalWrite(temp1, data);
			break;
		case 7:
			temp1 = mmu.logicalRead(regs[PC]);
			regs[PC] += 2;
			temp1 = (short)(regs[destReg] + temp1);
			temp1 = mmu.logicalRead(temp1);
			mmu.logicalWrite(temp1, data);
			break;
		}
	}

	private final short loadDest() throws Trap {
		int destMode = (ir & 070) >> 3;
		int destReg = ir & 07;
		short temp1;
		switch (destMode) {
		case 0:
			return regs[destReg];
		case 1:
			savedAddress = regs[destReg];
			return mmu.logicalRead(regs[destReg]);
		case 2:
			savedAddress = regs[destReg];
			temp1 = mmu.logicalRead(regs[destReg]);
			regs[destReg] += 2;
			return temp1;
		case 3:
			temp1 = mmu.logicalRead(regs[destReg]);
			savedAddress = temp1;
			regs[destReg] += 2;				// wrong place?
			return mmu.logicalRead(temp1);
		case 4:
			regs[destReg] -= 2;
			if ((destReg == SP) && isKernel() && stackLimit()) {
				stackCheck = true;
			}
			savedAddress = regs[destReg];
			return mmu.logicalRead(regs[destReg]);
		case 5:
			regs[destReg] -= 2;
			if ((destReg == SP) && isKernel() && stackLimit()) {
				stackCheck = true;
			}
			temp1 = mmu.logicalRead(regs[destReg]);
			savedAddress = temp1;
			return mmu.logicalRead(temp1);
		case 6:
			temp1 = mmu.logicalRead(regs[PC]);
			regs[PC] += 2;
			temp1 = (short)(regs[destReg] + temp1);
			savedAddress = temp1;
			return mmu.logicalRead(temp1);
		case 7:
			temp1 = mmu.logicalRead(regs[PC]);
			regs[PC] += 2;
			temp1 = (short)(regs[destReg] + temp1);
			temp1 = mmu.logicalRead(temp1);
			savedAddress = temp1;
			return mmu.logicalRead(temp1);
		}
		throw new Trap(Trap.Unimplemented);
	}

	private final void storeDest2(short data) throws Trap {
		int destMode = (ir & 070) >> 3;
		if (destMode == 0) {
			int destReg = ir & 07;
			regs[destReg] = data;
		} else {
			mmu.logicalWrite(savedAddress, data);
		}
	}

	private final short loadEffectiveAddress() throws Trap {
		int destMode = (ir & 070) >> 3;
		int destReg = ir & 07;
		short temp1;
		switch (destMode) {
		case 0:
			throw new Trap(Trap.IllegalInstruction);
		case 1:
			return regs[destReg];
		case 2:
			temp1 = regs[destReg];
			regs[destReg] += 2;
			return temp1;
		case 3:
			// behavior on trap
			temp1 = regs[destReg];
			regs[destReg] += 2;
			return mmu.logicalRead(temp1);
		case 4:
			regs[destReg] -= 2;
			if ((destReg == SP) && isKernel() && stackLimit()) {
				stackCheck = true;
			}
			return regs[destReg];
		case 5:
			regs[destReg] -= 2;
			if ((destReg == SP) && isKernel() && stackLimit()) {
				stackCheck = true;
			}
			temp1 = mmu.logicalRead(regs[destReg]);
			return temp1;
		case 6:
			temp1 = mmu.logicalRead(regs[PC]);
			regs[PC] += 2;
			temp1 = (short)(regs[destReg] + temp1);
			return temp1;
		case 7:
			temp1 = mmu.logicalRead(regs[PC]);
			regs[PC] += 2;
			temp1 = (short)(regs[destReg] + temp1);
			temp1 = mmu.logicalRead(temp1);
			return temp1;
		}
		throw new Trap(Trap.Unimplemented);
	}

	private final short loadDestPrevious() throws Trap {
		int destMode = (ir & 070) >> 3;
		int destReg = ir & 07;
		short temp1;
		switch (destMode) {
		case 0:
			if (destReg == SP) {
				int current = (psw & 0140000) >>> 14;
				int previous = (psw & 030000) >>> 12;
				if (current != previous) {
					return stacks[previous];
				} else {
					return regs[SP];
				}
			} else {
				return regs[destReg];
			}
		case 1:
			return mmu.logicalReadPrevious(regs[destReg]);
		case 2:
			temp1 = mmu.logicalReadPrevious(regs[destReg]);
			regs[destReg] += 2;
			return temp1;
		case 3:
			temp1 = mmu.logicalRead(regs[destReg]);
			temp1 = mmu.logicalReadPrevious(temp1);
			regs[destReg] += 2;
			return temp1;
		case 4:
			regs[destReg] -= 2;
			if ((destReg == SP) && isKernel() && stackLimit()) {
				stackCheck = true;
			}
			return mmu.logicalReadPrevious(regs[destReg]);
		case 5:
			regs[destReg] -= 2;
			if ((destReg == SP) && isKernel() && stackLimit()) {
				stackCheck = true;
			}
			temp1 = mmu.logicalRead(regs[destReg]);
			return mmu.logicalReadPrevious(temp1);
		case 6:
			temp1 = mmu.logicalRead(regs[PC]);
			regs[PC] += 2;
			temp1 = (short)(regs[destReg] + temp1);
			return mmu.logicalReadPrevious(temp1);
		case 7:
			temp1 = mmu.logicalRead(regs[PC]);
			regs[PC] += 2;
			temp1 = (short)(regs[destReg] + temp1);
			temp1 = mmu.logicalRead(temp1);
			return mmu.logicalReadPrevious(temp1);
		}
		throw new Trap(Trap.Unimplemented);
	}

	private final void storeDestPrevious(short data) throws Trap {
		int destMode = (ir & 070) >> 3;
		int destReg = ir & 07;
		short temp1;
		switch (destMode) {
		case 0:
			if (destReg == SP) {
				int current = (psw & 0140000) >>> 14;
				int previous = ((psw & 030000) >>> 12);
				if (current != previous) {
					stacks[previous] = data;
				} else {
					regs[SP] = data;
				}
			} else {
				regs[destReg] = data;
			}
			break;
		case 1:
			mmu.logicalWritePrevious(regs[destReg], data);
			break;
		case 2:
			mmu.logicalWritePrevious(regs[destReg], data);
			regs[destReg] += 2;
			break;
		case 3:
			temp1 = mmu.logicalRead(regs[destReg]);
			mmu.logicalWritePrevious(temp1, data);
			regs[destReg] += 2;
			break;
		case 4:
			regs[destReg] -= 2;
			if ((destReg == SP) && isKernel() && stackLimit()) {
				stackCheck = true;
			}
			mmu.logicalWritePrevious(regs[destReg], data);
			break;
		case 5:
			regs[destReg] -= 2;
			if ((destReg == SP) && isKernel() && stackLimit()) {
				stackCheck = true;
			}
			temp1 = mmu.logicalRead(regs[destReg]);
			mmu.logicalWritePrevious(temp1, data);
			break;
		case 6:
			temp1 = mmu.logicalRead(regs[PC]);
			regs[PC] += 2;
			temp1 = (short)(regs[destReg] + temp1);
			mmu.logicalWritePrevious(temp1, data);
			break;
		case 7:
			temp1 = mmu.logicalRead(regs[PC]);
			regs[PC] += 2;
			temp1 = (short)(regs[destReg] + temp1);
			temp1 = mmu.logicalRead(temp1);
			mmu.logicalWritePrevious(temp1, data);
			break;
		}
	}

	private final short pop() throws Trap {
		short data = mmu.logicalRead(regs[SP]);
		regs[SP] += 2;
		return data;
	}

	private final void push(short data) throws Trap {
		regs[SP] -= 2;
		if (isKernel() && stackLimit()) {
			stackCheck = true;
		}
		mmu.logicalWrite(regs[SP], data);
	}

	private final byte loadSourceByte() throws Trap {
		int sourceMode = (ir & 07000) >> 9;
		int sourceReg = (ir & 0700) >> 6;
		short temp1;
		byte temp2;
		switch (sourceMode) {
		case 0:
			return(byte)(regs[sourceReg] & LNB);
		case 1:
			return mmu.logicalReadByte(regs[sourceReg]);
		case 2:
			temp2 = mmu.logicalReadByte(regs[sourceReg]);
			if (sourceReg >= 6) {
				regs[sourceReg] += 2;
			} else {
				regs[sourceReg] += 1;
			}
			return temp2;
		case 3:
			temp1 = mmu.logicalRead(regs[sourceReg]);
			regs[sourceReg] += 2;
			return mmu.logicalReadByte(temp1);
		case 4:
			if (sourceReg >= 6) {
				regs[sourceReg] -= 2;
				if ((sourceReg == SP) && isKernel() && stackLimit()) {
					stackCheck = true;
				}
			} else {
				regs[sourceReg] -= 1;
			}
			return mmu.logicalReadByte(regs[sourceReg]);
		case 5:
			regs[sourceReg] -= 2;
			if ((sourceReg == SP) && isKernel() && stackLimit()) {
				stackCheck = true;
			}
			temp1 = mmu.logicalRead(regs[sourceReg]);
			return mmu.logicalReadByte(temp1);
		case 6:
			temp1 = mmu.logicalRead(regs[PC]);
			regs[PC] += 2;
			temp1 = (short)(regs[sourceReg] + temp1);
			return mmu.logicalReadByte(temp1);
		case 7:
			temp1 = mmu.logicalRead(regs[PC]);
			regs[PC] += 2;
			temp1 = (short)(regs[sourceReg] + temp1);
			temp1 = mmu.logicalRead(temp1);
			return mmu.logicalReadByte(temp1);
		}
		throw new Trap(Trap.Unimplemented);
	}

	private final void storeDestByteExt(byte data) throws Trap {
		int destMode = (ir & 070) >> 3;
		if (destMode == 0) {
			int destReg = ir & 07;
			regs[destReg] = (short) data;
		} else {
			storeDestByte(data);
		}
	}

	private final void storeDestByte(byte data) throws Trap {
		int destMode = (ir & 070) >> 3;
		int destReg = ir & 07;
		short temp1;
		int temp2;
		switch (destMode) {
		case 0:
			temp2 = ((int) regs[destReg]) & 0177400;
			temp2 += ((int) data) & LNB;
			regs[destReg] = (short) temp2;
			break;
		case 1:
			mmu.logicalWriteByte(regs[destReg], data);
			break;
		case 2:
			mmu.logicalWriteByte(regs[destReg], data);
			if (destReg >= 6) {
				regs[destReg] += 2;
			} else {
				regs[destReg] += 1;
			}
			break;
		case 3:
			temp1 = mmu.logicalRead(regs[destReg]);
			regs[destReg] += 2;
			mmu.logicalWriteByte(temp1, data);
			break;
		case 4:
			if (destReg >= 6) {
				regs[destReg] -= 2;
				if ((destReg == SP) && isKernel() && stackLimit()) {
					stackCheck = true;
				}
			} else {
				regs[destReg] -= 1;
			}
			mmu.logicalWriteByte(regs[destReg], data);
			break;
		case 5:
			regs[destReg] -= 2;
			if ((destReg == SP) && isKernel() && stackLimit()) {
				stackCheck = true;
			}
			temp1 = mmu.logicalRead(regs[destReg]);
			mmu.logicalWriteByte(temp1, data);
			break;
		case 6:
			temp1 = mmu.logicalRead(regs[PC]);
			regs[PC] += 2;
			temp1 = (short)(regs[destReg] + temp1);
			mmu.logicalWriteByte(temp1, data);
			break;
		case 7:
			temp1 = mmu.logicalRead(regs[PC]);
			regs[PC] += 2;
			temp1 = (short)(regs[destReg] + temp1);
			temp1 = mmu.logicalRead(temp1);
			mmu.logicalWriteByte(temp1, data);
			break;
		}
	}

	private final byte loadDestByte() throws Trap {
		int destMode = (ir & 070) >> 3;
		int destReg = ir & 07;
		short temp1;
		byte temp2;
		switch (destMode) {
		case 0:
			return(byte)(regs[destReg] & LNB);
		case 1:
			savedAddress = regs[destReg];
			return mmu.logicalReadByte(regs[destReg]);
		case 2:
			savedAddress = regs[destReg];
			temp2 = mmu.logicalReadByte(regs[destReg]);
			if (destReg >= 6) {
				regs[destReg] += 2;
			} else {
				regs[destReg] += 1;
			}
			return temp2;
		case 3:
			temp1 = mmu.logicalRead(regs[destReg]);
			savedAddress = temp1;
			regs[destReg] += 2;
			return mmu.logicalReadByte(temp1);
		case 4:
			if (destReg >= 6) {
				regs[destReg] -= 2;
				if ((destReg == SP) && isKernel() && stackLimit()) {
					stackCheck = true;
				}
			} else {
				regs[destReg] -= 1;
			}
			savedAddress = regs[destReg];
			return mmu.logicalReadByte(regs[destReg]);
		case 5:
			regs[destReg] -= 2;
			if ((destReg == SP) && isKernel() && stackLimit()) {
				stackCheck = true;
			}
			temp1 = mmu.logicalRead(regs[destReg]);
			savedAddress = temp1;
			return mmu.logicalReadByte(temp1);
		case 6:
			temp1 = mmu.logicalRead(regs[PC]);
			regs[PC] += 2;
			temp1 = (short)(regs[destReg] + temp1);
			savedAddress = temp1;
			return mmu.logicalReadByte(temp1);
		case 7:
			temp1 = mmu.logicalRead(regs[PC]);
			regs[PC] += 2;
			temp1 = (short)(regs[destReg] + temp1);
			temp1 = mmu.logicalRead(temp1);
			savedAddress = temp1;
			return mmu.logicalReadByte(temp1);
		}
		throw new Trap(Trap.Unimplemented);
	}

	private final void storeDestByte2(byte data) throws Trap {
		int destMode = (ir & 070) >> 3;
		if (destMode == 0) {
			int destReg = ir & 07;
			int temp;
			temp = ((int) regs[destReg]) & 0177400;
			temp += ((int) data) & LNB;
			regs[destReg] = (short) temp;
		} else {
			mmu.logicalWriteByte(savedAddress, data);
		}
	}

	//
	// Instruction Implementations
	//

	private final void executeHALT() throws Trap {
		if (isKernel()) {
			throw new Trap(Trap.HaltInstruction);
		} else {
			// 11/34 throws reserved -- 11/44 throws illegal
			throw new Trap(Trap.ReservedInstruction);
		}
	}

	private final void executeWAIT() throws Trap {
		if (isKernel()) {
			throw new Trap(Trap.WaitInstruction);
		}
	}

	private final void executeBPT() throws Trap {
		throw new Trap(Trap.BreakpointTrap);
	}

	private final void executeIOT() throws Trap {
		throw new Trap(Trap.IOTrap);
	}

	private final void executeRESET() throws Trap {
		if (isKernel()) {
			unibus.reset();
		}
	}

	private final void executeRTIorRTT() throws Trap {
		int oldmode = (psw & 0140000) >>> 14;
		short newpc = pop();
		int newpsw = ((int) pop()) & LNI;
		if (!isKernel()) {
			newpsw &= ~0340;
			newpsw |= (psw & 0340);
			psw = newpsw | (psw & 0170000);
		} else {
			psw = newpsw;
		}
		int newmode = (psw & 0140000) >>> 14;
		regs[PC] = newpc;
		stacks[oldmode] = regs[SP];
		regs[SP] = stacks[newmode];
		if (ir == 0000006) {
			throw new Trap(Trap.RTTInstruction);
		}
	}

	private final void executeJMP() throws Trap {
		regs[PC] = loadEffectiveAddress();
	}

	private final void executeRTS() throws Trap {
		int destReg = ir & 07;
		regs[PC] = regs[destReg];
		regs[destReg] = pop();
	}

	private final void executeCC() throws Trap {
		if ((ir & 020) == 0) {
			psw &= ~(ir & 017);
		} else {
			psw |= (ir & 017);
		}
	}

	private final void executeSWAB() throws Trap {
		int data1 = ((int) loadDest()) & LNI;
		int data2 = (data1 << 8 ) & 0xff00;
		int data3 = (data1 >> 8 ) & 0x00ff;
		data1 = data2 + data3;
		psw &= ~C_BIT;
		psw &= ~V_BIT;
		if ((data1 & LNB) == 0) {
			psw |= Z_BIT;
		} else {
			psw &= ~Z_BIT;
		}
		if ((data1 & MNB) == 0) {
			psw &= ~N_BIT;
		} else {
			psw |= N_BIT;
		}
		storeDest2((short) data1);
	}

	private final void executeBR() throws Trap {
		int temp1 = ir & 0377;
		if ((temp1 & 0200) != 0) {
			temp1 += 0177400;
		}
		temp1 = regs[PC] + (temp1 * 2);
		regs[PC] = (short) temp1;
	}

	private final void executeBNEorBEQ() throws Trap {
		if (((psw >> 2) & 1) == ((ir >> 8) & 1)) {
			executeBR();
		}
	}

	private final void executeBGEorBLT() throws Trap {
		int temp = ((psw >> 3) & 1) ^ ((psw >> 1) & 1);
		if (temp == ((ir >> 8) & 1)) {
			executeBR();
		}
	}

	private final void executeBGTorBLE() throws Trap {
		int nbit = (psw >> 3) & 1;
		int vbit = (psw >> 1) & 1;
		int zbit = (psw >> 2) & 1;
		if (((nbit ^ vbit) | zbit) == ((ir >> 8) & 1)) {
			executeBR();
		}
	}

	private final void executeJSR() throws Trap {
		int sourceReg = (ir & 0700) >> 6;
		short data = loadEffectiveAddress();
		push(regs[sourceReg]);
		regs[sourceReg] = regs[PC];
		regs[PC] = data;
	}

	private final void executeCLR() throws Trap {
		psw &= ~C_BIT;
		psw &= ~V_BIT;
		psw |= Z_BIT;
		psw &= ~N_BIT;
		storeDest((short) 0);
	}

	private final void executeCOM() throws Trap {
		int data = ((int) loadDest()) & LNI;
		data = ~data & LNI;
		if ((data & LNI) == 0) {
			psw |= Z_BIT;
		} else {
			psw &= ~Z_BIT;
		}
		if ((data & MNI) == 0) {
			psw &= ~N_BIT;
		} else {
			psw |= N_BIT;
		}
		psw &= ~V_BIT;
		psw |= C_BIT;
		storeDest2((short) data);
	}

	private final void executeINC() throws Trap {
		int data = ((int) loadDest()) & LNI;
		if (data == MPI) {
			psw |= V_BIT;
		} else {
			psw &= ~V_BIT;
		}
		++data;
		if ((data & LNI) == 0) {
			psw |= Z_BIT;
		} else {
			psw &= ~Z_BIT;
		}
		if ((data & MNI) == 0) {
			psw &= ~N_BIT;
		} else {
			psw |= N_BIT;
		}
		storeDest2((short) data);
	}

	private final void executeDEC() throws Trap {
		int data = ((int) loadDest()) & LNI;
		if (data == MNI) {
			psw |= V_BIT;
		} else {
			psw &= ~V_BIT;
		}
		--data;
		if ((data & LNI) == 0) {
			psw |= Z_BIT;
		} else {
			psw &= ~Z_BIT;
		}
		if ((data & MNI) == 0) {
			psw &= ~N_BIT;
		} else {
			psw |= N_BIT;
		}
		storeDest2((short) data);
	}

	private final void executeNEG() throws Trap {
		int data = ((int) loadDest()) & LNI;
		data = (LNI - data) + 1;
		if ((data & LNI) == 0) {
			psw |= Z_BIT;
		} else {
			psw &= ~Z_BIT;
		}
		if ((data & MNI) == 0) {
			psw &= ~N_BIT;
		} else {
			psw |= N_BIT;
		}
		if ((data & LNI) == MNI) {
			psw |= V_BIT;
		} else {
			psw &= ~V_BIT;
		}
		if ((data & LNI) == 0) {
			psw &= ~C_BIT;
		} else {
			psw |= C_BIT;
		}
		storeDest2((short) data);
	}

	private final void executeADC() throws Trap {
		int data = ((int) loadDest()) & LNI;
		if ((psw & C_BIT) != 0) {
			if (data == MPI) {
				psw |= V_BIT;
			} else {
				psw &= ~V_BIT;
			}
			if (data == LNI) {
				psw |= C_BIT;
			} else {
				psw &= ~C_BIT;
			}
			data++;
		} else {
			psw &= ~V_BIT;
			psw &= ~C_BIT;
		}
		if ((data & LNI) == 0) {
			psw |= Z_BIT;
		} else {
			psw &= ~Z_BIT;
		}
		if ((data & MNI) == 0) {
			psw &= ~N_BIT;
		} else {
			psw |= N_BIT;
		}
		storeDest2((short) data);
	}

	private final void executeSBC() throws Trap {
		int data = ((int) loadDest()) & LNI;
		if (data == MNI) {
			psw |= V_BIT;
		} else {
			psw &= ~V_BIT;
		}
		if ((psw & C_BIT) != 0) {
			if ((data & LNI) == 0) {
				psw |= C_BIT;
			} else {
				psw &= ~C_BIT;
			}
			--data;
		} else {
			psw &= ~C_BIT;
		}
		if ((data & LNI) == 0) {
			psw |= Z_BIT;
		} else {
			psw &= ~Z_BIT;
		}
		if ((data & MNI) == 0) {
			psw &= ~N_BIT;
		} else {
			psw |= N_BIT;
		}
		storeDest2((short) data);
	}

	private final void executeTST() throws Trap {
		int data = ((int) loadDest()) & LNI;
		if ((data & LNI) == 0) {
			psw |= Z_BIT;
		} else {
			psw &= ~Z_BIT;
		}
		if ((data & MNI) == 0) {
			psw &= ~N_BIT;
		} else {
			psw |= N_BIT;
		}
		psw &= ~V_BIT;
		psw &= ~C_BIT;
	}

	private final void executeROR() throws Trap {
		int data = ((int) loadDest()) & LNI;
		int temp = data & 1;
		data >>>= 1;
		if ((psw & C_BIT) != 0) {
			data += MNI;
		}
		if (temp != 0) {
			psw |= C_BIT;
		} else {
			psw &= ~C_BIT;
		}
		if ((data & LNI) == 0) {
			psw |= Z_BIT;
		} else {
			psw &= ~Z_BIT;
		}
		if ((data & MNI) == 0) {
			psw &= ~N_BIT;
		} else {
			psw |= N_BIT;
		}
		if ((((psw >> 3) & 1) ^ (psw & 1)) == 0) {
			psw &= ~V_BIT;
		} else {
			psw |= V_BIT;
		}
		storeDest2((short) data);
	}

	private final void executeROL() throws Trap {
		int data = ((int) loadDest()) & LNI;
		int temp = data & MNI;
		data <<= 1;
		if ((psw & C_BIT) != 0) {
			++data;
		}
		if (temp != 0) {
			psw |= C_BIT;
		} else {
			psw &= ~C_BIT;
		}
		if ((data & LNI) == 0) {
			psw |= Z_BIT;
		} else {
			psw &= ~Z_BIT;
		}
		if ((data & MNI) == 0) {
			psw &= ~N_BIT;
		} else {
			psw |= N_BIT;
		}
		if ((((psw >> 3) & 1) ^ (psw & 1)) == 0) {
			psw &= ~V_BIT;
		} else {
			psw |= V_BIT;
		}
		storeDest2((short) data);
	}

	private final void executeASR() throws Trap {
		int data = ((int) loadDest()) & LNI;
		if ((data & 1) != 0) {
			psw |= C_BIT;
		} else {
			psw &= ~C_BIT;
		}
		data = (data >>> 1) + (data & MNI);
		if ((data & LNI) == 0) {
			psw |= Z_BIT;
		} else {
			psw &= ~Z_BIT;
		}
		if ((data & MNI) == 0) {
			psw &= ~N_BIT;
		} else {
			psw |= N_BIT;
		}
		if ((((psw >> 3) & 1) ^ (psw & 1)) == 0) {
			psw &= ~V_BIT;
		} else {
			psw |= V_BIT;
		}
		storeDest2((short) data);
	}

	private final void executeASL() throws Trap {
		int data = ((int) loadDest()) & LNI;
		if ((data & MNI) != 0) {
			psw |= C_BIT;
		} else {
			psw &= ~C_BIT;
		}
		data <<= 1;
		if ((data & LNI) == 0) {
			psw |= Z_BIT;
		} else {
			psw &= ~Z_BIT;
		}
		if ((data & MNI) == 0) {
			psw &= ~N_BIT;
		} else {
			psw |= N_BIT;
		}
		if ((((psw >> 3) & 1) ^ (psw & 1)) == 0) {
			psw &= ~V_BIT;
		} else {
			psw |= V_BIT;
		}
		storeDest2((short) data);
	}

	private final void executeMARK() throws Trap {
		int data1 = regs[PC] & LNI;
		int data2 = (ir & 077) * 2;
		regs[SP] = (short)(data1 + data2);
		regs[PC] = regs[R5];
		regs[R5] = pop();
	}

	private final void executeMFPI() throws Trap {
		short data = loadDestPrevious();
		push(data);
	}

	private final void executeMTPI() throws Trap {
		short data = pop();
		storeDestPrevious(data);
	}

	private final void executeSXT() throws Trap {
		int data;
		if ((psw & N_BIT) != 0) {
			data = LNI;
			psw &= ~Z_BIT;
		} else {
			data = 0;
			psw |= Z_BIT;
		}
		psw &= ~V_BIT;
		storeDest((short) data);
	}

	private final void executeMOV() throws Trap {
		int data = ((int) loadSource()) & LNI;
		if ((data & LNI) == 0) {
			psw |= Z_BIT;
		} else {
			psw &= ~Z_BIT;
		}
		if ((data & MNI) == 0) {
			psw &= ~N_BIT;
		} else {
			psw |= N_BIT;
		}
		psw &= ~V_BIT;
		storeDest((short) data);
	}

	private final void executeCMP() throws Trap {
		int data1 = ((int) loadSource()) & LNI;
		int data2 = ((int) loadDest()) & LNI;
		int data3 = ~data2 & LNI;
		data3 = data1 + data3 + 1;
		if ((data3 & LNI) == 0) {
			psw |= Z_BIT;
		} else {
			psw &= ~Z_BIT;
		}
		if ((data3 & MNI) == 0) {
			psw &= ~N_BIT;
		} else {
			psw |= N_BIT;
		}
		if (((data1 & MNI) != (data2 & MNI)) && ((data2 & MNI) == (data3 & MNI))) {
			psw |= V_BIT;
		} else {
			psw &= ~V_BIT;
		}
		if ((data3 & CARRY) == 0) {
			psw |= C_BIT;
		} else {
			psw &= ~C_BIT;
		}
	}

	private final void executeBIT() throws Trap {
		int data1 = ((int) loadSource()) & LNI;
		int data2 = ((int) loadDest()) & LNI;
		data2 = data1 & data2;
		if ((data2 & LNI) == 0) {
			psw |= Z_BIT;
		} else {
			psw &= ~Z_BIT;
		}
		if ((data2 & MNI) == 0) {
			psw &= ~N_BIT;
		} else {
			psw |= N_BIT;
		}
		psw &= ~V_BIT;
	}

	private final void executeBIC() throws Trap {
		int data1 = ((int) loadSource()) & LNI;
		int data2 = ((int) loadDest()) & LNI;
		data2 = (~data1 & LNI) & data2;
		if ((data2 & LNI) == 0) {
			psw |= Z_BIT;
		} else {
			psw &= ~Z_BIT;
		}
		if ((data2 & MNI) == 0) {
			psw &= ~N_BIT;
		} else {
			psw |= N_BIT;
		}
		psw &= ~V_BIT;
		storeDest2((short) data2);
	}

	private final void executeBIS() throws Trap {
		int data1 = ((int) loadSource()) & LNI;
		int data2 = ((int) loadDest()) & LNI;
		data2 = data1 | data2;
		if ((data2 & LNI) == 0) {
			psw |= Z_BIT;
		} else {
			psw &= ~Z_BIT;
		}
		if ((data2 & MNI) == 0) {
			psw &= ~N_BIT;
		} else {
			psw |= N_BIT;
		}
		psw &= ~V_BIT;
		storeDest2((short) data2);
	}

	private final void executeADD() throws Trap {
		int data1 = ((int) loadSource()) & LNI;
		int data2 = ((int) loadDest()) & LNI;
		int data3 = data1 + data2;
		if ((data3 & LNI) == 0) {
			psw |= Z_BIT;
		} else {
			psw &= ~Z_BIT;
		}
		if ((data3 & MNI) == 0) {
			psw &= ~N_BIT;
		} else {
			psw |= N_BIT;
		}
		if (((data1 & MNI) == (data2 & MNI)) && ((data1 & MNI) != (data3 & MNI))) {
			psw |= V_BIT;
		} else {
			psw &= ~V_BIT;
		}
		if ((data3 & CARRY) == 0) {
			psw &= ~C_BIT;
		} else {
			psw |= C_BIT;
		}
		storeDest2((short) data3);
	}

	private final void executeMUL() throws Trap {
		int sourceReg = (ir & 0700) >> 6;
		int data1 = regs[sourceReg];
		int data2 = loadDest();
		int data3 = data1 * data2;
		regs[sourceReg] = (short) ((data3 >> 16) & LNI);
		regs[sourceReg|1] = (short) (data3 & LNI);
		if (data3 == 0) {
			psw |= Z_BIT;
		} else {
			psw &= ~Z_BIT;
		}
		if (data3 < 0) {
			psw |= N_BIT;
		} else {
			psw &= ~N_BIT;
		}
		if ((data3 < -32768) || (data3 > 32767)) {
			psw |= C_BIT;
		} else {
			psw &= ~C_BIT;
		}
		psw &= ~V_BIT;
	}

	private final void executeDIV() throws Trap {
		int sourceReg = (ir & 0700) >> 6;
		int temp = ((int)regs[sourceReg]) & LNI;
		temp <<= 16;
		temp += ((int)regs[sourceReg|1]) & LNI;
		int data2 = loadDest();
		if ( data2 == 0 ) {
			psw |= V_BIT;
			psw |= C_BIT;
			return;
		} else {
			psw &= ~C_BIT;
		}
		int eql = temp / data2;
		regs[sourceReg] = (short)(eql & LNI);
		if ((eql > 077777) || (eql < -0100000)) {
			psw |= V_BIT;
		} else {
			psw &= ~V_BIT;
		}

		if (eql < 0) {
			psw |= N_BIT;
		} else {
			psw &= ~N_BIT;
		}
		if (eql == 0) {
			psw |= Z_BIT;
		} else {
			psw &= ~Z_BIT;
		}
		eql = temp % data2;
		regs[sourceReg|1] = (short)(eql & LNI);
	}

	private final void executeASH() throws Trap {
		int old, temp;
		int sourceReg = (ir & 0700) >> 6;
		temp = old = ((int) regs[sourceReg]) & LNI;
		int count = loadDest();
		if ((count & 077) == 0) {
			if ((temp & LNI) == 0) {
				psw |= Z_BIT;
			} else {
				psw &= ~Z_BIT;
			}
			if ((temp & MNI) == 0) {
				psw &= ~N_BIT;
			} else {
				psw |= N_BIT;
			}
			psw &= ~V_BIT;
			psw &= ~C_BIT;
			return;
		}
		if ((count & 040) == 0) {
			count = count & 037;
			psw &= ~V_BIT;
			while (count-- > 0) {
				if ((temp & MNI) == 0) {
					psw &= ~C_BIT;
				} else {
					psw |= C_BIT;
				}
				temp <<= 1;
				if ((temp & MNI) != (old & MNI)) {
					psw |= V_BIT;
				}
			}
		} else {
			count = 0100 - (count & 077);
			int sign = temp & MNI;
			while (count-- > 0) {
				if ((temp & 1) == 0) {
					psw &= ~C_BIT;
				} else {
					psw |= C_BIT;
				}
				temp >>>= 1;
				temp += sign;
			}
			psw &= ~V_BIT;
		}
		regs[sourceReg] = (short) temp;
		if ((temp & LNI) == 0) {
			psw |= Z_BIT;
		} else {
			psw &= ~Z_BIT;
		}
		if ((temp & MNI) == 0) {
			psw &= ~N_BIT;
		} else {
			psw |= N_BIT;
		}
	}

	private final void executeASHC() throws Trap {
		int count;
		int sourceReg = (ir & 0700) >> 6;
		int temp = ((int)regs[sourceReg]) & LNI;
		temp <<= 16;
		temp += ((int)regs[sourceReg|1]) & LNI;
		int old = temp;
		int shift = ((int)loadDest()) & LNI;
		if ((shift & 077) == 0) {			 // no shift
			if ((temp & 0x80000000) == 0) {
				psw &= ~N_BIT;
			} else {
				psw |= N_BIT;
			}
			if (temp == 0) {
				psw |= Z_BIT;
			} else {
				psw &= ~Z_BIT;
			}
			psw &= ~V_BIT;
			psw &= ~C_BIT;
			return;
		}
		if ((shift & 040) == 040) {			// right shift
			count = 0100 - (shift & 077);
			int sign = temp & 0x80000000;
			while (count-- > 0) {
				if ((temp & 1) != 0) {
					psw |= C_BIT;
				} else {
					psw &= ~C_BIT;
				}
				temp >>>= 1;
				temp += sign;
			}
			psw &= ~V_BIT;
		} else {							// left shift
			count = shift & 037;
			psw &= ~V_BIT;
			while (count-- > 0) {
				if ((temp & 0x80000000) != 0) {
					psw |= C_BIT;
				} else {
					psw &= ~C_BIT;
				}
				temp <<= 1;
				if ((temp & 0x80000000) != (old & 0x80000000)) {
					psw |= V_BIT;
				}
			}
		}
		if (temp < 0) {
			psw |= N_BIT;
		} else {
			psw &= ~N_BIT;
		}
		if (temp == 0) {
			psw |= Z_BIT;
		} else {
			psw &= ~Z_BIT;
		}
		regs[sourceReg] = (short)(temp >> 16);
		regs[sourceReg|1] = (short)(temp & LNI);
	}

	private final void executeXOR() throws Trap {
		int data2 = regs[(ir & 0700) >> 6];
		int data1 = loadDest();
		data2 = data2 ^ data1;
		if ((data2 & LNI) == 0) {
			psw |= Z_BIT;
		} else {
			psw &= ~Z_BIT;
		}
		if ((data2 & MNI) == 0) {
			psw &= ~N_BIT;
		} else {
			psw |= N_BIT;
		}
		psw &= ~V_BIT;
		storeDest2((short) data2);
	}

	private final void executeSOB() throws Trap {
		int sourceReg = (ir & 0700) >> 6;
		regs[sourceReg] -= 1;
		if (regs[sourceReg] != 0) {
			regs[PC] -= (ir & 077) * 2;
		}
	}

	private final void executeBPLorBMI() throws Trap {
		if (((psw >> 3) & 1) == ((ir >> 8) & 1)) {
			executeBR();
		}
	}

	private final void executeBHIorBLOS() throws Trap {
		int temp;
		temp = ((psw >> 2) & 1) | (psw & 1);
		if (temp == ((ir >> 8) & 1)) {
			executeBR();
		}
	}

	private final void executeBVCorBVS() throws Trap {
		if (((psw >> 1) & 1) == ((ir >> 8) & 1)) {
			executeBR();
		}
	}

	private final void executeBCCorBCS() throws Trap {
		if ((psw & 1) == ((ir >> 8) & 1)) {
			executeBR();
		}
	}

	private final void executeEMT() throws Trap {
		throw new Trap(Trap.EmulatorTrap);
	}

	private final void executeTRAP() throws Trap {
		throw new Trap(Trap.TrapInstruction);
	}

	private final void executeCLRB() throws Trap {
		psw &= ~C_BIT;
		psw &= ~V_BIT;
		psw |= Z_BIT;
		psw &= ~N_BIT;
		storeDestByte((byte) 0);
	}

	private final void executeCOMB() throws Trap {
		int data = ((int) loadDestByte()) & LNB;
		data = ~data & LNB;
		if ((data & LNB) == 0) {
			psw |= Z_BIT;
		} else {
			psw &= ~Z_BIT;
		}
		if ((data & MNB) == 0) {
			psw &= ~N_BIT;
		} else {
			psw |= N_BIT;
		}
		psw &= ~V_BIT;
		psw |= C_BIT;
		storeDestByte2((byte) data);
	}

	private final void executeINCB() throws Trap {
		int data = ((int) loadDestByte()) & LNB;
		if (data == MPB) {
			psw |= V_BIT;
		} else {
			psw &= ~V_BIT;
		}
		++data;
		if ((data & LNB) == 0) {
			psw |= Z_BIT;
		} else {
			psw &= ~Z_BIT;
		}
		if ((data & MNB) == 0) {
			psw &= ~N_BIT;
		} else {
			psw |= N_BIT;
		}
		storeDestByte2((byte) data);
	}

	private final void executeDECB() throws Trap {
		int data = ((int) loadDestByte()) & LNB;
		if (data == MNB) {
			psw |= V_BIT;
		} else {
			psw &= ~V_BIT;
		}
		--data;
		if ((data & LNB) == 0) {
			psw |= Z_BIT;
		} else {
			psw &= ~Z_BIT;
		}
		if ((data & MNB) == 0) {
			psw &= ~N_BIT;
		} else {
			psw |= N_BIT;
		}
		storeDestByte2((byte) data);
	}

	private final void executeNEGB() throws Trap {
		int data = ((int) loadDestByte()) & LNB;
		data = (LNB - data) + 1;
		if ((data & LNB) == 0) {
			psw |= Z_BIT;
		} else {
			psw &= ~Z_BIT;
		}
		if ((data & MNB) == 0) {
			psw &= ~N_BIT;
		} else {
			psw |= N_BIT;
		}
		if ((data & LNB) == MNB) {
			psw |= V_BIT;
		} else {
			psw &= ~V_BIT;
		}
		if ((data & LNB) == 0) {
			psw &= ~C_BIT;
		} else {
			psw |= C_BIT;
		}
		storeDestByte2((byte) data);
	}

	private final void executeADCB() throws Trap {
		int data = ((int) loadDestByte()) & LNB;
		if ((psw & C_BIT) != 0) {
			if (data == MPB) {
				psw |= V_BIT;
			} else {
				psw &= ~V_BIT;
			}
			if (data == LNB) {
				psw |= C_BIT;
			} else {
				psw &= ~C_BIT;
			}
			data++;
		} else {
			psw &= ~V_BIT;
			psw &= ~C_BIT;
		}
		if ((data & LNB) == 0) {
			psw |= Z_BIT;
		} else {
			psw &= ~Z_BIT;
		}
		if ((data & MNB) == 0) {
			psw &= ~N_BIT;
		} else {
			psw |= N_BIT;
		}
		storeDestByte2((byte) data);
	}

	private final void executeSBCB() throws Trap {
		int data = ((int) loadDestByte()) & LNB;
		if (data == MNB) {
			psw |= V_BIT;
		} else {
			psw &= ~V_BIT;
		}
		if ((psw & C_BIT) != 0) {
			if ((data & LNB) == 0) {
				psw |= C_BIT;
			} else {
				psw &= ~C_BIT;
			}
			--data;
		} else {
			psw &= ~C_BIT;
		}
		if ((data & LNB) == 0) {
			psw |= Z_BIT;
		} else {
			psw &= ~Z_BIT;
		}
		if ((data & MNB) == 0) {
			psw &= ~N_BIT;
		} else {
			psw |= N_BIT;
		}
		storeDestByte2((byte) data);
	}

	private final void executeTSTB() throws Trap {
		int data = ((int) loadDestByte()) & LNB;
		if ((data & LNB) == 0) {
			psw |= Z_BIT;
		} else {
			psw &= ~Z_BIT;
		}
		if ((data & MNB) == 0) {
			psw &= ~N_BIT;
		} else {
			psw |= N_BIT;
		}
		psw &= ~V_BIT;
		psw &= ~C_BIT;
	}

	private final void executeRORB() throws Trap {
		int data = ((int) loadDestByte()) & LNB;
		int temp = data & 1;
		data >>>= 1;
		if ((psw & C_BIT) != 0) {
			data += MNB;
		}
		if (temp != 0) {
			psw |= C_BIT;
		} else {
			psw &= ~C_BIT;
		}
		if ((data & LNB) == 0) {
			psw |= Z_BIT;
		} else {
			psw &= ~Z_BIT;
		}
		if ((data & MNB) == 0) {
			psw &= ~N_BIT;
		} else {
			psw |= N_BIT;
		}
		if ((((psw >> 3) & 1) ^ (psw & 1)) == 0) {
			psw &= ~V_BIT;
		} else {
			psw |= V_BIT;
		}
		storeDestByte2((byte) data);
	}

	private final void executeROLB() throws Trap {
		int data = ((int) loadDestByte()) & LNB;
		int temp = data & MNB;
		data <<= 1;
		if ((psw & C_BIT) != 0) {
			++data;
		}
		if (temp != 0) {
			psw |= C_BIT;
		} else {
			psw &= ~C_BIT;
		}
		if ((data & LNB) == 0) {
			psw |= Z_BIT;
		} else {
			psw &= ~Z_BIT;
		}
		if ((data & MNB) == 0) {
			psw &= ~N_BIT;
		} else {
			psw |= N_BIT;
		}
		if ((((psw >> 3) & 1) ^ (psw & 1)) == 0) {
			psw &= ~V_BIT;
		} else {
			psw |= V_BIT;
		}
		storeDestByte2((byte) data);
	}

	private final void executeASRB() throws Trap {
		int data = ((int) loadDestByte()) & LNB;
		if ((data & 1) != 0) {
			psw |= C_BIT;
		} else {
			psw &= ~C_BIT;
		}
		data = (data >>> 1) + (data & MNB);
		if ((data & LNB) == 0) {
			psw |= Z_BIT;
		} else {
			psw &= ~Z_BIT;
		}
		if ((data & MNB) == 0) {
			psw &= ~N_BIT;
		} else {
			psw |= N_BIT;
		}
		if ((((psw >> 3) & 1) ^ (psw & 1)) == 0) {
			psw &= ~V_BIT;
		} else {
			psw |= V_BIT;
		}
		storeDestByte2((byte) data);
	}

	private final void executeASLB() throws Trap {
		int data = ((int) loadDestByte()) & LNB;
		if ((data & MNB) != 0) {
			psw |= C_BIT;
		} else {
			psw &= ~C_BIT;
		}
		data <<= 1;
		if ((data & LNB) == 0) {
			psw |= Z_BIT;
		} else {
			psw &= ~Z_BIT;
		}
		if ((data & MNB) == 0) {
			psw &= ~N_BIT;
		} else {
			psw |= N_BIT;
		}
		if ((((psw >> 3) & 1) ^ (psw & 1)) == 0) {
			psw &= ~V_BIT;
		} else {
			psw |= V_BIT;
		}
		storeDestByte2((byte) data);
	}

	private final void executeMTPS() throws Trap {
		// 11/44 does not have this instruction
		// 11/34 should mmu trap if PS not mapped in user mode
		int data = loadDestByte() & LNB;
		if (isKernel()) {
			psw &= ~(0357);
			psw |= (data & 0357);
		} else {
			psw &= ~(0017);
			psw |= (data & 0017);
		}
	}

	private final void executeMFPS() throws Trap {
		// 11/44 does not have this instruction
		// 11/34 should mmu trap if PS not mapped in user mode
		int data = psw & LNB;
		if ((data & LNB) == 0) {
			psw |= Z_BIT;
		} else {
			psw &= ~Z_BIT;
		}
		if ((data & MNB) == 0) {
			psw &= ~N_BIT;
		} else {
			psw |= N_BIT;
		}
		psw &= ~V_BIT;
		storeDestByteExt((byte) data);
	}

	private final void executeMOVB() throws Trap {
		int data = ((int) loadSourceByte()) & LNB;
		if ((data & LNB) == 0) {
			psw |= Z_BIT;
		} else {
			psw &= ~Z_BIT;
		}
		if ((data & MNB) == 0) {
			psw &= ~N_BIT;
		} else {
			psw |= N_BIT;
		}
		psw &= ~V_BIT;
		storeDestByteExt((byte) data);
	}

	private final void executeCMPB() throws Trap {
		int data1 = ((int) loadSourceByte()) & LNB;
		int data2 = ((int) loadDestByte()) & LNB;
		int data3 = ~data2 & LNB;
		data3 = data1 + data3 + 1;
		if ((data3 & LNB) == 0) {
			psw |= Z_BIT;
		} else {
			psw &= ~Z_BIT;
		}
		if ((data3 & MNB) == 0) {
			psw &= ~N_BIT;
		} else {
			psw |= N_BIT;
		}
		if (((data1 & MNB) != (data2 & MNB)) && ((data2 & MNB) == (data3 & MNB))) {
			psw |= V_BIT;
		} else {
			psw &= ~V_BIT;
		}
		if ((data3 & CARRYB) == 0) {
			psw |= C_BIT;
		} else {
			psw &= ~C_BIT;
		}
	}

	private final void executeBITB() throws Trap {
		int data1 = ((int) loadSourceByte()) & LNB;
		int data2 = ((int) loadDestByte()) & LNB;
		data2 = data1 & data2;
		if ((data2 & LNB) == 0) {
			psw |= Z_BIT;
		} else {
			psw &= ~Z_BIT;
		}
		if ((data2 & MNB) == 0) {
			psw &= ~N_BIT;
		} else {
			psw |= N_BIT;
		}
		psw &= ~V_BIT;
	}

	private final void executeBICB() throws Trap {
		int data1 = ((int) loadSourceByte()) & LNB;
		int data2 = ((int) loadDestByte()) & LNB;
		data2 = (~data1 & LNB) & data2;
		if ((data2 & LNB) == 0) {
			psw |= Z_BIT;
		} else {
			psw &= ~Z_BIT;
		}
		if ((data2 & MNB) == 0) {
			psw &= ~N_BIT;
		} else {
			psw |= N_BIT;
		}
		psw &= ~V_BIT;
		storeDestByte2((byte) data2);
	}

	private final void executeBISB() throws Trap {
		int data1 = ((int) loadSourceByte()) & LNB;
		int data2 = ((int) loadDestByte()) & LNB;
		data2 = data1 | data2;
		if ((data2 & LNB) == 0) {
			psw |= Z_BIT;
		} else {
			psw &= ~Z_BIT;
		}
		if ((data2 & MNB) == 0) {
			psw &= ~N_BIT;
		} else {
			psw |= N_BIT;
		}
		psw &= ~V_BIT;
		storeDestByte2((byte) data2);
	}

	private final void executeSUB() throws Trap {
		int data1 = ((int) loadSource()) & LNI;
		int data2 = ((int) loadDest()) & LNI;
		int data3 = ~data1 & LNI;
		data3 = data2 + data3 + 1;
		if ((data3 & LNI) == 0) {
			psw |= Z_BIT;
		} else {
			psw &= ~Z_BIT;
		}
		if ((data3 & MNI) == 0) {
			psw &= ~N_BIT;
		} else {
			psw |= N_BIT;
		}
		if (((data1 & MNI) != (data2 & MNI)) && ((data1 & MNI) == (data3 & MNI))) {
			psw |= V_BIT;
		} else {
			psw &= ~V_BIT;
		}
		if ((data3 & CARRY) == 0) {
			psw |= C_BIT;
		} else {
			psw &= ~C_BIT;
		}
		storeDest2((short) data3);
	}

	private final void executeFIS() throws Trap {
		throw new Trap(Trap.ReservedInstruction);		// No FP11
	}

	//
	// Instruction Decoding
	//

	private final void decodeAndExecute() throws Trap {
		switch (ir >> 12) {
		case 0:
			switch ((ir >> 9) & 07) {
			case 0:
				switch ((ir >> 6) & 07) {
				case 0:
					switch (ir) {
					case 0: executeHALT(); return;			// 000000
					case 1: executeWAIT(); return;			// 000001
					case 2: executeRTIorRTT(); return;		// 000002
					case 3: executeBPT(); return;			// 000003
					case 4: executeIOT(); return;			// 000004
					case 5: executeRESET(); return;			// 000005
					case 6: executeRTIorRTT(); return;		// 000006
					}
															// 000007 - 000077
					throw new Trap(Trap.ReservedInstruction);
				case 1: executeJMP(); return;				// 000100 - 000177
				case 2:
					switch ((ir >> 3) & 07) {
					case 0: executeRTS(); return;			// 000200 - 000207
					case 1: // fall through
					case 2: // fall through
					case 3: 								// 000210 - 000237
						throw new Trap(Trap.ReservedInstruction);
					case 4: // fall through
					case 5: // fall through
					case 6: // fall through
					case 7: executeCC(); return;			// 000240 - 000277
					}
				case 3: executeSWAB(); return;				// 000300 - 000377
				case 4: // fall through
				case 5: // fall through
				case 6: // fall through
				case 7: executeBR(); return;				// 000400 - 000777
				}
			case 1: executeBNEorBEQ(); return;				// 001000 - 001777
			case 2: executeBGEorBLT(); return;				// 002000 - 002777
			case 3: executeBGTorBLE(); return;				// 003000 - 003777
			case 4: executeJSR(); return;					// 004000 - 004777
			case 5:
				switch ((ir >> 6) & 07) {
				case 0: executeCLR(); return;				// 005000 - 005077
				case 1: executeCOM(); return;				// 005100 - 005177
				case 2: executeINC(); return;				// 005200 - 005277
				case 3: executeDEC(); return;				// 005300 - 005377
				case 4: executeNEG(); return;				// 005400 - 005477
				case 5: executeADC(); return;				// 005500 - 005577
				case 6: executeSBC(); return;				// 005600 - 005677
				case 7: executeTST(); return;				// 005700 - 005777
				}
			case 6:
				switch ((ir >> 6) & 07) {
				case 0: executeROR(); return;				// 006000 - 006077
				case 1: executeROL(); return;				// 006100 - 006177
				case 2: executeASR(); return;				// 006200 - 006277
				case 3: executeASL(); return;				// 006300 - 006377
				case 4: executeMARK(); return;				// 006400 - 006477
				case 5: executeMFPI(); return;				// 006500 - 006577
				case 6: executeMTPI(); return;				// 006600 - 006677
				case 7: executeSXT(); return;				// 006700 - 006777
				}
			case 7:
				throw new Trap(Trap.ReservedInstruction);	// 007000 - 007777
			}
		case 1: executeMOV(); return;						// 010000 - 017777
		case 2: executeCMP(); return;						// 020000 - 027777
		case 3: executeBIT(); return;						// 030000 - 037777
		case 4: executeBIC(); return;						// 040000 - 047777
		case 5: executeBIS(); return;						// 050000 - 057777
		case 6: executeADD(); return;						// 060000 - 067777
		case 7:
			switch ((ir >> 9) & 07) {
			case 0: executeMUL(); return;					// 070000 - 070777
			case 1: executeDIV(); return;					// 071000 - 071777
			case 2: executeASH(); return;					// 072000 - 072777
			case 3: executeASHC(); return;					// 073000 - 073777
			case 4: executeXOR(); return;					// 074000 - 074777
			case 5:
				throw new Trap(Trap.ReservedInstruction);	// 075000 - 075777
			case 6:
				throw new Trap(Trap.ReservedInstruction);	// 076000 - 076777
			case 7: executeSOB(); return;					// 077000 - 077777
			}
		case 010:
			switch ((ir >> 9) & 07) {
			case 0: executeBPLorBMI(); return;				// 100000 - 100777
			case 1: executeBHIorBLOS(); return;				// 101000 - 101777
			case 2: executeBVCorBVS(); return;				// 102000 - 102777
			case 3: executeBCCorBCS(); return;				// 103000 - 103777
			case 4:
				if (ir <= 0104377) {
					executeEMT(); return;					// 104000 - 104377
				} else {
					executeTRAP(); return;					// 104400 - 104777
				}
			case 5:
				switch ((ir >> 6) & 07) {
				case 0: executeCLRB(); return;				// 105000 - 105077
				case 1: executeCOMB(); return;				// 105100 - 105177
				case 2: executeINCB(); return;				// 105200 - 105277
				case 3: executeDECB(); return;				// 105300 - 105377
				case 4: executeNEGB(); return;				// 105400 - 105477
				case 5: executeADCB(); return;				// 105500 - 105577
				case 6: executeSBCB(); return;				// 105600 - 105677
				case 7: executeTSTB(); return;				// 105700 - 105777
				}
			case 6:
				switch ((ir >> 6) & 07) {
				case 0: executeRORB(); return;				// 106000 - 106077
				case 1: executeROLB(); return;				// 106100 - 106177
				case 2: executeASRB(); return;				// 106200 - 106277
				case 3: executeASLB(); return;				// 106300 - 106377
				case 4: executeMTPS(); return;				// 106400 - 106477
				case 5: executeMFPI(); return;				// 106500 - 106577
				case 6: executeMTPI(); return;				// 106600 - 106677
				case 7: executeMFPS(); return;				// 106700 - 106777
				}
			case 7:
				throw new Trap(Trap.ReservedInstruction);	// 107000 - 107777
			}
		case 011: executeMOVB(); return;					// 110000 - 117777
		case 012: executeCMPB(); return;					// 120000 - 127777
		case 013: executeBITB(); return;					// 130000 - 137777
		case 014: executeBICB(); return;					// 140000 - 147777
		case 015: executeBISB(); return;					// 150000 - 157777
		case 016: executeSUB(); return;						// 160000 - 167777
		case 017: executeFIS(); return;						// 170000 - 177777
		}
		System.out.println("decode fell through");
		throw new Trap(Trap.ReservedInstruction);			// fell through
	}

}

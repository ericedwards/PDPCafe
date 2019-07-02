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
// CommandLine.java - Command line user-interface.
//

package PDPCafe;

import java.io.*;

public class CommandLine extends Thread {

	private static final int NUMBER_EMPTY = -1;
	private static final int NUMBER_SYNTAX = -2;

	private CPUDevice cpu;
	private KTDevice mmu;
	private Unibus unibus;
	private BufferedReader input;
	private PrintStream output;
	private String cmdLine;
	private int cmdOffset;
	private int savedAddress = 0;

	//
	// CommandLine() - Constructor, just start the thread.
	//

	public CommandLine() {
		this.start();
	}

	//
	// grok() - Unrecognized command, print the error message.
	//

	private void grok() {
		output.println("Can't Grok.");
	}

	//
	// eatWhitespace()
	//

	private void eatWhitespace() {
		while ((cmdOffset < cmdLine.length()) &&
		Character.isWhitespace(cmdLine.charAt(cmdOffset))) {
			++cmdOffset;
		}
	}

	//
	// nextToken()
	//

	private String nextToken() {
		int start;
		eatWhitespace();
		start = cmdOffset;
		while ((cmdOffset < cmdLine.length()) &&
		!(Character.isWhitespace(cmdLine.charAt(cmdOffset)))) {
			++cmdOffset;
		}
		if (start == cmdOffset) {
			return null;
		} else {
			return cmdLine.substring(start, cmdOffset);
		}
	}

	//
	// nextOctal16()
	//

	private int nextOctal16() {
		int i;
		String s = nextToken();
		if (s == null) {
			return NUMBER_EMPTY;
		}
		try {
			i = Integer.parseInt(s, 8);
		} catch (NumberFormatException e) {
			return NUMBER_SYNTAX;
		}
		if ((i < 0) || (i > 0177777)) {
			return NUMBER_SYNTAX;
		}
		return i;
	}

	//
	// nextOctal18()
	//

	private int nextOctal18() {
		int i;
		String s = nextToken();
		if (s == null) {
			return NUMBER_EMPTY;
		}
		try {
			i = Integer.parseInt(s, 8);
		} catch (NumberFormatException e) {
			return NUMBER_SYNTAX;
		}
		if ((i < 0) || (i > 0777777)) {
			return NUMBER_SYNTAX;
		}
		return i;
	}

	//
	// dumpOctal16()
	//

	private void dumpOctal16(int value) {
		int v = value & 0177777;
		String s = Integer.toOctalString(v);
		String t = new String("");
		for (int i = s.length(); i < 6; ++i) {
			t = t + "0";
		}
		t = t + s;
		output.print(t + " ");
	}

	//
	// dumpOctal18()
	//

	private void dumpOctal18(int value) {
		int v = value & 0777777;
		String s = Integer.toOctalString(v);
		String t = new String("");
		for (int i = s.length(); i < 6; ++i) {
			t = t + "0";
		}
		t = t + s;
		output.print(t + " ");
	}

	//
	// dumpPSW()
	//

	private void dumpPSW(int pswValue) {
		int v = pswValue & 0177777;
		dumpOctal16(v);
		System.out.print("[");
		switch ((v >>> 14) & 3) {
		case 0:
			System.out.print("CM=K ");
			break;
		case 1:
		case 2:
			System.out.print("CM=invalid ");
			break;
		case 3:
			System.out.print("CM=U ");
			break;
		}
		switch ((v >>> 12) & 3) {
		case 0:
			System.out.print("PM=K ");
			break;
		case 1:
		case 2:
			System.out.print("PM=invalid ");
			break;
		case 3:
			System.out.print("PM=U ");
			break;
		}
		System.out.print("PR=" + ((v >>> 5) & 7) + " ");
		System.out.print("CC=");
		if ((v & 020) != 0) {
			System.out.print("T");
		}
		if ((v & 010) != 0) {
			System.out.print("N");
		}
		if ((v & 04) != 0) {
			System.out.print("Z");
		}
		if ((v & 02) != 0) {
			System.out.print("V");
		}
		if ((v & 01) != 0) {
			System.out.print("C");
		}
		System.out.print("] ");
	}

	//
	// cpuDumpCmd()
	//

	private void cpuDumpCmd() {
		if (cpu.isExecuting()) {
			System.out.print("CPU=running ");
		} else {
			System.out.print("CPU=stopped ");
		}
		System.out.print("PSW=");
		dumpPSW(cpu.psw);
		System.out.println("");
		for (int i = 0; i < 8; ++i) {
			System.out.print("R" + i + "=");
			dumpOctal16(cpu.regs[i]);
			if (i == 3) {
				System.out.println("");
			}
		}
		System.out.println("");
		System.out.print("MMR0=");
		dumpOctal16(mmu.mmr0);
		System.out.print("MMR2=");
		dumpOctal16(mmu.mmr2);
		System.out.println("");
	}

	//
	// bootCmd()
	//

	private void bootCmd() {
		int addr = nextOctal16();
		if (addr == NUMBER_EMPTY) {
			addr = 0173000;
		} else if (addr == NUMBER_SYNTAX) {
			grok();
			return;
		}
		if (!cpu.isExecuting()) {
			cpu.regs[7] = (short) addr;
			cpu.psw = 0340;
			if (cpu.startExecution(false)) {
				return;
			}
		}
		output.println("** boot failed **");
	}

	//
	// memoryDumpCmd()
	//

	private void memoryDumpCmd() {
		int start = nextOctal18();
		if (start == NUMBER_SYNTAX) {
			grok();
			return;
		}
		if (start == NUMBER_EMPTY) {
			start = savedAddress;
		}
		start &= 0777776;				// full words only
		int end = nextOctal18();
		if (end == NUMBER_SYNTAX) {
			grok();
			return;
		}
		if (end == NUMBER_EMPTY) {
			end = start + 14;			// default to sixteen bytes
			if (end > 07777776) {
				end = 0777776;			// stop dump at end of memory
			}
		}
		end &= 0777776;					// full words only
		if (end < start) {
			grok();
			return;
		}
		savedAddress = end + 2;			// next dump starts here
		if (savedAddress > 07777776) {
			savedAddress = 0;			// wrap back to zero
		}
		int y, data;
		for (y = 0; start <= end; start += 2) {
			if (y == 0) {
				dumpOctal18(start);
			}
			try {
				data = unibus.read(start);
				dumpOctal16(data);
			} catch (Trap t) {
				output.print("XXXXXX ");
			}
			if (++y == 8) {
					y = 0;
					output.println();
			}
		}
		if (y > 0) {
				output.println();
		}
	}

	//
	// goCmd()
	//

	private void goCmd() {
		int addr = nextOctal16();
		if (addr == NUMBER_SYNTAX) {
			grok();
			return;
		}
		if (!cpu.isExecuting()) {
			if (addr != NUMBER_EMPTY)
				cpu.regs[7] = (short) addr;
			if (cpu.startExecution(false)) {
				return;
			}
		}
		output.println("** go failed **");
	}

	//
	//
	//

	private void haltCmd() {
		if (cpu.isExecuting() && cpu.stopExecution()) {
			return;
		}
		output.println("** halt failed **");
	}

	//
	//
	//

	private void stepCmd() {
		int addr = nextOctal16();
		if (addr == NUMBER_SYNTAX) {
			grok();
			return;
		}
		if (!cpu.isExecuting()) {
			if (addr != NUMBER_EMPTY)
				cpu.regs[7] = (short) addr;
			if (cpu.startExecution(true)) {
				return;
			}
		}
		output.println("** step failed **");
	}

	//
	//
	//

	private void unibusResetCmd() {
		if (!cpu.isExecuting()) {
			unibus.reset();
			return;
		}
		output.println("** reset failed **");
		
	}

	//
	//
	//

	private void statusCmd() {
		unibus.dumpDevices();
	}

	//
	// helpCmd()
	//

	private void helpCmd() {
		output.println("?                              print this help");
	//	output.println("a <device> <unit> [options]    assign device");
		output.println("b [addr]                       boot");
		output.println("c                              processor dump");
		output.println("d [addr] [addr]                memory dump");
		output.println("g [addr]                       go");
		output.println("h                              halt");
	//	output.println("l <filename>                   load config");
	//	output.println("m [addr]                       memory modify");
	//	output.println("n <device> [options]           new device");
	//	output.println("o <device> <options>           change device options");
		output.println("q                              quit");
	//	output.println("r                              register modify");
		output.println("s                              step");
	//	output.println("w <filename>                   write config");
		output.println("x                              unibus reset");
		output.println("z                              status");
	}

	//
	// run() - Mainline of command processing.
	//

	public void run() {

		boolean done = false;

		// get private references to important stuff

		cpu = CPUDevice.instance();
		mmu = KTDevice.instance();
		unibus = Unibus.instance();
		input = new BufferedReader(new InputStreamReader(System.in));
		output = System.out;

		// main loop, get a command a parse it

		while (!done) {

			String token;

			output.print("-> ");
			output.flush();
			try {
				cmdLine = input.readLine();
			} catch (IOException e) {
				// input failure, end command line processing
				break;
			}
			cmdOffset = 0;
			if ((token = nextToken()) == null)
				continue;
			if (token.length() > 1) {
				grok();
				continue;
			}
			switch (token.charAt(0)) {
			case '?':
				helpCmd();
				break;
			case 'b':
				bootCmd();
				break;
			case 'c':
				cpuDumpCmd();
				break;
			case 'd':
				memoryDumpCmd();
				break;
			case 'g':
				goCmd();
				break;
			case 'h':
				haltCmd();
				break;
			case 'q':
				done = true;
				break;
			case 's':
				stepCmd();
				break;
			case 'x':
				unibusResetCmd();
				break;
			case 'z':
				statusCmd();
				break;
			default:
				grok();
				break;
			}

		}
	}
}

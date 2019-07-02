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
// Trap.java - Exception class.
//

package PDPCafe;

public class Trap extends Exception {

	public static final int UnibusTimeout = 04;
	public static final int IllegalInstruction = 04;
	public static final int OddAddress = 04;
	public static final int StackLimit = 04;
	public static final int ReservedInstruction = 010;
	public static final int BreakpointTrap = 014;
	public static final int IOTrap = 020;
	public static final int PowerFailTrap = 024;
	public static final int EmulatorTrap = 030;
	public static final int TrapInstruction = 034;
	public static final int SegmentationError = 0250;
	public static final int Unimplemented = -1;
	public static final int HaltInstruction = -2;
	public static final int WaitInstruction = -3;
	public static final int RTTInstruction = -4;

	public int vector;

	public Trap(int vector) {
		this.vector = vector;
	}
} 

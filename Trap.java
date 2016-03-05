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

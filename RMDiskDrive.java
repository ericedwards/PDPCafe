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
// RMDiskDrive - Class to store per drive information.
//

package PDPCafe;

import java.io.*;

public class RMDiskDrive {
	public int exists;				// does drive exist (type)
	public int cylinders;			// number of cylinders
	public int heads;				// number of heads
	public int sectors;				// number of sectors
	public int rmcs1;				// control and status #1
	public int rmda;				// desired address (track & sector)
	public int rmds;				// drive status
	public int rmer1;				// error #1
	public int rmla;				// look ahead
	public int rmmr1;				// maintenance #1
	public int rmdt;				// drive type
	public int rmsn;				// serial number
	public int rmof;				// offset
	public int rmdc;				// desired cylinder
	public int rmhr;				// holding register
	public int rmmr2;				// maintenance #2
	public int rmer2;				// error #2
	public int rmec1;				// ecc #1
	public int rmec2;				// ecc #2
	public RandomAccessFile file;	// where the data lives
}

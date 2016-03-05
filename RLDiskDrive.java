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
// RLDiskDrive - Per drive information.
//

package PDPCafe;

import java.io.*;

public class RLDiskDrive {
	public int exists;						// does drive exist (type)
	public int cylinder;					// current cylinder
	public int head;						// current head
	public boolean error;					// drive in error
	public RandomAccessFile file;			// where the data lives
}

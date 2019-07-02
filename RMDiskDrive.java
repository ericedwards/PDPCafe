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

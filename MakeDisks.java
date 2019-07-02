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
// MakeDisks.java - 
//

package PDPCafe;

import java.io.*;

public class MakeDisks {

  private static final int RL_CYL_RL01 = 256;
  private static final int RL_CYL_RL02 = 512;
  private static final int RL_NUM_SECT = 40;
  private static final int RL_NUM_HEADS = 2;
  private static final int RL_WORDS_SECTOR = 128;
  private static final int RL_BYTES_SECTOR = (RL_WORDS_SECTOR * 2);
  private static final int RL_BYTES_TRACK = (RL_BYTES_SECTOR * RL_NUM_SECT);
  private static final int RL_BYTES_CYL = (RL_BYTES_TRACK * RL_NUM_HEADS);
  private static final int RL_SIZE_RL01 = (RL_BYTES_CYL * RL_CYL_RL01);
  private static final int RL_SIZE_RL02 = (RL_BYTES_CYL * RL_CYL_RL02);

  public static void makeDisk(String path, boolean big) throws IOException {
    int size;
    if (big)
      size = (RL_SIZE_RL02/RL_BYTES_SECTOR);
    else
      size = (RL_SIZE_RL01/RL_BYTES_SECTOR);
    FileOutputStream f = new FileOutputStream(path, false);
    byte[] b = new byte[256];
    for (int i = 0; i < size; ++i) {
      f.write(b);
    }
    f.close();
  }

  public static void main(String args[]) {
    try {
      makeDisk("/Users/ericedwa/Pdp11/unix0.rl02", true);
      makeDisk("/Users/ericedwa/Pdp11/unix1.rl02", true);
      makeDisk("/Users/ericedwa/Pdp11/unix2.rl02", true);
      makeDisk("/Users/ericedwa/Pdp11/unix3.rl02", true);
    } catch(IOException e) {
      System.out.println("Error Occurred");
    }
  }
}

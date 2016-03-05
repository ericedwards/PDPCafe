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

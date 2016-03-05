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
// TapeConvert.java - Convert tapes to internal format.
//

// package PDPCafe;

import java.io.*;

public class TapeConvert {

  private FileOutputStream outputFile;

  private byte[] encodeRecordSize(int recordSize) {
    byte[] encodedSize = new byte[4];
    encodedSize[3] = (byte) ((recordSize >> 24) & 0xff);
    encodedSize[2] = (byte) ((recordSize >> 16) & 0xff);
    encodedSize[1] = (byte) ((recordSize >> 8) & 0xff);
    encodedSize[0] = (byte) (recordSize & 0xff);
    return encodedSize;
  }

  public TapeConvert(String outputPath) throws IOException {
    outputFile = new FileOutputStream(outputPath);
  }

  public void addImage(String inputPath, int recordSize) throws IOException {
    int bytesRead = 0, lastBytesRead = 0, records = 0;
    byte[] buffer = new byte[recordSize];
    FileInputStream inputFile = new FileInputStream(inputPath);
    while ((bytesRead = inputFile.read(buffer)) > 0) {
      ++records;
      outputFile.write(encodeRecordSize(bytesRead));
      outputFile.write(buffer, 0, bytesRead);
      outputFile.write(encodeRecordSize(bytesRead));
      lastBytesRead = bytesRead;
    }
    outputFile.write(encodeRecordSize(0));
    // SIMH outputFile.write(encodeRecordSize(0));
    inputFile.close();
    System.out.println("Added: " + inputPath + " " + records +
      " record(s) of " + recordSize + " bytes, last record was " +
      lastBytesRead);
  }

  public void close() throws IOException {
    outputFile.close();
  }

  public static void main(String args[]) {
    try {
      TapeConvert r = new TapeConvert("/Users/ericedwards/src/LURCH/29bsd.tape");
      r.addImage("/Users/ericedwards/tmp/ArchiveStuff/2.9BSD/file1", 512);
      r.addImage("/Users/ericedwards/tmp/ArchiveStuff/2.9BSD/file2", 1024);
      r.addImage("/Users/ericedwards/tmp/ArchiveStuff/2.9BSD/file3", 1024);
      r.addImage("/Users/ericedwards/tmp/ArchiveStuff/2.9BSD/file4", 1024);
      r.addImage("/Users/ericedwards/tmp/ArchiveStuff/2.9BSD/file5", 1024);
      r.addImage("/Users/ericedwards/tmp/ArchiveStuff/2.9BSD/file6", 1024);
      r.addImage("/Users/ericedwards/tmp/ArchiveStuff/2.9BSD/file7", 10240);
      r.addImage("/Users/ericedwards/tmp/ArchiveStuff/2.9BSD/usr.tar", 10240);
      r.close();
    } catch(IOException e) {
      System.out.println("Error Occurred");
    }
  }
}

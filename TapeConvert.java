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

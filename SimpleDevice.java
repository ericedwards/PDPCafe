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
// SimpleDevice - A simple example device for testing.
//

package PDPCafe;

public class SimpleDevice implements UnibusDevice {

  private static final int DEFAULT_BASE = 0760100;    // base address
  private static final int DEFAULT_SIZE = 8;          // number of registers

  private short storage[];
  private UnibusDeviceInfo info;
  private String options;

  public SimpleDevice() {
    this(DEFAULT_BASE, DEFAULT_SIZE, "");
  }

  public SimpleDevice(int base, int size, String options) {
    this.options = options;
    storage = new short[size];
    Unibus u = Unibus.instance();
    info = new UnibusDeviceInfo(this, base, size, "SIMPLE", false);
    u.registerDevice(info);
  }

  public void reset() {
    for (int i = 0; i < info.size; ++i)
      storage[i] = 0;
  }

  public short read(int addr) {
    return storage[(addr - info.base) >> 1];
  }

  public void write(int addr, short data) {
    storage[(addr - info.base) >> 1] = data;
  }

  public void writebyte(int addr, byte data) {
    if ((addr & 1) == 1) {
      storage[(addr - info.base) >> 1] &= 0177400;
      storage[(addr - info.base) >> 1] |= data << 8;
    } else {
      storage[(addr - info.base) >> 1] &= 0377;
      storage[(addr - info.base) >> 1] |= data;
    }
  }

  public void eventService(int data) {
      // do nothing
  }

  public void interruptService() {
      // do nothing
  }
} 

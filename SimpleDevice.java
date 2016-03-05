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

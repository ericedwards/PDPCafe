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
// UnibusDeviceInfo.java
//

package PDPCafe;

public class UnibusDeviceInfo {

	public UnibusDevice device;
	public int base;
	public int size;
	public String name;
	public boolean standard;

	public UnibusDeviceInfo(UnibusDevice device, int base, int size,
		String name, boolean standard) {
			this.device = device;
			this.base = base;
			this.size = size;
			this.name = name;
			this.standard = standard;
	}

} 

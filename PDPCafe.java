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
// PDPCafe.java - The main program.
//

package PDPCafe;

public class PDPCafe {
  public static void main(String argv[]) {
    Unibus unibus = Unibus.instance();
    KTDevice mmu = KTDevice.instance();
    CPUDevice cpu = CPUDevice.instance();
    KWDevice kw = KWDevice.instance();
    BootDevice bd = new BootDevice();
    RLDiskDevice rl = new RLDiskDevice();
    TMTapeDevice tm = new TMTapeDevice();
    KLConsoleDevice kl = new KLConsoleDevice();
    KLConsoleDevice kl2 = new KLConsoleDevice(0776500,4,"1");
    LPDevice lp = new LPDevice();
    RMDiskDevice rm = new RMDiskDevice();
    DZTerminalDevice dz = new DZTerminalDevice();
    try {

      //rl.assign(0, "/Users/ericedwards/etc/PDPCafe/xxdp25.rl02");

      rl.assign(0, "/Users/ericedwards/etc/PDPCafe/RL.0");
      rl.assign(1, "/Users/ericedwards/etc/PDPCafe/RL.1");
      rl.assign(2, "/Users/ericedwards/etc/PDPCafe/RL.2");
      rl.assign(3, "/Users/ericedwards/etc/PDPCafe/RL.3");

      rm.assign(0, "/Users/ericedwards/etc/PDPCafe/RM.0");

      //tm.assign("/Users/ericedwards/etc/PDPCafe/temp");
      tm.assign("/Users/ericedwards/etc/PDPCafe/29bsdtape");

      lp.assign("/Users/ericedwards/etc/PDPCafe/printer");

    } catch (java.io.IOException e) {
      System.out.println("Can't open file");
    }
    //System.out.println("Starting GUI:");
    //GUIMainFrame.startGUI();
    System.out.println("Starting command line:");
    CommandLine cm = new CommandLine();
    while(cm.isAlive()) {
      try {
        cm.join();
      } catch (Exception e) {
        // interrupted
      }
    }
    System.exit(0);
  }
}

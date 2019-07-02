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

PDPCafe
=======

This is my second PDP-11 emulator, in Java this time.
The development was done between 1999 and 2002.
The prospect of a cross-platform emulator appealed to me, so I ported my original C
code.  A disk image of XXDP became available on the web and I ironed out a lot
of the mistakes I had made in the original emulator.

There are started device drivers for the RM02/03/05 and DZ.
2.9BSD will run on the RM02 emulation, but DEC diagnostics will
surely fail.  I had wanted to start a Swing/AWT
console with VT emulation, but never got that far. 

I released the code with the GPL license, but it's now available with the
MIT License.

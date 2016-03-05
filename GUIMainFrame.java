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
// GUIMainFrame.java - Just an attempt to figure out a GUI framework.
//

package PDPCafe;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

public class GUIMainFrame extends Frame implements ActionListener {

	public GUIMainFrame() {
		super("PDPCafe");
		setSize(450, 250);
	}

	public void actionPerformed(ActionEvent ae) {
		System.out.println(ae.getActionCommand());
	}

	public static void startGUI() {
		GUIMainFrame mainFrame = new GUIMainFrame();
		mainFrame.setVisible(true);
	}
}

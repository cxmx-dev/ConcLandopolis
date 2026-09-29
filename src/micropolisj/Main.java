// This file is part of MicropolisJ.
// Copyright (C) 2013 Jason Long
// Portions Copyright (C) 1989-2007 Electronic Arts Inc.
//
// MicropolisJ is free software; you can redistribute it and/or modify
// it under the terms of the GNU GPLv3, with additional terms.
// See the README file, included in this distribution, for details.

package micropolisj;

import java.awt.Font;
import javax.swing.*;

import micropolisj.gui.MainWindow;

public class Main
{
	static void installUiDefaults()
	{
		try {
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		}
		catch (Exception e) {
			// keep the cross-platform LookAndFeel
		}

		// Bump tiny default UI fonts so menus/dialogs stay readable on modern displays.
		String[] fontKeys = {
			"Button.font", "ToggleButton.font", "RadioButton.font",
			"CheckBox.font", "ComboBox.font", "Label.font",
			"List.font", "MenuBar.font", "MenuItem.font", "Menu.font",
			"PopupMenu.font", "OptionPane.font", "Panel.font",
			"ScrollPane.font", "TextField.font", "TextArea.font",
			"ToolBar.font", "ToolTip.font", "TitledBorder.font",
			"CheckBoxMenuItem.font", "RadioButtonMenuItem.font",
			"TabbedPane.font", "Table.font", "TableHeader.font"
		};
		for (String key : fontKeys) {
			Font f = UIManager.getFont(key);
			if (f != null && f.getSize() < 13) {
				UIManager.put(key, f.deriveFont(13f));
			}
		}
	}

	static void createAndShowGUI()
	{
		MainWindow win = new MainWindow();
		win.setVisible(true);
		win.doNewCity(true);
	}

	public static void main(String [] args)
	{
		System.setProperty("awt.useSystemAAFontSettings", "on");
		System.setProperty("swing.aatext", "true");

		SwingUtilities.invokeLater(new Runnable() {
		public void run() {
			installUiDefaults();
			createAndShowGUI();
		}});
	}
}

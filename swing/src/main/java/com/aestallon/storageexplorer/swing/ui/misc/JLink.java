/*
 * Copyright (C) 2025 Szabolcs Bazil Papp
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along with this program.
 * If not, see <http://www.gnu.org/licenses/>.
 */

package com.aestallon.storageexplorer.swing.ui.misc;

import java.awt.*;
import java.io.IOException;
import java.net.URI;
import javax.swing.*;

public class JLink extends JButton {

  public JLink(final String text, final URI uri) {
    super("<HTML><FONT color=\"#000099\"><U>%s</U></FONT></HTML>".formatted(text));
    setBorderPainted(false);
    setOpaque(false);
    setFocusPainted(false);
    setBackground(getBackground());
    setContentAreaFilled(false);
    setBorder(null);
    setHorizontalAlignment(SwingConstants.LEFT);
    setToolTipText(uri.toString());
    addActionListener(e -> {
      try {
        Desktop.getDesktop().browse(uri);
      } catch (final IOException ex) {
        JOptionPane.showMessageDialog(this,
            "Cannot open link",
            "Could not open link at %s: %s".formatted(uri, ex.getMessage()),
            JOptionPane.ERROR_MESSAGE,
            IconProvider.ERROR);
      }
    });
    setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
  }

}

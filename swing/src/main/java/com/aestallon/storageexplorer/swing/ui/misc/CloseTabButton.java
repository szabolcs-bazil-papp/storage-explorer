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
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import javax.swing.*;

public class CloseTabButton extends JButton {
  
  private static final int SIZE = 17;
  
  public CloseTabButton(final ActionListener actionListener) {
    setPreferredSize(new Dimension(SIZE, SIZE));

    setToolTipText("Close this tab. Alt + Click to close all tabs but this.");
    setContentAreaFilled(false);
    setBorderPainted(false);

    addMouseListener(CLOSE_BUTTON_ROLLOVER_LISTENER);
    setRolloverEnabled(true);

    addActionListener(actionListener);
  }

  @Override
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);
    Graphics2D g2 = (Graphics2D) g.create();
    //shift the image for pressed buttons
    if (getModel().isPressed()) {
      g2.translate(1, 1);
    }
    g2.setStroke(new BasicStroke(2));
    g2.setColor(Color.BLACK);
    if (getModel().isRollover()) {
      g2.setColor(Color.MAGENTA);
    }
    int delta = 6;
    g2.drawLine(delta, delta, getWidth() - delta - 1, getHeight() - delta - 1);
    g2.drawLine(getWidth() - delta - 1, delta, delta, getHeight() - delta - 1);
    g2.dispose();
  }

  private static final MouseListener CLOSE_BUTTON_ROLLOVER_LISTENER = new MouseAdapter() {
    public void mouseEntered(MouseEvent e) {
      final var component = e.getComponent();
      if (component instanceof AbstractButton button) {
        button.setBorderPainted(true);
      }
    }

    public void mouseExited(MouseEvent e) {
      final var component = e.getComponent();
      if (component instanceof AbstractButton button) {
        button.setBorderPainted(false);
      }
    }
  };
}

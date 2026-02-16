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

package com.aestallon.storageexplorer.swing.ui.graph.uml;

import java.awt.*;
import java.awt.event.MouseEvent;
import prefuse.Display;
import prefuse.controls.ControlAdapter;
import prefuse.util.ui.UILib;
import prefuse.visual.VisualItem;

public class PanControl extends ControlAdapter {

  private boolean m_panOverItem;
  private boolean m_DownValid;
  private int m_xDown, m_yDown;
  private int m_button;

  public PanControl() {
    this(LEFT_MOUSE_BUTTON, false);
  }

  public PanControl(boolean panOverItem) {
    this(LEFT_MOUSE_BUTTON, panOverItem);
  }

  public PanControl(int mouseButton) {
    this(mouseButton, false);
  }

  public PanControl(int mouseButton, boolean panOverItem) {
    m_button = mouseButton;
    m_panOverItem = panOverItem;
  }

  // ------------------------------------------------------------------------

  @Override
  public void mousePressed(MouseEvent e) {
    if (UILib.isButtonPressed(e, m_button)) {
      e.getComponent().setCursor(
          Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
      m_xDown = e.getX();
      m_yDown = e.getY();
      m_DownValid = true;
    }
  }

  /**
   * @see java.awt.event.MouseMotionListener#mouseDragged(java.awt.event.MouseEvent)
   */
  @Override
  public void mouseDragged(MouseEvent e) {
    if (UILib.isButtonPressed(e, m_button)) {
      Display display = (Display) e.getComponent();
      int x = e.getX(), y = e.getY();
      int dx = x - m_xDown, dy = y - m_yDown;
      if (m_DownValid) {
        display.pan(dx, dy);
      }
      m_xDown = x;
      m_yDown = y;
      m_DownValid = true;
      display.repaint();
    }
  }

  /**
   * @see java.awt.event.MouseListener#mouseReleased(java.awt.event.MouseEvent)
   */
  @Override
  public void mouseReleased(MouseEvent e) {
    if (UILib.isButtonPressed(e, m_button)) {
      e.getComponent().setCursor(null); // e.getComponent().setCursor(Cursor.getDefaultCursor());
      m_xDown = -1;
      m_yDown = -1;
      m_DownValid = false;
    }
  }

  /**
   * @see prefuse.controls.Control#itemPressed(prefuse.visual.VisualItem, java.awt.event.MouseEvent)
   */
  @Override
  public void itemPressed(VisualItem item, MouseEvent e) {
    if (m_panOverItem)
      mousePressed(e);
  }

  /**
   * @see prefuse.controls.Control#itemDragged(prefuse.visual.VisualItem, java.awt.event.MouseEvent)
   */
  @Override
  public void itemDragged(VisualItem item, MouseEvent e) {
    if (m_panOverItem)
      mouseDragged(e);
  }

  /**
   * @see prefuse.controls.Control#itemReleased(prefuse.visual.VisualItem,
   *     java.awt.event.MouseEvent)
   */
  @Override
  public void itemReleased(VisualItem item, MouseEvent e) {
    if (m_panOverItem)
      mouseReleased(e);
  }

}

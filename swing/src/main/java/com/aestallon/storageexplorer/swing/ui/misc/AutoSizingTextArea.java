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
import javax.swing.*;


public final class AutoSizingTextArea extends JTextArea {

  public AutoSizingTextArea(final String text) {
    super(text);
    recalculate();
  }
  
  @Override
  public Dimension getMinimumSize() {
    return getPreferredSize();
  }

  @Override
  public Dimension getPreferredSize() {
    final FontMetrics fm = getFontMetrics(getFont());
    final int rows = getRows();
    return new Dimension(super.getPreferredSize().width,
        fm.getHeight() * rows + fm.getDescent());
  }

  @Override
  public void setText(String t) {
    super.setText(t);
    recalculate();
  }

  private void recalculate() {
    setRows(getText().split("\n").length);
    setMaximumSize(new Dimension(Integer.MAX_VALUE, getPreferredSize().height * 2));
  }

}
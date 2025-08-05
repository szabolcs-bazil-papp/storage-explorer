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

package com.aestallon.storageexplorer.swing.ui.explorer;

import java.awt.*;
import java.awt.event.ActionEvent;
import javax.swing.*;
import com.aestallon.storageexplorer.swing.ui.misc.CloseTabButton;

public class TabComponent extends JPanel {

  public final JLabel label;

  public <T extends JTabbedPane & TabContainer> TabComponent(final String title, T container) {
    super(new FlowLayout(FlowLayout.LEFT, 0, 0));
    setOpaque(false);
    label = new JLabel(title);
    add(label);
    add(new CloseTabButton(e -> {
      int idx = container.indexOfTabComponent(this);
      if (idx < 0) {
        return;
      }

      final var tabView = container.tabViewAt(idx);
      if (tabView == null) {
        return;
      }

      final int alt = ActionEvent.ALT_MASK;
      if ((e.getModifiers() & alt) == alt) {
        final int tabCount = container.getTabCount();
        if (idx < tabCount - 1) {
          for (int i = 0; i < tabCount - 1 - idx; i++) {
            container.discardTabView(container.tabViewAt(idx + 1));
          }
        }
        for (int i = 0; i < idx; i++) {
          container.discardTabView(container.tabViewAt(0));

        }
      } else {
        container.discardTabView(tabView);
      }
    }));
  }

}

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

package com.aestallon.storageexplorer.swing.ui.commander;

import java.awt.*;
import javax.swing.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class CommanderContainerView extends JPanel {

  private static final Logger log = LoggerFactory.getLogger(CommanderContainerView.class);

  public CommanderContainerView() {
    setLayout(new GridLayout(1, 1));
  }

  public void setCommanderView(final CommanderView commanderView) {
    boolean repaint = false;
    if (getComponentCount() > 0 && getComponent(0) != commanderView.asComponent()) {
      remove(0);
      repaint = true;
    }

    add(commanderView.asComponent());
    if (repaint) {
      revalidate();
      repaint();
    }
  }

}

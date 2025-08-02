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
import com.aestallon.storageexplorer.swing.ui.arcscript.ArcScriptContainerView;
import com.aestallon.storageexplorer.swing.ui.commander.console.ConsoleView;

@Component
public class CommanderView extends JPanel {

  private static final Logger log = LoggerFactory.getLogger(CommanderView.class);

  private final ArcScriptContainerView arcScriptContainerView;
  private final ConsoleView consoleView;

  public CommanderView(ArcScriptContainerView arcScriptContainerView, ConsoleView consoleView) {
    this.arcScriptContainerView = arcScriptContainerView;
    this.consoleView = consoleView;

    setLayout(new GridLayout(1, 1));
    setBorder(BorderFactory.createLineBorder(Color.MAGENTA));

    initArcScriptTab();
  } 

  private void initArcScriptTab() {
    add(consoleView);
  }

}

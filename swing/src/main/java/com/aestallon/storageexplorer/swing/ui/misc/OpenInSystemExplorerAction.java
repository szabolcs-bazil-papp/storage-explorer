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
import java.awt.event.ActionEvent;
import java.nio.file.Path;
import javax.swing.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.aestallon.storageexplorer.core.model.entry.StorageEntry;

public class OpenInSystemExplorerAction extends AbstractAction {
  
  private static final Logger log = LoggerFactory.getLogger(OpenInSystemExplorerAction.class);
  
  private final StorageEntry storageEntry;
  private final JComponent parent;
  
  public OpenInSystemExplorerAction(final StorageEntry storageEntry, JComponent parent) {
    super(null, IconProvider.SE);
    this.storageEntry = storageEntry;
    this.parent = parent;
  }

  @Override
  public void actionPerformed(ActionEvent e) {
    if (storageEntry == null) {
      return;
    }

    if (!Desktop.isDesktopSupported()) {
      return;
    }

    try {
      final Path path = storageEntry.path();
      if (path != null) {
        Desktop.getDesktop().open(path.getParent().toFile());
      } else {
        JOptionPane.showMessageDialog(
            parent,
            "This entry location is not available on the file system.",
            "Info",
            JOptionPane.INFORMATION_MESSAGE);
      }
    } catch (final Exception ex) {
      log.warn("Could not open [ {} ] in system explorer!", storageEntry.uri());
      log.debug(ex.getMessage(), ex);
      JOptionPane.showMessageDialog(
          parent,
          "Could not show entry location in System Explorer!",
          "Error",
          JOptionPane.ERROR_MESSAGE);
    }
  }
}

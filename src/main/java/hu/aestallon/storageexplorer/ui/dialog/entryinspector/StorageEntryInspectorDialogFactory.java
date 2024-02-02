/*
 * Copyright (C) 2024 it4all Hungary Kft.
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

package hu.aestallon.storageexplorer.ui.dialog.entryinspector;

import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.net.URI;
import java.util.HashSet;
import java.util.Set;
import javax.swing.*;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.ObjectMapper;
import hu.aestallon.storageexplorer.domain.storage.model.ListEntry;
import hu.aestallon.storageexplorer.domain.storage.model.MapEntry;
import hu.aestallon.storageexplorer.domain.storage.model.ObjectEntry;
import hu.aestallon.storageexplorer.domain.storage.model.StorageEntry;
import hu.aestallon.storageexplorer.domain.storage.service.StorageIndex;

@Service
public class StorageEntryInspectorDialogFactory {

  private final ObjectMapper objectMapper;
  private final StorageIndex storageIndex;
  private final Set<StorageEntry> openedDialogs;

  public StorageEntryInspectorDialogFactory(ObjectMapper objectMapper, StorageIndex storageIndex) {
    this.objectMapper = objectMapper;
    this.storageIndex = storageIndex;
    this.openedDialogs = new HashSet<>();
  }

  public void showDialog(StorageEntry storageEntry, Component parent) {
    if (openedDialogs.contains(storageEntry)) {
      return;
    }
    openedDialogs.add(storageEntry);

    final JFrame dialog;
    if (storageEntry instanceof MapEntry) {
      return;
    } else if (storageEntry instanceof ListEntry) {
      return;
    } else if (storageEntry instanceof ObjectEntry) {
      dialog = new ObjectEntryInspectorDialog((ObjectEntry) storageEntry, objectMapper, this);
    } else {
      throw new IllegalArgumentException(storageEntry + " is not interpreted!");
    }
    dialog.addWindowListener(new WindowAdapter() {
      @Override
      public void windowClosing(WindowEvent e) {
        openedDialogs.remove(storageEntry);
      }

    });
    dialog.setLocationRelativeTo(parent);
    dialog.setVisible(true);
  }

  public void showDialogOnUri(final URI uri) {
    storageIndex
        .get(uri)
        .ifPresentOrElse(
            it -> showDialog(it, null),
            () -> JOptionPane.showMessageDialog(
                null,
                "Cannot show URI: " + uri,
                "Unreachable URI",
                JOptionPane.ERROR_MESSAGE));
  }

}

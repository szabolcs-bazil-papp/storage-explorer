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

package hu.aestallon.storageexplorer.ui.editor;

import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import hu.aestallon.storageexplorer.domain.storage.model.ObjectEntry;
import hu.aestallon.storageexplorer.domain.storage.model.StorageEntry;
import hu.aestallon.storageexplorer.domain.storage.service.StorageEntryEditingService;
import hu.aestallon.storageexplorer.ui.dialog.StorageEntryEditorDialog;
import hu.aestallon.storageexplorer.ui.inspector.InspectorTextareaFactory;
import hu.aestallon.storageexplorer.ui.misc.MonospaceFontProvider;

@Service
public class StorageEntryEditorViewService {

  private static final Logger log = LoggerFactory.getLogger(StorageEntryEditorViewService.class);


  public static final class ShowView {

    private final StorageEntry storageEntry;

    public ShowView(final StorageEntry storageEntry) {
      this.storageEntry = storageEntry;
    }

  }


  private final StorageEntryEditingService editingService;
  private final MonospaceFontProvider monospaceFontProvider;
  private final InspectorTextareaFactory textareaFactory;
  private StorageEntryEditorDialog dialog;

  public StorageEntryEditorViewService(StorageEntryEditingService editingService,
                                       MonospaceFontProvider monospaceFontProvider) {
    this.editingService = editingService;
    this.monospaceFontProvider = monospaceFontProvider;
    this.textareaFactory = new InspectorTextareaFactory(monospaceFontProvider);
  }

  @EventListener(ShowView.class)
  public void onShowView(final ShowView showView) {
    if (dialog != null) {
      SwingUtilities.invokeLater(() -> {
        if (dialog.getState() != Frame.NORMAL) {
          dialog.setState(Frame.NORMAL);
        }

        dialog.toFront();
        dialog.repaint();
      });
      return;
    }

    show(showView.storageEntry);
  }

  public void show(final StorageEntry storageEntry) {
    final StorageEntryEditorView view = new StorageEntryEditorView(
        this,
        StorageEntryEditorView.edit(storageEntry));
    dialog = new StorageEntryEditorDialog(view);
    dialog.addWindowListener(new WindowAdapter() {
      @Override
      public void windowClosed(WindowEvent e) {
        super.windowClosed(e);
        dialog = null;
      }
    });
    dialog.setVisible(true);
  }

  InspectorTextareaFactory.PaneAndTextarea createEditorTextarea(final ObjectEntry objectEntry) {
    return textareaFactory.createEditor(objectEntry, objectEntry.load());
  }
}

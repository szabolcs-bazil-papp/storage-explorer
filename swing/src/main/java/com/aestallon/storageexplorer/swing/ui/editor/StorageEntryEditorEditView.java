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

package com.aestallon.storageexplorer.swing.ui.editor;

import java.awt.*;
import javax.swing.*;
import com.aestallon.storageexplorer.swing.ui.inspector.InspectorTextareaFactory;
import com.aestallon.storageexplorer.swing.ui.misc.PaneAndTextarea;

public class StorageEntryEditorEditView extends JPanel {

  private final StorageEntryEditorController controller;
  final JTextArea textArea;

  StorageEntryEditorEditView(StorageEntryEditorController controller) {
    this.controller = controller;
    setLayout(new GridLayout(1, 1));

    final PaneAndTextarea paneAndTextarea;
    if (controller.text() != null) {
      paneAndTextarea = controller.textareaFactory().create(
          controller.storageEntry(),
          controller.text(),
          new InspectorTextareaFactory.Config(InspectorTextareaFactory.Type.FANCY, false, false));
    } else {
      paneAndTextarea = controller.textareaFactory().create(
          controller.storageEntry(),
          controller.version(),
          new InspectorTextareaFactory.Config(InspectorTextareaFactory.Type.FANCY, false, false));
    }

    textArea = paneAndTextarea.textArea();
    add(paneAndTextarea.scrollPane());
  }
}

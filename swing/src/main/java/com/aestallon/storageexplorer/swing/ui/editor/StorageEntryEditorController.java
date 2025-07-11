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

import java.util.concurrent.CompletableFuture;
import javax.swing.*;
import org.springframework.context.ApplicationEventPublisher;
import com.aestallon.storageexplorer.client.storage.StorageInstanceProvider;
import com.aestallon.storageexplorer.core.event.EntryModified;
import com.aestallon.storageexplorer.core.model.entry.ObjectEntry;
import com.aestallon.storageexplorer.core.model.entry.StorageEntry;
import com.aestallon.storageexplorer.core.model.instance.StorageInstance;
import com.aestallon.storageexplorer.core.model.loading.ObjectEntryLoadResult;
import com.aestallon.storageexplorer.core.service.StorageEntryModificationService;
import com.aestallon.storageexplorer.swing.ui.inspector.InspectorTextareaFactory;
import com.aestallon.storageexplorer.swing.ui.inspector.StorageEntryInspectorViewFactory;

public class StorageEntryEditorController {

  enum State { INTENT, EDIT, REVIEW }


  private final ApplicationEventPublisher eventPublisher;
  private final InspectorTextareaFactory textareaFactory;
  private final StorageInstanceProvider storageInstanceProvider;
  private final StorageEntryInspectorViewFactory inspectorViewFactory;

  private StorageInstance storageInstance;
  private StorageEntry storageEntry;
  private ObjectEntryLoadResult.SingleVersion version;
  private long srcVersionNr;
  private boolean singleVersionMode;
  private String text;
  private State state = State.INTENT;

  public StorageEntryEditorController(ApplicationEventPublisher eventPublisher,
                                      InspectorTextareaFactory textareaFactory,
                                      StorageInstanceProvider storageInstanceProvider,
                                      StorageEntryInspectorViewFactory inspectorViewFactory) {
    this.eventPublisher = eventPublisher;
    this.textareaFactory = textareaFactory;
    this.storageInstanceProvider = storageInstanceProvider;
    this.inspectorViewFactory = inspectorViewFactory;
  }

  InspectorTextareaFactory textareaFactory() {
    return textareaFactory;
  }

  StorageEntryInspectorViewFactory inspectorViewFactory() {
    return inspectorViewFactory;
  }

  ApplicationEventPublisher eventPublisher() {
    return eventPublisher;
  }

  StorageEntry storageEntry() {
    return storageEntry;
  }

  ObjectEntryLoadResult.SingleVersion version() {
    return version;
  }

  long srcVersionNr() {
    return srcVersionNr;
  }

  boolean singleVersionMode() {
    return singleVersionMode;
  }

  String text() {
    return text;
  }

  void text(String text) {
    this.text = text;
  }

  State state() {
    return state;
  }

  public void launch(final StorageEntry storageEntry,
                     final ObjectEntryLoadResult.SingleVersion version,
                     final long versionNr) {
    storageInstance = storageInstanceProvider.get(storageEntry.storageId());
    this.storageEntry = storageEntry;
    this.version = version;
    srcVersionNr = versionNr;
    singleVersionMode = !(storageEntry instanceof ObjectEntry o)
                        || o.versioning() instanceof ObjectEntry.Versioning.Single;
    text = version.oamStr();

    final var frame = new StorageEntryEditorFrame(this);
    frame.setVisible(true);
  }

  void proceed(StorageEntryEditorFrame frame) {
    switch (state) {
      case INTENT -> {
        state = State.EDIT;
        frame.back.setEnabled(true);
        frame.contentPane.header.setText("Make Your Edits");
        frame.contentPane.setContent(new StorageEntryEditorEditView(this));
      }
      case EDIT -> {
        state = State.REVIEW;
        frame.contentPane.header.setText("Review Your Changes");
        text(((StorageEntryEditorEditView) frame.contentPane.content).textArea.getText());
        frame.contentPane.setContent(new StorageEntryEditorDiffView(this));
      }
      case REVIEW -> {
        frame.proceed.setEnabled(false);
        CompletableFuture.supplyAsync(() -> storageInstance
                .index()
                .modifier()
                .modify(storageEntry, text(), null))
            .thenAccept(result -> {
              switch (result) {
                case StorageEntryModificationService.StorageEntryModificationResult.Ok ok -> {
                  eventPublisher.publishEvent(new EntryModified(storageEntry));
                  SwingUtilities.invokeLater(frame::dispose);
                }
                case StorageEntryModificationService.StorageEntryModificationResult.Err(
                    var entry, var msg
                ) -> {
                  SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(frame, msg, "Error", JOptionPane.ERROR_MESSAGE);
                    frame.proceed.setEnabled(true);
                  });
                }
              }
            });
      }
    }
  }

  void back(StorageEntryEditorFrame frame) {
    switch (state) {
      case INTENT -> { /* do nothing. */ }
      case EDIT -> {
        state = State.INTENT;
        frame.back.setEnabled(false);
        frame.contentPane.header.setText("Declare Your Intent");
        frame.contentPane.setContent(new StorageEntryEditorIntentView(this));
      }
      case REVIEW -> {
        state = State.EDIT;
        frame.contentPane.header.setText("Make Your Edits");
        frame.contentPane.setContent(new StorageEntryEditorEditView(this));
      }
    }
  }

}

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
import java.util.stream.Stream;
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
import com.aestallon.storageexplorer.swing.ui.inspector.StorageEntryVersionDiffView;
import com.aestallon.storageexplorer.swing.ui.misc.IconProvider;
import jakarta.annotation.Nonnull;

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
  private ObjectEntryLoadResult.SingleVersion headVersion;
  private long headVersionNr;
  private String text;
  private State state = State.INTENT;
  private StorageEntryModificationService.ModificationMode modificationMode;

  public StorageEntryEditorController(ApplicationEventPublisher eventPublisher,
                                      InspectorTextareaFactory textareaFactory,
                                      StorageInstanceProvider storageInstanceProvider,
                                      StorageEntryInspectorViewFactory inspectorViewFactory) {
    this.eventPublisher = eventPublisher;
    this.textareaFactory = textareaFactory;
    this.storageInstanceProvider = storageInstanceProvider;
    this.inspectorViewFactory = inspectorViewFactory;
  }

  public InspectorTextareaFactory textareaFactory() {
    return textareaFactory;
  }

  StorageEntryInspectorViewFactory inspectorViewFactory() {
    return inspectorViewFactory;
  }

  ApplicationEventPublisher eventPublisher() {
    return eventPublisher;
  }

  public StorageEntry storageEntry() {
    return storageEntry;
  }

  public ObjectEntryLoadResult.SingleVersion version() {
    return version;
  }

  long srcVersionNr() {
    return srcVersionNr;
  }

  boolean singleVersionMode() {
    return singleVersionMode;
  }

  public ObjectEntryLoadResult.SingleVersion headVersion() {
    return headVersion;
  }

  long headVersionNr() {
    return headVersionNr;
  }

  public String text() {
    return text;
  }

  void text(String text) {
    this.text = text;
  }

  State state() {
    return state;
  }

  public StorageEntryModificationService.ModificationMode modificationMode() {
    return modificationMode;
  }

  public void launch(final StorageEntry storageEntry,
                     final ObjectEntryLoadResult.SingleVersion version,
                     final long versionNr,
                     final ObjectEntryLoadResult.SingleVersion headVersion,
                     final long headVersionNr) {
    storageInstance = storageInstanceProvider.get(storageEntry.storageId());
    this.storageEntry = storageEntry;
    this.version = version;
    srcVersionNr = versionNr;
    singleVersionMode = !(storageEntry instanceof ObjectEntry o)
                        || o.versioning() instanceof ObjectEntry.Versioning.Single;
    if (!singleVersionMode) {
      this.headVersion = headVersion;
      this.headVersionNr = headVersionNr;
    }
    text = version.oamStr();

    final var frame = new StorageEntryEditorFrame(this);
    frame.setVisible(true);
  }

  void proceed(StorageEntryEditorFrame frame) {
    switch (state) {
      case INTENT -> {
        final var intentView = (StorageEntryEditorIntentView) frame.contentPane.content;
        try {
          modificationMode = determineModificationMode(intentView);
        } catch (ModificationModeDeterminationException e) {
          modificationMode = null;
          JOptionPane.showMessageDialog(frame, e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
          return;
        }

        state = State.EDIT;
        frame.back.setEnabled(true);
        setHeaderText(frame);
        setToolbarText(frame);
        frame.contentPane.setContent(new StorageEntryEditorEditView(this));
      }
      case EDIT -> {
        state = State.REVIEW;
        setHeaderText(frame);
        setToolbarText(frame);
        text(((StorageEntryEditorEditView) frame.contentPane.content).textArea.getText());
        frame.contentPane.setContent(new StorageEntryVersionDiffView(this));
      }
      case REVIEW -> {
        frame.proceed.setEnabled(false);
        int confirm = JOptionPane.showConfirmDialog(
            frame,
            getConfirmDialogText(),
            "Confirm Modification",
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.WARNING_MESSAGE,
            IconProvider.WARNING);
        if (confirm != JOptionPane.OK_OPTION) {
          frame.proceed.setEnabled(true);
          return;
        }

        CompletableFuture.supplyAsync(() -> storageInstance
                .index()
                .modifier()
                .modify(storageEntry, text(), modificationMode))
            .thenAccept(result -> {
              switch (result) {
                case StorageEntryModificationService.StorageEntryModificationResult.Ok ok -> {
                  eventPublisher.publishEvent(new EntryModified(storageEntry));
                  SwingUtilities.invokeLater(frame::dispose);
                }
                case StorageEntryModificationService.StorageEntryModificationResult.Err(
                    var entry, var msg
                ) -> SwingUtilities.invokeLater(() -> {
                  JOptionPane.showMessageDialog(frame, msg, "Error", JOptionPane.ERROR_MESSAGE);
                  frame.proceed.setEnabled(true);
                });
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
        setHeaderText(frame);
        setToolbarText(frame);
        frame.contentPane.setContent(new StorageEntryEditorIntentView(this));
      }
      case REVIEW -> {
        state = State.EDIT;
        setHeaderText(frame);
        setToolbarText(frame);
        frame.contentPane.setContent(new StorageEntryEditorEditView(this));
      }
    }
  }

  void setHeaderText(final StorageEntryEditorFrame frame) {
    final String s = switch (state) {
      case INTENT -> "Declare Your Intent";
      case EDIT -> "Make Your Changes";
      case REVIEW -> "Review Your Modifications";
    };
    frame.contentPane.header.setText(s);
  }

  void setToolbarText(final StorageEntryEditorFrame frame) {
    switch (state) {
      case INTENT -> frame.toolbarLabel.setText(" ");
      case EDIT -> frame.toolbarLabel.setText(
          "<HTML><P>Your are editing "
          + (singleVersionMode
              ? "the <B>single version</B> of "
              : "version v<B>" + srcVersionNr + "</B> of ")
          + storageEntry.uri()
          + " ("
          + StorageEntry.typeNameOf(storageEntry)
          + ")</P></HTML>");
      case REVIEW -> {
        final StringBuilder sb = new StringBuilder("<HTML><P>Your are ");
        switch (modificationMode) {
          case StorageEntryModificationService.ModificationMode.Overwrite ow -> sb
              .append("overwriting version <B>v").append(srcVersionNr).append("</B> of ")
              .append(storageEntry.uri()).append(" (").append(StorageEntry.typeNameOf(storageEntry))
              .append("), ")
              .append("the <B>LEFT</B> side shows the original content of version <B>v")
              .append(srcVersionNr).append("</B>, ");
          case StorageEntryModificationService.ModificationMode.OverwriteSingleVersion sv -> sb
              .append("modifying the single version of ")
              .append(storageEntry.uri()).append(" (").append(StorageEntry.typeNameOf(storageEntry))
              .append("), ")
              .append("the <B>LEFT</B> side shows the original content, ");
          case StorageEntryModificationService.ModificationMode.SaveNewVersion nv -> sb
              .append("creating a new version of ")
              .append(storageEntry.uri()).append(" (").append(StorageEntry.typeNameOf(storageEntry))
              .append("), ")
              .append("the <B>LEFT</B> side shows the content of the latest known version <B>v")
              .append(headVersionNr)
              .append("</B>, ");
        }
        sb.append("the <B>RIGHT</B> side shows your modifications.</P></HTML>");
        frame.toolbarLabel.setText(sb.toString());
      }
    }
  }

  private static final class ModificationModeDeterminationException extends Exception {
    ModificationModeDeterminationException(String message) {
      super(message);
    }
  }

  @Nonnull
  StorageEntryModificationService.ModificationMode determineModificationMode(
      StorageEntryEditorIntentView intentView) throws ModificationModeDeterminationException {
    final StorageEntryModificationService.ModificationMode mode;
    if (singleVersionMode) {
      if (intentView.svModificationConsent.isSelected()) {
        mode = new StorageEntryModificationService.ModificationMode.OverwriteSingleVersion();
      } else {
        throw new ModificationModeDeterminationException(
            "You must declare your intent to modify the single version entry!");
      }
    } else {
      if (intentView.optionOverride.isSelected()) {
        mode = new StorageEntryModificationService.ModificationMode.Overwrite(srcVersionNr);
      } else if (intentView.optionSaveNewVersion.isSelected()) {
        mode = new StorageEntryModificationService.ModificationMode.SaveNewVersion(-1);
      } else {
        throw new ModificationModeDeterminationException(
            "You must declare your intent to override or create a new version of the entry!");
      }
    }

    if (!Stream
        .of(
            intentView.authorisationConsent,
            intentView.licenceConsent,
            intentView.experimentalConsent)
        .allMatch(JCheckBox::isSelected)) {
      throw new ModificationModeDeterminationException(
          "You must declare consent for all aspects of your modification!");
    }
    return mode;
  }

  String getConfirmDialogText() {
    return "Are you sure?";
  }

}

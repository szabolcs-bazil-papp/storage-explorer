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
import java.awt.event.ActionEvent;
import java.util.Arrays;
import java.util.Map;
import javax.swing.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.difflib.text.DiffRowGenerator;
import hu.aestallon.storageexplorer.domain.storage.model.ObjectEntry;
import hu.aestallon.storageexplorer.domain.storage.model.StorageEntry;
import hu.aestallon.storageexplorer.ui.inspector.InspectorView;
import hu.aestallon.storageexplorer.ui.misc.IconProvider;

public final class StorageEntryEditorView extends JPanel implements InspectorView<StorageEntry> {

  private static final Logger log = LoggerFactory.getLogger(StorageEntryEditorView.class);


  enum Phase {
    EDITOR, DIFF
  }

  static StorageEntryEditorConfig edit(final StorageEntry storageEntry) {
    return new StorageEntryEditorConfig(storageEntry, Phase.EDITOR, -1L);
  }

  static StorageEntryEditorConfig rebase(final StorageEntry storageEntry, final long version) {
    return new StorageEntryEditorConfig(storageEntry, Phase.DIFF, version);
  }

  static final class StorageEntryEditorConfig {

    private final StorageEntry storageEntry;
    private final Phase phaseToOpen;
    private final long rebaseVersion;

    StorageEntryEditorConfig(StorageEntry storageEntry, Phase phaseToOpen, long rebaseVersion) {
      this.storageEntry = storageEntry;
      this.phaseToOpen = phaseToOpen;
      this.rebaseVersion = rebaseVersion;
    }

  }


  private final StorageEntryEditorViewService service;
  private final StorageEntry storageEntry;
  private final long rebaseVersion;

  private Phase phase;

  private JScrollPane editorPane;
  private JTextArea editorArea;

  private String originalText;

  public StorageEntryEditorView(StorageEntryEditorViewService service,
                                StorageEntryEditorConfig config) {
    super(new BorderLayout());

    this.service = service;
    this.storageEntry = config.storageEntry;
    this.rebaseVersion = config.rebaseVersion;
    this.phase = config.phaseToOpen;

    initSelf();
  }

  @Override
  public StorageEntry storageEntry() {
    return storageEntry;
  }

  private void initSelf() {
    final var toolbar = new JToolBar();
    toolbar.add(new AbstractAction(null, IconProvider.EDIT) {
      @Override
      public void actionPerformed(ActionEvent e) {
        try {
          onEditFinish();
        } catch (JsonProcessingException ex) {
          log.error(ex.getMessage(), ex);
        }
      }
    });
    add(toolbar, BorderLayout.NORTH);
    initContent();
  }

  private void initContent() {
    if (phase == Phase.EDITOR) {
      renderEditor();
    } else {
      renderDifftool();
    }
  }

  private void renderEditor() {
    if (!(storageEntry instanceof ObjectEntry)) {
      return;
    }

    if (editorPane != null) {
      add(editorPane, BorderLayout.CENTER);
      return;
    }

    final ObjectEntry objectEntry = (ObjectEntry) storageEntry;
    final var paneAndTextarea = service.createEditorTextarea(objectEntry);
    editorPane = paneAndTextarea.scrollPane;
    editorArea = paneAndTextarea.textArea;
    originalText = editorArea.getText();

    add(editorPane, BorderLayout.CENTER);
  }

  private void onEditFinish() throws JsonProcessingException {
    final String text = editorArea.getText();
    final ObjectMapper om = new ObjectMapper();
    final Map<String, Object> objectMap = om.readValue(text, new TypeReference<>() {
    });
    String formattedText = om.writerWithDefaultPrettyPrinter().writeValueAsString(objectMap);
    final var diffRowGenerator = DiffRowGenerator.create()
        .showInlineDiffs(true)
        .inlineDiffByWord(true)
        .oldTag(f -> "~")
        .newTag(f -> "**")
        .build();
    final var diffRows = diffRowGenerator.generateDiffRows(
        Arrays.asList(originalText.split("\\r")),
        Arrays.asList(formattedText.split("\\r")));
    diffRows.forEach(
        it -> log.info(">>> {} | {} | {} |", it.getTag(), it.getOldLine(), it.getNewLine()));
  }

  private void renderDifftool() {

  }

}

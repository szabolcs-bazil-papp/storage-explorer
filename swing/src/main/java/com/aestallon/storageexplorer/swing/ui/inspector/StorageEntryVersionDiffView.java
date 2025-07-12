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

package com.aestallon.storageexplorer.swing.ui.inspector;

import java.awt.*;
import java.util.Arrays;
import java.util.List;
import javax.swing.*;
import javax.swing.text.BadLocationException;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.aestallon.storageexplorer.core.model.entry.StorageEntry;
import com.aestallon.storageexplorer.core.model.loading.ObjectEntryLoadResult;
import com.aestallon.storageexplorer.core.service.StorageEntryModificationService;
import com.aestallon.storageexplorer.swing.ui.editor.StorageEntryEditorController;
import com.aestallon.storageexplorer.swing.ui.misc.PaneAndTextarea;
import com.github.difflib.text.DiffRow;
import com.github.difflib.text.DiffRowGenerator;

public class StorageEntryVersionDiffView extends JPanel {

  private static final Logger log = LoggerFactory.getLogger(StorageEntryVersionDiffView.class);
  
  private static final Color COLOUR_MOD = new Color(128, 128, 0, 60);
  private static final Color COLOUR_DEL = new Color(128, 0, 0, 60);
  private static final Color COLOUR_INS = new Color(0, 128, 0, 60);


  private final JTextArea leftTextArea;
  private final JTextArea rightTextArea;

  public StorageEntryVersionDiffView(final InspectorTextareaFactory textareaFactory,
                                     final StorageEntry storageEntry,
                                     final ObjectEntryLoadResult.SingleVersion a, 
                                     final ObjectEntryLoadResult.SingleVersion b) {
    setLayout(new GridLayout(1, 2));
    
    final var left = textareaFactory.create(
        storageEntry,
        a,
        new InspectorTextareaFactory.Config(InspectorTextareaFactory.Type.FANCY, true, false));
    final var right = textareaFactory.create(
        storageEntry,
        b,
        new InspectorTextareaFactory.Config(InspectorTextareaFactory.Type.FANCY, true, false));
    leftTextArea = left.textArea();
    rightTextArea = right.textArea();
    add(left.scrollPane());
    add(right.scrollPane());
    showDiff();
  } 
  
  public StorageEntryVersionDiffView(final StorageEntryEditorController controller) {
    setLayout(new GridLayout(1, 2));

    final PaneAndTextarea left = controller.textareaFactory().create(
        controller.storageEntry(),
        controller.modificationMode() instanceof StorageEntryModificationService.ModificationMode.SaveNewVersion
            ? controller.headVersion()
            : controller.version(),
        new InspectorTextareaFactory.Config(InspectorTextareaFactory.Type.FANCY, true, false));
    final var right = controller.textareaFactory().create(
        controller.storageEntry(),
        controller.text(),
        new InspectorTextareaFactory.Config(InspectorTextareaFactory.Type.FANCY, true, false));

    leftTextArea = left.textArea();
    rightTextArea = right.textArea();
    add(left.scrollPane());
    add(right.scrollPane());
    showDiff();
  }

  private void showDiff() {
    final List<DiffRow> diffRows = DiffRowGenerator.create()
        .inlineDiffByWord(true)
        .build()
        .generateDiffRows(
            Arrays.asList(leftTextArea.getText().split("\n")),
            Arrays.asList(rightTextArea.getText().split("\n")));
    applyDifferences((RSyntaxTextArea) leftTextArea, (RSyntaxTextArea) rightTextArea, diffRows);
  }

  private void applyDifferences(final RSyntaxTextArea left,
                                final RSyntaxTextArea right,
                                final List<DiffRow> diffRows) {

    int leftPtr = 0;
    int rightPtr = 0;
    for (final var row : diffRows) {
      try {

        switch (row.getTag()) {
          case EQUAL -> {
            leftPtr++;
            rightPtr++;
          }
          case CHANGE -> {
            left.addLineHighlight(leftPtr++, COLOUR_MOD);
            right.addLineHighlight(rightPtr++, COLOUR_MOD);
          }
          case DELETE -> left.addLineHighlight(leftPtr++, COLOUR_DEL);
          case INSERT -> right.addLineHighlight(rightPtr++, COLOUR_INS);
        }

      } catch (final BadLocationException e) {
        log.error(e.getMessage(), e);
      }
    }
  }

}

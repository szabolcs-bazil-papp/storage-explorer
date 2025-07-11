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
import java.util.Arrays;
import java.util.List;
import javax.swing.*;
import javax.swing.text.BadLocationException;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import com.aestallon.storageexplorer.swing.ui.misc.PaneAndTextarea;
import com.github.difflib.text.DiffRow;
import com.github.difflib.text.DiffRowGenerator;

public class StorageEntryEditorDiffView extends JPanel {

  private final transient StorageEntryEditorController controller;

  private final JTextArea leftTextArea;
  private final JTextArea rightTextArea;

  StorageEntryEditorDiffView(final StorageEntryEditorController controller) {
    this.controller = controller;

    setLayout(new GridLayout(1, 2));

    final PaneAndTextarea left = controller.textareaFactory().create(
        controller.storageEntry(),
        controller.version(),
        true);
    final var right = controller.textareaFactory().create(
        controller.storageEntry(),
        controller.text(),
        false);

    leftTextArea = left.textArea();
    rightTextArea = right.textArea();

    // Add scroll panes

    add(left.scrollPane());
    add(right.scrollPane());

    showDiff();

  }

  public void showDiff() {
    final List<DiffRow> diffRows = DiffRowGenerator.create()
        .inlineDiffByWord(true)
        .build()
        .generateDiffRows(
            Arrays.asList(leftTextArea.getText().split("\n")),
            Arrays.asList(rightTextArea.getText().split("\n")));
    applyDifferences((RSyntaxTextArea) leftTextArea, (RSyntaxTextArea) rightTextArea, diffRows);
  }

  private void applyDifferences(RSyntaxTextArea left,
                                RSyntaxTextArea right,
                                List<DiffRow> diffRows) {

    int leftPtr = 0;
    int rightPtr = 0;
    LOOP:
    for (final var row : diffRows) {
      try {

        switch (row.getTag()) {
          case EQUAL -> {
            leftPtr++;
            rightPtr++;
          }
          case CHANGE -> {
            left.addLineHighlight(leftPtr++, new Color(128, 128, 0, 60));
            right.addLineHighlight(rightPtr++, new Color(128, 128, 0, 60));
          }
          case DELETE -> left.addLineHighlight(leftPtr++, new Color(128, 0, 0, 60));
          case INSERT -> right.addLineHighlight(rightPtr++, new Color(0, 128, 0, 60));
        }
      
      } catch (final BadLocationException e) {
        System.err.println(e.getMessage());
      }
    }
  }


  public static void main(String[] args) {
    final var left = """
        a
        b2
        c
        d
        e""";
    final var right = """
        a
        b
        e
        g""";
    List<DiffRow> diffRows = DiffRowGenerator.create()
        .inlineDiffByWord(true)
        .build()
        .generateDiffRows(
            Arrays.asList(left.split("\n")),
            Arrays.asList(right.split("\n")));
    for (final var row : diffRows) {
      System.out.println(row.getTag());
    }
  }

}

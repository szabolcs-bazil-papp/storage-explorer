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

package hu.aestallon.storageexplorer.swing.ui.inspector;

import java.awt.*;
import javax.swing.*;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rtextarea.RTextScrollPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import hu.aestallon.storageexplorer.storage.model.entry.ObjectEntry;
import hu.aestallon.storageexplorer.storage.model.loading.ObjectEntryLoadResult;


public final class InspectorTextareaFactory {

  private static final Logger log = LoggerFactory.getLogger(InspectorTextareaFactory.class);

  static final class PaneAndTextarea {
    final JScrollPane scrollPane;
    final JTextArea textArea;

    PaneAndTextarea(JScrollPane scrollPane, JTextArea textArea) {
      this.scrollPane = scrollPane;
      this.textArea = textArea;
    }

  }


  public enum Type { SIMPLE, FANCY; }

  static JScrollPane textAreaContainerPane(JTextArea objectAsMapTextarea) {
    final var pane = new JScrollPane(
        objectAsMapTextarea,
        ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
        ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    pane.setPreferredSize(new Dimension(2000, 2000));
    return pane;
  }

  private final StorageEntryInspectorViewFactory inspectorViewFactory;

  public InspectorTextareaFactory(StorageEntryInspectorViewFactory inspectorViewFactory) {
    this.inspectorViewFactory = inspectorViewFactory;
  }

  PaneAndTextarea create(final ObjectEntry objectEntry,
                         final ObjectEntryLoadResult.SingleVersion nodeVersion) {
    return create(objectEntry, nodeVersion, Type.FANCY);
  }

  private PaneAndTextarea create(final ObjectEntry objectEntry,
                                 final ObjectEntryLoadResult.SingleVersion nodeVersion,
                                 final Type type) {
    return switch (type) {
      case SIMPLE -> createSimple(objectEntry, nodeVersion);
      case FANCY -> createFancy(objectEntry, nodeVersion);
    };
  }

  private PaneAndTextarea createSimple(final ObjectEntry objectEntry,
                                       final ObjectEntryLoadResult.SingleVersion nodeVersion) {
    final var textarea = objectAsMapTextarea(objectEntry, nodeVersion);
    final var pane = textAreaContainerPane(textarea);
    return new PaneAndTextarea(pane, textarea);
  }

  private PaneAndTextarea createFancy(final ObjectEntry objectEntry,
                                      final ObjectEntryLoadResult.SingleVersion nodeVersion) {
    final var textarea = new RSyntaxTextArea(nodeVersion.oamStr());
    textarea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JSON);
    textarea.setCodeFoldingEnabled(true);
    textarea.setWrapStyleWord(true);
    textarea.setLineWrap(true);
    textarea.setEditable(false);
    textarea.setFont(inspectorViewFactory.monospaceFontProvider().getFont());

    inspectorViewFactory.addJumpAction(textarea);
    inspectorViewFactory.monospaceFontProvider().applyFontSizeChangeAction(textarea);

    inspectorViewFactory.submitTextArea(objectEntry, textarea);

    final var scrollPane = new RTextScrollPane(textarea);
    //scrollPane.add(textarea);
    return new PaneAndTextarea(scrollPane, textarea);
  }

  private JTextArea objectAsMapTextarea(final ObjectEntry objectEntry,
                                        final ObjectEntryLoadResult.SingleVersion nodeVersion) {
    final var textarea = new JTextArea(nodeVersion.oamStr(), 0, 80);
    textarea.setWrapStyleWord(true);
    textarea.setLineWrap(true);
    textarea.setEditable(false);
    textarea.setOpaque(false);
    textarea.setFont(inspectorViewFactory.monospaceFontProvider().getFont());

    inspectorViewFactory.addJumpAction(textarea);
    inspectorViewFactory.monospaceFontProvider().applyFontSizeChangeAction(textarea);

    inspectorViewFactory.submitTextArea(objectEntry, textarea);

    return textarea;
  }

}

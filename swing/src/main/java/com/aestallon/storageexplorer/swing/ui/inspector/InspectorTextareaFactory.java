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

package com.aestallon.storageexplorer.swing.ui.inspector;

import java.awt.*;
import java.io.IOException;
import javax.swing.*;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rsyntaxtextarea.Theme;
import org.fife.ui.rtextarea.RTextScrollPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.aestallon.storageexplorer.core.model.entry.ObjectEntry;
import com.aestallon.storageexplorer.core.model.loading.ObjectEntryLoadResult;
import com.aestallon.storageexplorer.swing.ui.event.LafChanged;


public final class InspectorTextareaFactory {

  private static final Logger log = LoggerFactory.getLogger(InspectorTextareaFactory.class);


  record PaneAndTextarea(JScrollPane scrollPane, JTextArea textArea) {}


  public enum Type { SIMPLE, FANCY }

  static JScrollPane textAreaContainerPane(JTextArea objectAsMapTextarea) {
    final var pane = new JScrollPane(
        objectAsMapTextarea,
        ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
        ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    pane.setPreferredSize(new Dimension(2000, 2000));
    return pane;
  }

  private final StorageEntryInspectorViewFactory inspectorViewFactory;
  private final Theme lightTheme;
  private final Theme darkTheme;

  private Theme currentTheme;

  public InspectorTextareaFactory(StorageEntryInspectorViewFactory inspectorViewFactory) {
    this.inspectorViewFactory = inspectorViewFactory;
    lightTheme = loadLightTheme();
    darkTheme = loadDarkTheme();

    currentTheme = lightTheme;
  }

  private Theme loadLightTheme() {
    try {
      return Theme.load(getClass().getResourceAsStream(
          "/org/fife/ui/rsyntaxtextarea/themes/idea.xml"));
    } catch (IOException ignored) {
      return null;
    }
  }

  public void setCurrentTheme(LafChanged.Laf laf) {
    this.currentTheme = switch (laf) {
      case LIGHT -> lightTheme;
      case DARK -> darkTheme;
    };
  }
  
  public void applyCurrentTheme(JTextArea textArea) {
    if (textArea instanceof RSyntaxTextArea fancy) {
      currentTheme.apply(fancy);
      
    }
  }

  private Theme loadDarkTheme() {
    try {
      return Theme.load(getClass().getResourceAsStream(
          "/org/fife/ui/rsyntaxtextarea/themes/dark.xml"));
    } catch (IOException ignored) {
      return null;
    }
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
    if (currentTheme != null) {
      currentTheme.apply(textarea);
    }

    textarea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JSON);
    textarea.setCodeFoldingEnabled(true);
    textarea.setWrapStyleWord(true);
    textarea.setLineWrap(true);
    textarea.setEditable(false);
    textarea.setFont(inspectorViewFactory.monospaceFontProvider().getFont());

    inspectorViewFactory.addJumpAction(objectEntry.storageId(), textarea);
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

    inspectorViewFactory.addJumpAction(objectEntry.storageId(), textarea);
    inspectorViewFactory.monospaceFontProvider().applyFontSizeChangeAction(textarea);

    inspectorViewFactory.submitTextArea(objectEntry, textarea);

    return textarea;
  }

}

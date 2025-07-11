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
import javax.swing.*;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rtextarea.RTextScrollPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.aestallon.storageexplorer.core.model.entry.StorageEntry;
import com.aestallon.storageexplorer.core.model.loading.ObjectEntryLoadResult;
import com.aestallon.storageexplorer.swing.ui.misc.PaneAndTextarea;
import com.aestallon.storageexplorer.swing.ui.misc.RSyntaxTextAreaThemeProvider;


public final class InspectorTextareaFactory {

  private static final Logger log = LoggerFactory.getLogger(InspectorTextareaFactory.class);


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
  private final RSyntaxTextAreaThemeProvider themeProvider;

  public InspectorTextareaFactory(StorageEntryInspectorViewFactory inspectorViewFactory,
                                  RSyntaxTextAreaThemeProvider themeProvider) {
    this.inspectorViewFactory = inspectorViewFactory;
    this.themeProvider = themeProvider;
  }

  public PaneAndTextarea create(final StorageEntry storageEntry,
                                final ObjectEntryLoadResult.SingleVersion nodeVersion,
                                final boolean readOnly) {
    final var oamStr = nodeVersion.oamStr();
    return create(storageEntry, oamStr, readOnly);
  }

  public PaneAndTextarea create(final StorageEntry storageEntry,
                                final String text,
                                final boolean readOnly) {
    if (text.length() >= 500_000) {
      return createSimple(storageEntry, text, readOnly);
    }

    return createFancy(storageEntry, text, readOnly);
  }

  public PaneAndTextarea create(final StorageEntry storageEntry,
                                final ObjectEntryLoadResult.SingleVersion nodeVersion,
                                final boolean readOnly,
                                final Type type) {
    final var oamStr = nodeVersion.oamStr();
    return switch (type) {
      case SIMPLE -> createSimple(storageEntry, oamStr, readOnly);
      case FANCY -> createFancy(storageEntry, oamStr, readOnly);
    };
  }


  private PaneAndTextarea createSimple(final StorageEntry storageEntry,
                                       final String oamStr,
                                       final boolean readOnly) {
    final var textarea = objectAsMapTextarea(storageEntry, oamStr, readOnly);
    final var pane = textAreaContainerPane(textarea);
    return new PaneAndTextarea(pane, textarea);
  }

  private PaneAndTextarea createFancy(final StorageEntry storageEntry,
                                      final String oamStr,
                                      final boolean readOnly) {
    final var textarea = new RSyntaxTextArea(oamStr);
    if (themeProvider.hasTheme()) {
      themeProvider.applyCurrentTheme(textarea);
    }

    textarea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JSON);
    textarea.setCodeFoldingEnabled(true);
    textarea.setWrapStyleWord(true);
    textarea.setLineWrap(true);
    textarea.setEditable(!readOnly);
    textarea.setFont(inspectorViewFactory.monospaceFontProvider().getFont());

    inspectorViewFactory.addJumpAction(storageEntry.storageId(), textarea);
    inspectorViewFactory.monospaceFontProvider().applyFontSizeChangeAction(textarea);

    if (readOnly) {
      inspectorViewFactory.submitTextArea(storageEntry, textarea);
    }

    final var scrollPane = new RTextScrollPane(textarea);
    scrollPane.setMinimumSize(new Dimension(200, 200));
    return new PaneAndTextarea(scrollPane, textarea);
  }


  private JTextArea objectAsMapTextarea(final StorageEntry storageEntry,
                                        final String oamStr,
                                        final boolean readOnly) {
    final var textarea = new JTextArea(oamStr, 0, 80);
    textarea.setWrapStyleWord(true);
    textarea.setLineWrap(true);
    textarea.setEditable(!readOnly);
    textarea.setOpaque(!readOnly);
    textarea.setFont(inspectorViewFactory.monospaceFontProvider().getFont());

    inspectorViewFactory.addJumpAction(storageEntry.storageId(), textarea);
    inspectorViewFactory.monospaceFontProvider().applyFontSizeChangeAction(textarea);

    if (readOnly) {
      inspectorViewFactory.submitTextArea(storageEntry, textarea);
    }

    return textarea;
  }

}

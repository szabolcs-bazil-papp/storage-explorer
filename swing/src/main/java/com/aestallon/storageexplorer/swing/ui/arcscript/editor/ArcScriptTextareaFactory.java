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

package com.aestallon.storageexplorer.swing.ui.arcscript.editor;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rtextarea.RTextScrollPane;
import com.aestallon.storageexplorer.swing.ui.misc.MonospaceFontProvider;
import com.aestallon.storageexplorer.swing.ui.misc.PaneAndTextarea;
import com.aestallon.storageexplorer.swing.ui.misc.RSyntaxTextAreaThemeProvider;

public final class ArcScriptTextareaFactory {

  private final RSyntaxTextAreaThemeProvider themeProvider;
  private final MonospaceFontProvider monospaceFontProvider;

  public ArcScriptTextareaFactory(RSyntaxTextAreaThemeProvider themeProvider,
                                  MonospaceFontProvider monospaceFontProvider) {
    this.themeProvider = themeProvider;
    this.monospaceFontProvider = monospaceFontProvider;
  }

  PaneAndTextarea create(final String text) {
    final RSyntaxTextArea textarea;
    if (text == null) {
      textarea = new RSyntaxTextArea();
    } else {
      textarea = new RSyntaxTextArea(text);
    }

    if (themeProvider.hasTheme()) {
      themeProvider.applyCurrentTheme(textarea);
    }

    textarea.setSyntaxEditingStyle(RSyntaxTextAreaThemeProvider.SYNTAX_STYLE_ARCSCRIPT);
    textarea.setCodeFoldingEnabled(true);
    textarea.setWrapStyleWord(true);
    textarea.setLineWrap(true);
    textarea.setFont(monospaceFontProvider.getFont());
    monospaceFontProvider.applyFontSizeChangeAction(textarea);

    final var scrollPane = new RTextScrollPane(textarea);
    return new PaneAndTextarea(scrollPane, textarea);
  }

}
// this is largely copy-paste code: I practically settled on RSyntaxTextArea ---> so refactor!

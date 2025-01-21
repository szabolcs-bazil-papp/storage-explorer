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

package com.aestallon.storageexplorer.swing.ui.misc;

import java.io.IOException;
import javax.swing.*;
import org.fife.ui.rsyntaxtextarea.AbstractTokenMakerFactory;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.Theme;
import org.fife.ui.rsyntaxtextarea.TokenMakerFactory;
import org.springframework.stereotype.Service;
import com.aestallon.storageexplorer.swing.ui.event.LafChanged;

@Service
public class RSyntaxTextAreaThemeProvider {

  public static final String SYNTAX_STYLE_ARCSCRIPT = "text/arcscript";

  private final Theme lightTheme;
  private final Theme darkTheme;

  private Theme currentTheme;

  public RSyntaxTextAreaThemeProvider() {
    lightTheme = loadLightTheme();
    darkTheme = loadDarkTheme();
    currentTheme = lightTheme;

    AbstractTokenMakerFactory atmf =
        (AbstractTokenMakerFactory) TokenMakerFactory.getDefaultInstance();
    atmf.putMapping(
        SYNTAX_STYLE_ARCSCRIPT,
        "com.aestallon.storageexplorer.swing.ui.commander.arcscript.ArcScriptTokenMaker");
  }

  private Theme loadLightTheme() {
    try {
      return Theme.load(getClass().getResourceAsStream(
          "/org/fife/ui/rsyntaxtextarea/themes/idea.xml"));
    } catch (IOException ignored) {
      return null;
    }
  }

  public boolean hasTheme() {
    return currentTheme != null;
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

}

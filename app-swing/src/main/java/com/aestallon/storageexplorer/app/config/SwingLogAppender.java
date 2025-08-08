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

package com.aestallon.storageexplorer.app.config;


import com.aestallon.storageexplorer.swing.ui.commander.console.ConsoleView;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.UnsynchronizedAppenderBase;

public class SwingLogAppender extends UnsynchronizedAppenderBase<ILoggingEvent> {

  private static ConsoleView consoleView;

  public static void setLogViewer(ConsoleView view) {
    consoleView = view;
  }

  private PatternLayoutEncoder encoder;
  
  public void setEncoder(PatternLayoutEncoder encoder) {
    this.encoder = encoder;
  }

  @Override
  protected void append(ILoggingEvent event) {
    if (consoleView != null) {
      consoleView.appendLog(encoder.getLayout().doLayout(event));
    }
  }
}

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

import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import com.aestallon.storageexplorer.swing.ui.commander.console.ConsoleView;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;

@Component
public class SwingLogLevelChanger implements ConsoleView.LogLevelChanger {

  public SwingLogLevelChanger(ConsoleView consoleView) {
    consoleView.setLogLevelChanger(this);
    SwingLogAppender.setLogViewer(consoleView);
  }

  @Override
  public void updateLogLevel(String level) {
    final LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
    final Level newLevel = switch (level) {
      case "TRACE" -> Level.TRACE;
      case "DEBUG" -> Level.DEBUG;
      case "WARN" -> Level.WARN;
      case "ERROR" -> Level.ERROR;
      case "OFF" -> Level.OFF;
      default -> Level.INFO;
    };

    final String[] targetPackages = {
        "com.aestallon",
        "org.smartbit4all",
        "org.springframework"
    };

    // Update each target logger
    for (String pkg : targetPackages) {
      final Logger logger = loggerContext.getLogger(pkg);
      logger.setLevel(newLevel);
    }
  }

}

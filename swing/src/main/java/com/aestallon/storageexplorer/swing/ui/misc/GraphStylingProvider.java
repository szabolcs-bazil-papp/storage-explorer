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

package com.aestallon.storageexplorer.swing.ui.misc;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StreamUtils;

public class GraphStylingProvider {

  private static final Logger log = LoggerFactory.getLogger(GraphStylingProvider.class);

  private GraphStylingProvider() {}

  private static String loadStylesheet(String loc) {
    try (final var in = GraphStylingProvider.class.getResourceAsStream(loc)) {
      if (in == null) {
        return "";
      }

      return StreamUtils.copyToString(in, StandardCharsets.UTF_8);
    } catch (IOException e) {
      log.error(e.getMessage(), e);
      return "";
    }
  }

  public static final String LIGHT = loadStylesheet("/styles.css");
  public static final String DARK = loadStylesheet("/styles-dark.css");

}

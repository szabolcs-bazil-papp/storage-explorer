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

package com.aestallon.storageexplorer.client.graph.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import com.aestallon.storageexplorer.core.model.entry.StorageEntry;

public final class NodeColours {

  private static final Map<String, String> COLOURS = new ConcurrentHashMap<>();

  private NodeColours() {}

  public static String ofType(final StorageEntry entry) {
    return COLOURS.computeIfAbsent(entry.toString(), NodeColours::stringToColour);
  }

  public static String ofSchema(final StorageEntry entry) {
    return COLOURS.computeIfAbsent(entry.uri().getScheme(), NodeColours::stringToColour);
  }

  public static String ofString(final String string) {
    return COLOURS.computeIfAbsent(string, NodeColours::stringToColour);
  }

  private static String stringToColour(String input) {
    final int hash = input.hashCode();

    final int r = (hash & 0xFF0000) >> 16;
    final int g = (hash & 0x00FF00) >> 8;
    final int b = hash & 0x0000FF;

    return String.format("%02X%02X%02XFF", r, g, b);
  }
}

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

package com.aestallon.storageexplorer.client.graph.service.internal;

import java.util.HashMap;
import java.util.Map;
import org.graphstream.graph.Edge;
import org.graphstream.graph.Node;

public class AttributeMap {

  private final Map<String, Map<String, String>> inner;

  public AttributeMap() { inner = new HashMap<>(); }

  public void set(Node node, String key, String val) {
    set(node.getId(), key, val);
  }

  public void set(Edge edge, String key, String val) {
    set(edge.getId(), key, val);
  }

  private void set(String id, String key, String val) {
    inner.computeIfAbsent(id, k -> new HashMap<>()).put(key, val);
  }

  public String get(Node node, String key) {
    return get(node.getId(), key);
  }

  public String get(Edge edge, String key) {
    return get(edge.getId(), key);
  }

  private String get(String id, String key) {
    final var attributes = inner.get(id);
    if (attributes == null) {
      return null;
    }

    return attributes.get(key);
  }

}

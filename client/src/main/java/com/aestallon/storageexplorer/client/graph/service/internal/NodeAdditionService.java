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

package com.aestallon.storageexplorer.client.graph.service.internal;

import java.net.URI;
import java.util.Set;
import static java.util.stream.Collectors.joining;
import org.graphstream.graph.Edge;
import org.graphstream.graph.Graph;
import org.graphstream.graph.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.aestallon.storageexplorer.common.util.Attributes;
import com.aestallon.storageexplorer.common.util.MsgStrings;
import com.aestallon.storageexplorer.common.util.Uris;
import com.aestallon.storageexplorer.core.model.entry.StorageEntry;
import com.aestallon.storageexplorer.core.model.entry.UriProperty;

public class NodeAdditionService {

  private static final Logger log = LoggerFactory.getLogger(NodeAdditionService.class);

  private static String stringKey(URI uri) {
    return Uris.latest(uri).toString();
  }

  private static String edgeKey(URI from, URI to) {
    return stringKey(from) + "-ref-" + stringKey(to);
  }

  public static boolean edgeMissing(Graph graph, StorageEntry from, StorageEntry to) {
    return edgeMissing(graph, from.uri(), to.uri());
  }

  private static boolean edgeMissing(Graph graph, URI from, URI to) {
    return graph.getEdge(edgeKey(from, to)) == null;
  }

  public static boolean containsNode(Graph graph, StorageEntry storageEntry) {
    return containsNode(graph, storageEntry.uri());
  }

  public static Node getNode(Graph graph, StorageEntry storageEntry) {
    if (storageEntry == null) {
      return null;
    }

    return graph.getNode(stringKey(storageEntry.uri()));
  }

  private static boolean containsNode(Graph graph, URI uri) {
    return graph.getNode(stringKey(uri)) != null;
  }

  
  private final AttributeMap attributeMap;

  public NodeAdditionService(AttributeMap attributeMap) {
    this.attributeMap = attributeMap;
  }

  public void add(Graph graph, StorageEntry from, StorageEntry to, Set<UriProperty> on) {
    if (!edgeMissing(graph, from, to)) {
      return;
    }

    final Node fromNode = getOrAddNode(graph, from);
    final Node toNode = getOrAddNode(graph, to);

    final String edgeKey = edgeKey(from.uri(), to.uri());
    final Edge edge = graph.addEdge(
        edgeKey,
        fromNode.getId(),
        toNode.getId(),
        true);
    edge.setAttribute(
        Attributes.LABEL,
        MsgStrings.trim(
            on.stream().map(UriProperty::label).collect(joining(" | ")),
            15));
    attributeMap.set(edge, Attributes.WEIGHT, String.valueOf(on.size()));
    if (on.stream().noneMatch(UriProperty::isStandalone)) {
      edge.setAttribute(Attributes.STYLE_CLASS, "listref");
    }
    edge.setAttribute(
        Attributes.INLINE_STYLE, 
        "size: %dpx;".formatted((int) Math.ceil(((double) on.size()) / 2)));
  }

  private Node getOrAddNode(Graph graph, StorageEntry storageEntry) {
    final Node node = graph.getNode(stringKey(storageEntry.uri()));
    if (node != null) {
      return node;
    }
    return add(graph, storageEntry);
  }

  Node add(Graph graph, StorageEntry storageEntry) {
    final var strKey = stringKey(storageEntry.uri());
    final Node graphNode = graph.addNode(strKey);
    graphNode.setAttribute(Attributes.LABEL, storageEntry.toString());
    attributeMap.set(graphNode, Attributes.TYPE_NAME, StorageEntry.typeNameOf(storageEntry));
    return graphNode;
  }

  public void addOrigin(Graph graph, StorageEntry storageEntry) {
    final Node node = add(graph, storageEntry);
    node.setAttribute(Attributes.STYLE_CLASS, "origin");
  }

}

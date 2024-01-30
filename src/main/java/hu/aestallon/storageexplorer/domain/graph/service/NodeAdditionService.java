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

package hu.aestallon.storageexplorer.domain.graph.service;

import java.net.URI;
import java.util.Map;
import java.util.Set;
import org.graphstream.graph.Edge;
import org.graphstream.graph.Graph;
import org.graphstream.graph.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import hu.aestallon.storageexplorer.domain.storage.model.StorageEntry;
import hu.aestallon.storageexplorer.domain.storage.model.UriProperty;
import hu.aestallon.storageexplorer.util.Attributes;
import hu.aestallon.storageexplorer.util.Uris;
import static java.util.stream.Collectors.joining;

@Service
public class NodeAdditionService {

  private static final Logger log = LoggerFactory.getLogger(NodeAdditionService.class);

  private static String stringKey(URI uri) {
    return Uris.latest(uri).toString();
  }

  private static String edgeKey(URI from, URI to) {
    return stringKey(from) + "-ref-" + stringKey(to);
  }

  static boolean edgeMissing(Graph graph, StorageEntry from, StorageEntry to) {
    return edgeMissing(graph, from.uri(), to.uri());
  }

  private static boolean edgeMissing(Graph graph, URI from, URI to) {
    return graph.getEdge(edgeKey(from, to)) == null;
  }

  static boolean containsNode(Graph graph, StorageEntry storageEntry) {
    return containsNode(graph, storageEntry.uri());
  }

  private static boolean containsNode(Graph graph, URI uri) {
    return graph.getNode(stringKey(uri)) != null;
  }


  public NodeAdditionService() {}

  void add(Graph graph, StorageEntry from, StorageEntry to, Set<UriProperty> on) {
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
        on.stream().map(UriProperty::label).collect(joining(" | ")));
    if (on.stream().noneMatch(UriProperty::isStandalone)) {
      edge.setAttribute(Attributes.STYLE_CLASS, "listref");
    }
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
    graphNode.setAttributes(Map.of(
        Attributes.LABEL, storageEntry.toString(),
        Attributes.TYPE_NAME, StorageEntry.typeNameOf(storageEntry),
        Attributes.STORAGE_ENTRY, storageEntry));
    return graphNode;
  }

  public void addOrigin(Graph graph, StorageEntry storageEntry) {
    final Node node = add(graph, storageEntry);
    node.setAttribute(Attributes.STYLE_CLASS, "origin");
  }

}

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

package hu.aestallon.storageexplorer.service;

import java.net.URI;
import java.util.Map;
import org.graphstream.graph.Edge;
import org.graphstream.graph.Graph;
import org.graphstream.graph.Node;
import org.smartbit4all.core.object.ObjectApi;
import org.smartbit4all.core.object.ObjectNode;
import org.springframework.stereotype.Service;
import com.google.common.base.Strings;
import hu.aestallon.storageexplorer.service.internal.UriProperty;
import hu.aestallon.storageexplorer.util.Attributes;
import hu.aestallon.storageexplorer.util.Uris;

@Service
public class NodeAdditionService {


  static String stringKey(URI uri) {
    return Uris.latest(uri).toString();
  }

  static String edgeKey(URI from, URI to) {
    return stringKey(from) + "-ref-" + stringKey(to);
  }

  static boolean edgeMissing(Graph graph, URI from, URI to) {
    return graph.getEdge(edgeKey(from, to)) == null;
  }

  static boolean containsNode(Graph graph, URI uri) {
    return graph.getNode(stringKey(uri)) != null;
  }

  private static String getTypeNameFromQualifiedName(String qualifiedName) {
    final var arr = qualifiedName.split("\\.");
    return arr[arr.length - 1];
  }

  private final ObjectApi objectApi;

  public NodeAdditionService(ObjectApi objectApi) {
    this.objectApi = objectApi;
  }

  public void add(Graph graph, URI from, UriProperty to) {
    if (!edgeMissing(graph, from, to.uri)) {
      return;
    }

    final Node fromNode = getOrAddNode(graph, from);
    final Node toNode = getOrAddNode(graph, to.uri);
    if (fromNode == null || toNode == null || to.uri == null || !Strings.isNullOrEmpty(to.uri.getFragment())) {
      return;
    }

    final String edgeKey = edgeKey(from, to.uri);
    final Edge edge = graph.addEdge(
        edgeKey,
        fromNode.getId(),
        toNode.getId(),
        true);
    edge.setAttribute(Attributes.LABEL, to.label());
    if (!to.isStandalone()) {
      edge.setAttribute(Attributes.STYLE_CLASS, "listref");
    }
  }

  private Node getOrAddNode(Graph graph, URI uri) {
    final Node node = graph.getNode(stringKey(uri));
    if (node != null) {
      return node;
    }
    try {
      return add(graph, uri);
    } catch (Throwable t) {
      return null;
    }
  }

  public Node add(Graph graph, URI uri) {
    final var strKey = stringKey(uri);
    final Node graphNode = graph.addNode(strKey);
    final ObjectNode objectNode = objectApi.load(uri);
    final String qualifiedName = objectNode.getData().getQualifiedName();

    graphNode.setAttributes(Map.of(
        Attributes.LABEL, qualifiedName,
        Attributes.OBJECT_AS_MAP, objectNode.getObjectAsMap(),
        Attributes.NODE_DATA, objectNode.getData(),
        Attributes.TYPE_NAME, getTypeNameFromQualifiedName(qualifiedName)));
    return graphNode;
  }

  public void addOrigin(Graph graph, URI uri) {
    final Node node = add(graph, uri);
    node.setAttribute(Attributes.STYLE_CLASS, "origin");
  }

}

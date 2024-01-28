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
import java.util.Collections;
import java.util.Set;
import java.util.function.BiPredicate;
import org.graphstream.graph.Graph;
import org.smartbit4all.core.object.ObjectApi;
import org.smartbit4all.core.object.ObjectNode;
import org.springframework.stereotype.Service;
import hu.aestallon.storageexplorer.util.GraphContainmentPredicate;
import hu.aestallon.storageexplorer.util.ObjectMaps;
import hu.aestallon.storageexplorer.util.Pair;
import hu.aestallon.storageexplorer.util.Uris;
import static java.util.stream.Collectors.toSet;

@Service
public class OutgoingEdgeDiscoveryService {

  private static final GraphContainmentPredicate ANY_CONNECTION = (g, u) -> true;
  private final ObjectApi objectApi;

  public OutgoingEdgeDiscoveryService(ObjectApi objectApi) {
    this.objectApi = objectApi;
  }

  public Set<UriProperty> execute(Graph graph, URI uri) {
    return findConnectionsSatisfying(graph, uri, ANY_CONNECTION);
  }

  public Set<UriProperty> findConnectionsWithExistingNodes(Graph graph, URI uri) {
    return findConnectionsSatisfying(graph, uri, NodeAdditionService::containsNode);
  }

  private Set<UriProperty> findConnectionsSatisfying(Graph graph, URI uri,
      GraphContainmentPredicate condition) {
    final ObjectNode node;
    try {
      node = objectApi.load(uri);
    } catch (Throwable t) {
      return Collections.emptySet();
    }
    return ObjectMaps
        .flatten(node.getObjectAsMap())
        .entrySet().stream()
        .filter(it -> !UriProperty.OWN.equals(it.getKey()))
        .map(Pair::of)
        .map(Pair.onB(Uris::parse))
        .flatMap(Pair.streamOnB())
        .filter(it -> condition.test(graph, it.b()))
        .map(it -> UriProperty.parse(it.a(), it.b()))
        .filter(it -> NodeAdditionService.edgeMissing(graph, uri, it.uri))
        .collect(toSet());
  }

}

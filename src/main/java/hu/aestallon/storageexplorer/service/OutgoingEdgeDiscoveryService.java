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
import java.util.stream.Collectors;
import org.graphstream.graph.Graph;
import org.springframework.stereotype.Service;
import hu.aestallon.storageexplorer.service.internal.StorageIndex;
import hu.aestallon.storageexplorer.service.internal.UriProperty;
import hu.aestallon.storageexplorer.util.GraphContainmentPredicate;
import hu.aestallon.storageexplorer.util.Pair;
import hu.aestallon.storageexplorer.util.Uris;

@Service
public class OutgoingEdgeDiscoveryService {

  private static final GraphContainmentPredicate ANY_CONNECTION = (g, u) -> true;

  private final StorageIndex storageIndex;

  public OutgoingEdgeDiscoveryService(StorageIndex storageIndex) {
    this.storageIndex = storageIndex;
  }

  public Set<UriProperty> execute(Graph graph, URI uri) {
    return findConnectionsSatisfying(graph, uri, ANY_CONNECTION);
  }

  public Set<UriProperty> findConnectionsWithExistingNodes(Graph graph, URI uri) {
    return findConnectionsSatisfying(graph, uri, NodeAdditionService::containsNode);
  }

  private Set<UriProperty> findConnectionsSatisfying(Graph graph, URI uri,
                                                     GraphContainmentPredicate condition) {
    return storageIndex.refs()
        .filter(pair -> Uris.equalIgnoringVersion(pair.a().uri(), uri))
        .findFirst()
        .stream()
        .flatMap(it -> it.b().stream())
        .filter(it -> condition.test(graph, it.uri))
        .filter(it -> NodeAdditionService.edgeMissing(graph, uri, it.uri))
        .filter(it -> storageIndex.get(it.uri).isPresent())
        .collect(Collectors.toSet());
  }

}

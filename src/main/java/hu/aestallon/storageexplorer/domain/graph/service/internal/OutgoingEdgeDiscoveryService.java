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

package hu.aestallon.storageexplorer.domain.graph.service.internal;

import java.util.Set;
import java.util.stream.Stream;
import org.graphstream.graph.Graph;
import hu.aestallon.storageexplorer.domain.storage.model.StorageEntry;
import hu.aestallon.storageexplorer.domain.storage.model.UriProperty;
import hu.aestallon.storageexplorer.domain.storage.service.StorageIndex;
import hu.aestallon.storageexplorer.util.GraphContainmentPredicate;
import hu.aestallon.storageexplorer.util.Pair;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toSet;

public class OutgoingEdgeDiscoveryService {

  private static final GraphContainmentPredicate ANY_CONNECTION = (g, se) -> true;

  private final StorageIndex storageIndex;

  public OutgoingEdgeDiscoveryService(StorageIndex storageIndex) {
    this.storageIndex = storageIndex;
  }

  public Stream<Pair<StorageEntry, Set<UriProperty>>> execute(Graph graph,
                                                              StorageEntry storageEntry) {
    return findConnectionsSatisfying(graph, storageEntry, ANY_CONNECTION);
  }

  public Stream<Pair<StorageEntry, Set<UriProperty>>> findConnectionsWithExistingNodes(Graph graph,
                                                                                       StorageEntry storageEntry) {
    return findConnectionsSatisfying(graph, storageEntry, NodeAdditionService::containsNode);
  }

  private Stream<Pair<StorageEntry, Set<UriProperty>>> findConnectionsSatisfying(Graph graph,
                                                                                 StorageEntry storageEntry,
                                                                                 GraphContainmentPredicate condition) {
    return storageEntry.uriProperties().stream()
        .map(it -> Pair.of(storageIndex.get(it.uri), it))
        .flatMap(Pair.streamOnA())
        .filter(it -> condition.test(graph, it.a()))
        .filter(it -> NodeAdditionService.edgeMissing(graph, storageEntry, it.a()))
        .collect(groupingBy(Pair::a, mapping(Pair::b, toSet())))
        .entrySet().stream()
        .map(Pair::of);
  }

}

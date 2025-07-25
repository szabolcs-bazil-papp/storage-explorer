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

import java.util.Set;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toSet;
import java.util.stream.Stream;
import org.graphstream.graph.Graph;
import com.aestallon.storageexplorer.client.graph.GraphContainmentPredicate;
import com.aestallon.storageexplorer.client.userconfig.model.GraphSettings;
import com.aestallon.storageexplorer.common.util.Pair;
import com.aestallon.storageexplorer.core.model.entry.StorageEntry;
import com.aestallon.storageexplorer.core.model.entry.UriProperty;
import com.aestallon.storageexplorer.core.model.instance.StorageInstance;

public class OutgoingEdgeDiscoveryService {


  private final StorageInstance storageInstance;
  private final GraphContainmentPredicate inclusionCriterion;
  private final boolean discover;

  public OutgoingEdgeDiscoveryService(StorageInstance storageInstance, GraphSettings settings) {
    this.storageInstance = storageInstance;
    this.inclusionCriterion = GraphContainmentPredicate.whiteListBlackListPredicate(settings);
    this.discover = settings.getAggressiveDiscovery();
  }

  public Stream<Pair<StorageEntry, Set<UriProperty>>> execute(Graph graph,
                                                              StorageEntry storageEntry) {
    return findConnectionsSatisfying(graph, storageEntry, inclusionCriterion);
  }

  public Stream<Pair<StorageEntry, Set<UriProperty>>> findConnectionsWithExistingNodes(Graph graph,
                                                                                       StorageEntry storageEntry) {
    return findConnectionsSatisfying(graph, storageEntry, NodeAdditionService::containsNode);
  }

  private Stream<Pair<StorageEntry, Set<UriProperty>>> findConnectionsSatisfying(Graph graph,
                                                                                 StorageEntry storageEntry,
                                                                                 GraphContainmentPredicate condition) {
    // we can safely call StorageEntry::uriProperties here, because it is guaranteed to be either a
    // valid ObjectEntry or non-object -> we avoid every non-managed load possible
    return storageEntry.uriProperties().stream()
        .map(it -> Pair.of(
            discover ? storageInstance.discover(it.uri) : storageInstance.index().get(it.uri),
            it))
        .flatMap(Pair.streamOnA())
        .filter(it -> condition.test(graph, it.a()))
        .filter(it -> NodeAdditionService.edgeMissing(graph, storageEntry, it.a()))
        .collect(groupingBy(Pair::a, mapping(Pair::b, toSet())))
        .entrySet().stream()
        .map(Pair::of);
  }

}

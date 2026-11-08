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

import java.util.Optional;
import java.util.Set;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toSet;
import java.util.stream.Stream;
import org.graphstream.graph.Graph;
import com.aestallon.storageexplorer.client.graph.GraphContainmentPredicate;
import com.aestallon.storageexplorer.client.userconfig.model.GraphSettings;
import com.aestallon.storageexplorer.common.util.Pair;
import com.aestallon.storageexplorer.core.model.EntryUri;
import com.aestallon.storageexplorer.core.model.entry.StorageEntry;
import com.aestallon.storageexplorer.core.model.entry.UriProperty;
import com.aestallon.storageexplorer.core.model.instance.StorageInstance;
import com.aestallon.storageexplorer.core.service.index.AdjacencyAware;
import com.aestallon.storageexplorer.core.service.index.result.AdjacencyResult;
import com.aestallon.storageexplorer.core.service.index.result.EntryResolution;

/**
 * Discovers {@code storageEntry}'s outgoing references via a held, operation-scoped
 * {@link AdjacencyAware} index (one per graph render, per {@code GraphRenderingService}'s own
 * lifecycle) — replaces the removed {@code StorageEntry#uriProperties()} (T4.1). Resolution of a
 * referenced address is always a fresh lookup now (there's no persistent "already discovered"
 * cache left to check against), so the old {@code aggressiveDiscovery}/{@code discover} vs.
 * {@code index.get} distinction no longer means anything — {@link GraphSettings
 * #getAggressiveDiscovery()} is accepted but has no effect (same kind of now-vestigial setting as
 * {@code ArcScriptEngine}'s own {@code index._strategy}, T5.1).
 */
public class OutgoingEdgeDiscoveryService {


  private final StorageInstance storageInstance;
  private final GraphContainmentPredicate inclusionCriterion;

  public OutgoingEdgeDiscoveryService(StorageInstance storageInstance, GraphSettings settings) {
    this.storageInstance = storageInstance;
    this.inclusionCriterion = GraphContainmentPredicate.whiteListBlackListPredicate(settings);
  }

  public Stream<Pair<StorageEntry, Set<UriProperty>>> execute(Graph graph,
                                                              StorageEntry storageEntry,
                                                              AdjacencyAware index) {
    return findConnectionsSatisfying(graph, storageEntry, index, inclusionCriterion);
  }

  public Stream<Pair<StorageEntry, Set<UriProperty>>> findConnectionsWithExistingNodes(Graph graph,
                                                                                       StorageEntry storageEntry,
                                                                                       AdjacencyAware index) {
    return findConnectionsSatisfying(graph, storageEntry, index, NodeAdditionService::containsNode);
  }

  private Stream<Pair<StorageEntry, Set<UriProperty>>> findConnectionsSatisfying(Graph graph,
                                                                                 StorageEntry storageEntry,
                                                                                 AdjacencyAware index,
                                                                                 GraphContainmentPredicate condition) {
    final Set<UriProperty> refs = switch (index.outgoingReferences(storageEntry)) {
      case AdjacencyResult.Ok(var e, var r) -> r;
      case AdjacencyResult.Err err -> Set.<UriProperty>of();
    };
    return refs.stream()
        .map(it -> Pair.of(resolve(it.uri, index), it))
        .flatMap(Pair.streamOnA())
        .filter(it -> condition.test(graph, it.a()))
        .filter(it -> NodeAdditionService.edgeMissing(graph, storageEntry, it.a()))
        .collect(groupingBy(Pair::a, mapping(Pair::b, toSet())))
        .entrySet().stream()
        .map(Pair::of);
  }

  private Optional<StorageEntry> resolve(final java.net.URI uri, final AdjacencyAware index) {
    return EntryUri.parse(uri.toString())
        .map(index::resolve)
        .flatMap(res -> switch (res) {
          case EntryResolution.Known(var entry) -> Optional.of(entry);
          case EntryResolution.Unknown u -> Optional.<StorageEntry>empty();
        });
  }

}

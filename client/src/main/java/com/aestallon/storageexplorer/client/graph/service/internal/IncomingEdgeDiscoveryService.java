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
import java.util.stream.Stream;
import org.graphstream.graph.Graph;
import com.aestallon.storageexplorer.client.graph.GraphContainmentPredicate;
import com.aestallon.storageexplorer.client.userconfig.model.GraphSettings;
import com.aestallon.storageexplorer.core.model.EntryUri;
import com.aestallon.storageexplorer.core.model.entry.StorageEntry;
import com.aestallon.storageexplorer.core.model.entry.UriProperty;
import com.aestallon.storageexplorer.core.model.instance.StorageInstance;
import com.aestallon.storageexplorer.core.model.loading.IndexingTarget;
import com.aestallon.storageexplorer.core.service.index.AdjacencyAware;
import com.aestallon.storageexplorer.core.service.index.result.AdjacencyResult;

/**
 * Discovers entries referencing {@code storageEntry} — the reverse of {@link
 * OutgoingEdgeDiscoveryService}. Forward-only adjacency is this rework's explicit scope (decisions
 * log §8): there is no index to answer "who references this entry" directly, so this now means an
 * explicit, visibly-slow full-storage scan (every entry's outgoing references are checked against
 * {@code storageEntry}'s address) rather than a reverse-lookup cache — a deliberate choice over
 * silently disabling the GUI's "render incoming references" action (§10's own framing: worst case
 * this is just slow, not missing).
 */
public class IncomingEdgeDiscoveryService {

  private final StorageInstance storageInstance;
  private final GraphContainmentPredicate inclusionCriterion;

  public IncomingEdgeDiscoveryService(StorageInstance storageInstance, GraphSettings settings) {
    this.storageInstance = storageInstance;
    this.inclusionCriterion = GraphContainmentPredicate.whiteListBlackListPredicate(settings);
  }

  public Stream<StorageEntry> execute(Graph graph, StorageEntry storageEntry, AdjacencyAware index) {
    final EntryUri target = storageEntry.uri();
    final Set<StorageEntry> candidates;
    try (Stream<StorageEntry> all = storageInstance.query(IndexingTarget.any())) {
      candidates = all
          .filter(it -> inclusionCriterion.test(graph, it))
          .filter(it -> referencesTarget(it, target, index))
          .filter(it -> NodeAdditionService.edgeMissing(graph, it, storageEntry))
          .collect(java.util.stream.Collectors.toSet());
    }
    return candidates.stream();
  }

  private boolean referencesTarget(final StorageEntry candidate, final EntryUri target,
                                   final AdjacencyAware index) {
    return switch (index.outgoingReferences(candidate)) {
      case AdjacencyResult.Ok(var e, var refs) -> refs.stream()
          .map(UriProperty::uri)
          .map(u -> EntryUri.parse(u.toString()))
          .flatMap(java.util.Optional::stream)
          .anyMatch(u -> u.latest().equals(target.latest()));
      case AdjacencyResult.Err err -> false;
    };
  }

}

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
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import org.graphstream.graph.Graph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import hu.aestallon.storageexplorer.domain.storage.model.StorageEntry;
import hu.aestallon.storageexplorer.domain.storage.model.UriProperty;
import hu.aestallon.storageexplorer.domain.storage.service.StorageIndex;
import hu.aestallon.storageexplorer.util.Pair;
import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

@Service
public class GraphRenderingService {

  private static final Logger log = LoggerFactory.getLogger(GraphRenderingService.class);

  private final NodeAdditionService nodeAdditionService;
  private final IncomingEdgeDiscoveryService incomingEdgeDiscoveryService;
  private final OutgoingEdgeDiscoveryService outgoingEdgeDiscoveryService;
  private final StorageIndex storageIndex;
  private final int graphTraversalLimit;

  public GraphRenderingService(NodeAdditionService nodeAdditionService,
                               IncomingEdgeDiscoveryService incomingEdgeDiscoveryService,
                               OutgoingEdgeDiscoveryService outgoingEdgeDiscoveryService,
                               StorageIndex storageIndex,
                               @Value("${graph.traversal.limit:-1}") int graphTraversalLimit) {
    this.nodeAdditionService = nodeAdditionService;
    this.incomingEdgeDiscoveryService = incomingEdgeDiscoveryService;
    this.outgoingEdgeDiscoveryService = outgoingEdgeDiscoveryService;
    this.storageIndex = storageIndex;
    this.graphTraversalLimit = graphTraversalLimit;
  }

  public void render(Graph graph, StorageEntry storageEntry) {
    if (!NodeAdditionService.containsNode(graph, storageEntry)) {
      nodeAdditionService.addOrigin(graph, storageEntry);
    }

    renderOutgoingReferences(graph, storageEntry);
    renderIncomingReferences(graph, storageEntry);
  }

  private void renderOutgoingReferences(Graph graph, StorageEntry storageEntry) {
    Map<StorageEntry, Set<Pair<StorageEntry, Set<UriProperty>>>> refs = outgoingEdgeDiscoveryService
        .execute(graph, storageEntry)
        .collect(collectingAndThen(toSet(), s -> Map.of(storageEntry, s)));
    int c = 0;
    do {
      log.info("OUTGOING REFERENCES: [ {} ]", refs);
      refs.forEach(
          (from, tos) -> tos.forEach(it -> nodeAdditionService.add(graph, from, it.a(), it.b())));
      refs = refs.values().stream()
          .flatMap(Set::stream)
          .map(Pair::a)
          .distinct()
          .collect(toMap(
              Function.identity(),
              it -> outgoingEdgeDiscoveryService
                  .execute(graph, it)
                  .collect(toSet())));
    } while ((++c < graphTraversalLimit || graphTraversalLimit < 1) && hasValues(refs));
  }

  private void renderIncomingReferences(Graph graph, StorageEntry storageEntry) {
    Map<StorageEntry, Set<StorageEntry>> referrers = Map.of(
        storageEntry,
        incomingEdgeDiscoveryService.execute(graph, storageEntry).
            collect(toSet()));
    int c = 0;
    do {
      log.info("INCOMING REFERENCES: [ {} ]", referrers);
      referrers.values().stream()
          .flatMap(Set::stream)
          .distinct()
          .forEach(it -> outgoingEdgeDiscoveryService
              .findConnectionsWithExistingNodes(graph, it)
              .forEach(target -> nodeAdditionService.add(graph, it, target.a(), target.b())));
      referrers = referrers.values().stream()
          .flatMap(Set::stream)
          .distinct()
          .collect(toMap(
              Function.identity(),
              it -> incomingEdgeDiscoveryService
                  .execute(graph, it)
                  .filter(r -> NodeAdditionService.edgeMissing(graph, r, it))
                  .collect(toSet())));
    } while ((++c < graphTraversalLimit || graphTraversalLimit < 1) && hasValues(referrers));
  }

  private static boolean hasValues(Map<?, ? extends Set<?>> m) {
    return m.values().stream().flatMap(Set::stream).findAny().isPresent();
  }

  public Optional<StorageEntry> getStorageEntry(String uriString) {
    return getStorageEntry(URI.create(uriString));
  }

  public Optional<StorageEntry> getStorageEntry(URI uri) {
    return storageIndex.get(uri);
  }

}

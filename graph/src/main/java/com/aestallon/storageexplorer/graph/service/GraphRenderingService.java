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

package com.aestallon.storageexplorer.graph.service;

import java.net.URI;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import org.graphstream.graph.Graph;
import org.graphstream.graph.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.aestallon.storageexplorer.common.util.Attributes;
import com.aestallon.storageexplorer.common.util.Pair;
import com.aestallon.storageexplorer.core.model.entry.StorageEntry;
import com.aestallon.storageexplorer.core.model.entry.UriProperty;
import com.aestallon.storageexplorer.core.model.instance.StorageInstance;
import com.aestallon.storageexplorer.graph.service.internal.IncomingEdgeDiscoveryService;
import com.aestallon.storageexplorer.graph.service.internal.NodeAdditionService;
import com.aestallon.storageexplorer.graph.service.internal.OutgoingEdgeDiscoveryService;
import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

public final class GraphRenderingService {

  private static final Logger log = LoggerFactory.getLogger(GraphRenderingService.class);

  private final StorageInstance storageInstance;
  private int inboundLimit;
  private int outboundLimit;

  private final IncomingEdgeDiscoveryService incomingEdgeDiscoveryService;
  private final OutgoingEdgeDiscoveryService outgoingEdgeDiscoveryService;
  private final NodeAdditionService nodeAdditionService;

  public GraphRenderingService(StorageInstance storageInstance, int inboundLimit,
                               int outboundLimit) {
    this.storageInstance = storageInstance;
    this.inboundLimit = inboundLimit;
    this.outboundLimit = outboundLimit;

    incomingEdgeDiscoveryService = new IncomingEdgeDiscoveryService(storageInstance);
    outgoingEdgeDiscoveryService = new OutgoingEdgeDiscoveryService(storageInstance);
    nodeAdditionService = new NodeAdditionService();
  }

  public void setLimits(final int inboundLimit, final int outboundLimit) {
    this.inboundLimit = inboundLimit;
    this.outboundLimit = outboundLimit;
  }

  public void render(Graph graph, StorageEntry storageEntry) {
    if (!NodeAdditionService.containsNode(graph, storageEntry)) {
      nodeAdditionService.addOrigin(graph, storageEntry);
    }

    if (outboundLimit != 0) {
      renderOutgoingReferences(graph, storageEntry);
    }
    if (inboundLimit != 0) {
      renderIncomingReferences(graph, storageEntry);
    }
  }

  public StorageInstance storageInstance() {
    return storageInstance;
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

      final Set<StorageEntry> candidates = refs.values().stream()
          .flatMap(Set::stream)
          .map(Pair::a)
          .collect(toSet());
      storageInstance.validate(candidates);
      refs = candidates.stream()
          .collect(toMap(
              Function.identity(),
              it -> outgoingEdgeDiscoveryService
                  .execute(graph, it)
                  .collect(toSet())));
    } while ((++c < outboundLimit || outboundLimit < 0) && hasValues(refs));
  }

  private void renderIncomingReferences(Graph graph, StorageEntry storageEntry) {
    Map<StorageEntry, Set<StorageEntry>> referrers = Map.of(
        storageEntry,
        incomingEdgeDiscoveryService.execute(graph, storageEntry).
            collect(toSet()));
    int c = 0;
    do {
      log.info("INCOMING REFERENCES: [ {} ]", referrers);
      Set<StorageEntry> candidates = referrers.values().stream()
          .flatMap(Set::stream)
          .collect(toSet());
      storageInstance.validate(candidates);
      candidates.forEach(it -> outgoingEdgeDiscoveryService
          .findConnectionsWithExistingNodes(graph, it)
          .forEach(target -> nodeAdditionService.add(graph, it, target.a(), target.b())));
      referrers = candidates.stream().collect(toMap(
          Function.identity(),
          it -> incomingEdgeDiscoveryService
              .execute(graph, it)
              .filter(r -> NodeAdditionService.edgeMissing(graph, r, it))
              .collect(toSet())));
    } while ((++c < inboundLimit || inboundLimit < 1) && hasValues(referrers));
  }

  private static boolean hasValues(Map<?, ? extends Set<?>> m) {
    return m.values().stream().flatMap(Set::stream).findAny().isPresent();
  }

  public Optional<StorageEntry> getStorageEntry(String uriString) {
    return getStorageEntry(URI.create(uriString));
  }

  public Optional<StorageEntry> getStorageEntry(URI uri) {
    return storageInstance.index().get(uri);
  }

  public void changeHighlight(final Graph graph, final StorageEntry from, final StorageEntry to) {
    Node fromNode = NodeAdditionService.getNode(graph, from);
    if (fromNode != null) {
      Object attribute = fromNode.getAttribute(Attributes.STYLE_CLASS);
      boolean addOrigin = attribute != null && String.valueOf(attribute).contains("origin");
      fromNode.removeAttribute(Attributes.STYLE_CLASS);
      if (addOrigin)
        fromNode.setAttribute(Attributes.STYLE_CLASS, "origin");
    }

    final Node toNode = NodeAdditionService.getNode(graph, to);
    if (toNode != null) {
      Object attribute = toNode.getAttribute(Attributes.STYLE_CLASS);
      boolean addOrigin = attribute != null && String.valueOf(attribute).contains("origin");
      toNode.setAttribute(Attributes.STYLE_CLASS, addOrigin ? "originhighlighted" : "highlighted");
    }
  }

}

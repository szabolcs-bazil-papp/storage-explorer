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
import java.util.Set;
import java.util.function.Function;
import org.graphstream.graph.Graph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import hu.aestallon.storageexplorer.service.internal.UriProperty;
import static java.util.stream.Collectors.toMap;

@Service
public class GraphRenderingService {

  private static final Logger log = LoggerFactory.getLogger(GraphRenderingService.class);

  private final NodeAdditionService nodeAdditionService;
  private final IncomingEdgeDiscoveryService incomingEdgeDiscoveryService;
  private final OutgoingEdgeDiscoveryService outgoingEdgeDiscoveryService;

  public GraphRenderingService(NodeAdditionService nodeAdditionService,
      IncomingEdgeDiscoveryService incomingEdgeDiscoveryService,
      OutgoingEdgeDiscoveryService outgoingEdgeDiscoveryService) {
    this.nodeAdditionService = nodeAdditionService;
    this.incomingEdgeDiscoveryService = incomingEdgeDiscoveryService;
    this.outgoingEdgeDiscoveryService = outgoingEdgeDiscoveryService;
  }

  public void render(Graph graph, URI uri) {
    nodeAdditionService.addOrigin(graph, uri);
    renderOutgoingReferences(graph, uri);
    renderIncomingReferences(graph, uri);
  }

  private void renderOutgoingReferences(Graph graph, URI uri) {
    Map<URI, Set<UriProperty>> refs = Map.of(
        uri,
        outgoingEdgeDiscoveryService.execute(graph, uri));
    do {
      log.info("OUTGOING REFERENCES: [ {} ]", refs);
      refs.forEach((from, tos) -> tos.forEach(it -> nodeAdditionService.add(graph, from, it)));
      refs = refs.values().stream()
          .flatMap(Set::stream)
          .map(it -> it.uri)
          .distinct()
          .collect(toMap(
              Function.identity(),
              it -> outgoingEdgeDiscoveryService.execute(graph, it)));
    } while (refs.values().stream().flatMap(Set::stream).findAny().isPresent());
  }

  private void renderIncomingReferences(Graph graph, URI uri) {
    Map<URI, Set<URI>> referrers = Map.of(
        uri,
        incomingEdgeDiscoveryService.execute(graph, uri));
    do {
      log.info("INCOMING REFERENCES: [ {} ]", referrers);
      referrers.forEach((to, froms) -> froms.forEach(it -> outgoingEdgeDiscoveryService
          .findConnectionsWithExistingNodes(graph, it)
          .forEach(target -> nodeAdditionService.add(graph, it, target))));
      referrers = referrers.values().stream()
          .flatMap(Set::stream)
          .distinct()
          .filter(it -> !NodeAdditionService.containsNode(graph, it))
          .collect(toMap(
              Function.identity(),
              it -> incomingEdgeDiscoveryService.execute(graph, it)));
    } while (referrers.values().stream().flatMap(Set::stream).findAny().isPresent());
  }

}

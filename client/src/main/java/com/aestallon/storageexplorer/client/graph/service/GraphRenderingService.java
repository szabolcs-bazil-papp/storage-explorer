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

package com.aestallon.storageexplorer.client.graph.service;

import java.net.URI;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;
import org.graphstream.graph.Graph;
import org.graphstream.graph.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.aestallon.storageexplorer.client.graph.service.internal.AttributeMap;
import com.aestallon.storageexplorer.client.graph.service.internal.IncomingEdgeDiscoveryService;
import com.aestallon.storageexplorer.client.graph.service.internal.NodeAdditionService;
import com.aestallon.storageexplorer.client.graph.service.internal.OutgoingEdgeDiscoveryService;
import com.aestallon.storageexplorer.client.graph.service.internal.Styles;
import com.aestallon.storageexplorer.client.userconfig.model.GraphSettings;
import com.aestallon.storageexplorer.common.util.Attributes;
import com.aestallon.storageexplorer.common.util.Pair;
import com.aestallon.storageexplorer.core.model.entry.ObjectEntry;
import com.aestallon.storageexplorer.core.model.entry.StorageEntry;
import com.aestallon.storageexplorer.core.model.entry.UriProperty;
import com.aestallon.storageexplorer.core.model.instance.StorageInstance;

public final class GraphRenderingService {

  private static final Logger log = LoggerFactory.getLogger(GraphRenderingService.class);

  private final StorageInstance storageInstance;
  private final GraphSettings settings;

  private final IncomingEdgeDiscoveryService incomingEdgeDiscoveryService;
  private final OutgoingEdgeDiscoveryService outgoingEdgeDiscoveryService;
  private final NodeAdditionService nodeAdditionService;
  private final AttributeMap attributeMap;

  public GraphRenderingService(StorageInstance storageInstance, GraphSettings settings) {
    this.storageInstance = storageInstance;
    this.settings = settings;

    attributeMap = new AttributeMap();
    incomingEdgeDiscoveryService = new IncomingEdgeDiscoveryService(storageInstance, settings);
    outgoingEdgeDiscoveryService = new OutgoingEdgeDiscoveryService(storageInstance, settings);
    nodeAdditionService = new NodeAdditionService(attributeMap);
  }

  public void render(Graph graph, StorageEntry storageEntry) {
    if (!NodeAdditionService.containsNode(graph, storageEntry)) {
      nodeAdditionService.addOrigin(graph, storageEntry);
    }

    if (settings.getGraphTraversalOutboundLimit() != 0) {
      renderOutgoingReferences(graph, storageEntry);
    }
    if (settings.getGraphTraversalInboundLimit() != 0) {
      renderIncomingReferences(graph, storageEntry);
    }

    styleGraph(graph);
  }

  public StorageInstance storageInstance() {
    return storageInstance;
  }

  private void renderOutgoingReferences(Graph graph, StorageEntry storageEntry) {
    final var limit = settings.getGraphTraversalOutboundLimit();
    Map<StorageEntry, Set<Pair<StorageEntry, Set<UriProperty>>>> refs = outgoingEdgeDiscoveryService
        .execute(graph, storageEntry)
        .collect(collectingAndThen(toSet(), s -> Map.of(storageEntry, s)));
    int c = 0;
    do {
      log.info("OUTGOING REFERENCES: loop enter");
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
    } while (!Thread.currentThread().isInterrupted()
             && (++c < limit || limit < 0)
             && hasValues(refs));
    log.info("OUTGOING REFERENCES: loop exit");
  }

  private void renderIncomingReferences(Graph graph, StorageEntry storageEntry) {
    final var limit = settings.getGraphTraversalInboundLimit();
    Map<StorageEntry, Set<StorageEntry>> referrers = Map.of(
        storageEntry,
        incomingEdgeDiscoveryService.execute(graph, storageEntry).
            collect(toSet()));
    int c = 0;
    do {
      log.info("INCOMING REFERENCES: loop enter");
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
    } while (!Thread.currentThread().isInterrupted()
             && (++c < limit || limit < 1)
             && hasValues(referrers));
    log.info("INCOMING REFERENCES: loop exit");
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
    final Node fromNode = NodeAdditionService.getNode(graph, from);
    if (fromNode != null) {
      final Object attribute = fromNode.getAttribute(Attributes.STYLE_CLASS);
      final boolean addOrigin = attribute != null
                                && String.valueOf(attribute).contains(Styles.ORIGIN);
      fromNode.removeAttribute(Attributes.STYLE_CLASS);
      if (addOrigin) { fromNode.setAttribute(Attributes.STYLE_CLASS, Styles.ORIGIN); }
    }

    final Node toNode = NodeAdditionService.getNode(graph, to);
    if (toNode != null) {
      final Object attribute = toNode.getAttribute(Attributes.STYLE_CLASS);
      final boolean addOrigin = attribute != null
                                && String.valueOf(attribute).contains(Styles.ORIGIN);
      toNode.setAttribute(Attributes.STYLE_CLASS, addOrigin ? "originhighlighted" : "highlighted");
    }
  }

  private void styleGraph(final Graph graph) {
    final var sStrategy = createNodeSizingStrategy(graph, settings.getNodeSizing());
    final var cStrategy = createNodeColouringStrategy(graph, settings.getNodeColouring());
    graph.nodes().forEach(node -> {
      final String sizing = sStrategy.compute(node);
      final String colouring = cStrategy.compute(node);
      if (!colouring.isBlank()) {
        attributeMap.set(node, "colouring", colouring);
      }

      if (!sizing.isBlank() && !colouring.isBlank()) {
        final String inlineStyle = sizing + colouring;
        node.setAttribute(Attributes.INLINE_STYLE, inlineStyle);
      } else {
        node.removeAttribute(Attributes.INLINE_STYLE);
      }
    });
    graph.edges().forEach(edge -> {
      final StringBuilder inlineStyle = new StringBuilder();
      final String weightStr = attributeMap.get(edge, Attributes.WEIGHT);
      if (weightStr != null && !weightStr.isBlank()) {
        inlineStyle.append("size: %dpx;".formatted(
            (int) Math.ceil(((double) Integer.parseInt(weightStr)) / 2)));
      }

      final String colouring = attributeMap.get(edge.getSourceNode(), "colouring");
      if (colouring != null && !colouring.isBlank()) {
        inlineStyle.append(replaceLast(colouring, "FF", "59"));
      }

      if (!inlineStyle.isEmpty()) {
        edge.setAttribute(Attributes.INLINE_STYLE, inlineStyle.toString());
      } else {
        edge.removeAttribute(Attributes.INLINE_STYLE);
      }
    });
  }

  private static String replaceLast(final String s, final String substr, final String replacement) {
    if (substr.length() != replacement.length()) {
      throw new IllegalArgumentException("replacement must be the same length as the substring");
    }

    final int pos = s.lastIndexOf(substr);
    if (pos < 0) {
      return s;
    }

    return s.substring(0, pos) + replacement + s.substring(pos + substr.length());
  }

  private interface NodeStylingStrategy {

    String compute(final Node node);

  }

  private NodeStylingStrategy createNodeSizingStrategy(final Graph graph,
                                                       final GraphSettings.NodeSizing sizing) {
    return switch (sizing) {
      case DEGREE -> new DegreeNodeSizingStrategy(graph);
      case IN_DEGREE -> new InDegreeNodeSizingStrategy(graph);
      case OUT_DEGREE -> new OutDegreeNodeSizingStrategy(graph);
      case VERSION_COUNT -> new VersionCountNodeSizingStrategy(graph);
      case SIZE, UNIFORM -> new UniformNodeSizingStrategy(graph);
    };
  }

  private NodeStylingStrategy createNodeColouringStrategy(final Graph graph,
                                                          final GraphSettings.NodeColouring colour) {
    return switch (colour) {
      case DEGREE -> new DegreeBasedNodeColouringStrategy(graph, Node::getDegree);
      case IN_DEGREE -> new DegreeBasedNodeColouringStrategy(graph, Node::getInDegree);
      case OUT_DEGREE -> new DegreeBasedNodeColouringStrategy(graph, Node::getOutDegree);
      case TYPE -> new TypeNodeColouringStrategy(graph);
      case SCHEMA -> new SchemaNodeColouringStrategy(graph);
      case SIZE, UNIFORM -> new UniformNodeColouringStrategy(graph);
    };
  }

  private abstract static class NodeSizingStrategy implements NodeStylingStrategy {

    protected final Graph graph;

    protected NodeSizingStrategy(final Graph graph) {
      this.graph = graph;
    }

    @Override
    public String compute(Node node) {
      final var size = computeSize(node);
      if (size < 1) {
        return "";
      }

      return "size: %dpx;".formatted(size);
    }

    protected abstract int computeSize(Node node);

  }


  private static class UniformNodeSizingStrategy extends NodeSizingStrategy {

    protected UniformNodeSizingStrategy(Graph graph) {
      super(graph);
    }

    @Override
    protected int computeSize(Node node) {
      return 0;
    }
  }


  protected static class DegreeBasedNodeSizingStrategy extends NodeSizingStrategy {

    private final ToIntFunction<Node> f;
    private final int max;

    protected DegreeBasedNodeSizingStrategy(Graph graph,
                                            ToIntFunction<Node> f) {
      super(graph);
      this.f = f;
      max = graph.nodes()
          .mapToInt(f)
          .max()
          .orElse(0);
    }

    @Override
    protected int computeSize(Node node) {
      return 10 + f.applyAsInt(node) * 3;
    }
  }


  private static final class DegreeNodeSizingStrategy extends DegreeBasedNodeSizingStrategy {
    DegreeNodeSizingStrategy(Graph graph) {
      super(graph, Node::getDegree);
    }
  }


  private static final class OutDegreeNodeSizingStrategy extends DegreeBasedNodeSizingStrategy {
    OutDegreeNodeSizingStrategy(Graph graph) {
      super(graph, Node::getOutDegree);
    }
  }


  private static final class InDegreeNodeSizingStrategy extends DegreeBasedNodeSizingStrategy {
    InDegreeNodeSizingStrategy(Graph graph) {
      super(graph, Node::getInDegree);
    }
  }


  private final class VersionCountNodeSizingStrategy extends NodeSizingStrategy {
    VersionCountNodeSizingStrategy(Graph graph) {
      super(graph);
    }

    @Override
    protected int computeSize(Node node) {
      return getStorageEntry(node.getId()).stream()
          .mapToInt(it -> it instanceof ObjectEntry o
              ? switch (o.versioning()) {
            case ObjectEntry.Versioning.Single s -> 10;
            case ObjectEntry.Versioning.Multi(long head) -> 10 + ((int) head);
          }
              : 10)
          .findFirst()
          .orElse(10);
    }
  }


  private abstract static class NodeColouringStrategy implements NodeStylingStrategy {

    protected final Graph graph;

    protected NodeColouringStrategy(final Graph graph) {
      this.graph = graph;
    }

    @Override
    public String compute(final Node node) {
      final var hexColour = hexColour(node);
      if (hexColour.isEmpty()) {
        return "";
      }

      return "fill-color: #%s;".formatted(hexColour);
    }

    protected abstract String hexColour(final Node node);

  }


  private static final class UniformNodeColouringStrategy extends NodeColouringStrategy {

    UniformNodeColouringStrategy(Graph graph) {
      super(graph);
    }

    @Override
    public String hexColour(Node node) {
      return "";
    }

  }


  private final class TypeNodeColouringStrategy extends NodeColouringStrategy {

    TypeNodeColouringStrategy(Graph graph) {
      super(graph);
    }

    @Override
    public String hexColour(Node node) {
      return getStorageEntry(node.getId())
          .map(NodeColours::ofType)
          .orElse("");
    }

  }


  private final class SchemaNodeColouringStrategy extends NodeColouringStrategy {

    SchemaNodeColouringStrategy(Graph graph) {
      super(graph);
    }

    @Override
    public String hexColour(Node node) {
      return getStorageEntry(node.getId())
          .map(NodeColours::ofSchema)
          .orElse("");
    }

  }


  private static final class DegreeBasedNodeColouringStrategy extends NodeColouringStrategy {

    private final ToIntFunction<Node> f;
    private final int max;

    DegreeBasedNodeColouringStrategy(Graph graph, ToIntFunction<Node> f) {
      super(graph);
      this.f = f;
      max = graph.nodes()
          .mapToInt(f)
          .max()
          .orElse(0);
    }

    @Override
    public String hexColour(Node node) {
      final int v = Math.clamp(f.applyAsInt(node), 0, max);
      final float ratio = (float) v / max;

      final int red = (int) (ratio * 255);
      final int green = 255 - red;
      final int blue = 0;

      return String.format("%02x%02x%02xFF", red, green, blue);
    }

  }

}

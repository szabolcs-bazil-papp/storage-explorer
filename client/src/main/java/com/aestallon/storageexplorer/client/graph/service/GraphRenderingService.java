/*
 * Copyright (C) 2025 Szabolcs Bazil Papp
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

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;
import org.graphstream.graph.Graph;
import org.graphstream.graph.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.aestallon.storageexplorer.client.graph.event.GraphState;
import com.aestallon.storageexplorer.client.graph.service.internal.AttributeMap;
import com.aestallon.storageexplorer.client.graph.service.internal.IncomingEdgeDiscoveryService;
import com.aestallon.storageexplorer.client.graph.service.internal.NodeAdditionService;
import com.aestallon.storageexplorer.client.graph.service.internal.OutgoingEdgeDiscoveryService;
import com.aestallon.storageexplorer.client.graph.service.internal.Styles;
import com.aestallon.storageexplorer.client.userconfig.model.GraphSettings;
import com.aestallon.storageexplorer.common.util.Attributes;
import com.aestallon.storageexplorer.common.util.Pair;
import com.aestallon.storageexplorer.core.model.EntryUri;
import com.aestallon.storageexplorer.core.model.entry.StorageEntry;
import com.aestallon.storageexplorer.core.model.entry.UriProperty;
import com.aestallon.storageexplorer.core.model.instance.StorageInstance;
import com.aestallon.storageexplorer.core.service.index.AdjacencyAware;
import com.aestallon.storageexplorer.core.service.index.MetadataAware;
import com.aestallon.storageexplorer.core.service.index.result.EntryResolution;
import com.aestallon.storageexplorer.core.service.index.result.MetadataResult;

public final class GraphRenderingService {

  private static final Logger log = LoggerFactory.getLogger(GraphRenderingService.class);

  private final StorageInstance storageInstance;
  private final GraphSettings settings;

  private final IncomingEdgeDiscoveryService incomingEdgeDiscoveryService;
  private final OutgoingEdgeDiscoveryService outgoingEdgeDiscoveryService;
  private final NodeAdditionService nodeAdditionService;
  private final AttributeMap attributeMap;
  private final Consumer<GraphState> graphStateListener;

  public GraphRenderingService(StorageInstance storageInstance, GraphSettings settings) {
    this(storageInstance, settings, null);
  }

  public GraphRenderingService(StorageInstance storageInstance,
                               GraphSettings settings,
                               Consumer<GraphState> graphStateListener) {
    this.storageInstance = storageInstance;
    this.settings = settings;
    this.graphStateListener = graphStateListener;

    attributeMap = new AttributeMap();
    incomingEdgeDiscoveryService = new IncomingEdgeDiscoveryService(storageInstance, settings);
    outgoingEdgeDiscoveryService = new OutgoingEdgeDiscoveryService(storageInstance, settings);
    nodeAdditionService = new NodeAdditionService(attributeMap);
  }

  public void render(Graph graph, StorageEntry storageEntry, boolean showLabels) {
    if (!NodeAdditionService.containsNode(graph, storageEntry)) {
      nodeAdditionService.addOrigin(graph, storageEntry);
    }
    reportGraphState(graph);

    // operation-scoped, one per render() call (per StorageInstance.newGraphIndex()'s own
    // javadoc) - shared by the outgoing/incoming discovery services and the version-count sizing
    // strategy below, then discarded:
    try (AdjacencyAware index = storageInstance.newGraphIndex();
         MetadataAware stats = storageInstance.newStatsIndex()) {
      if (settings.getGraphTraversalOutboundLimit() != 0) {
        renderOutgoingReferences(graph, storageEntry, index);
      }
      if (settings.getGraphTraversalInboundLimit() != 0) {
        renderIncomingReferences(graph, storageEntry, index);
      }

      styleGraph(graph, stats);
    }
    showLabels(graph, showLabels);
  }
  
  private void reportGraphState(final Graph graph) {
    if (graphStateListener == null) {
      return;
    }
    
    graphStateListener.accept(new GraphState(graph.getNodeCount(), graph.getEdgeCount()));
  }

  public StorageInstance storageInstance() {
    return storageInstance;
  }

  private void renderOutgoingReferences(Graph graph, StorageEntry storageEntry,
                                        AdjacencyAware index) {
    final var limit = settings.getGraphTraversalOutboundLimit();
    Map<StorageEntry, Set<Pair<StorageEntry, Set<UriProperty>>>> refs = outgoingEdgeDiscoveryService
        .execute(graph, storageEntry, index)
        .collect(collectingAndThen(toSet(), s -> Map.of(storageEntry, s)));
    int c = 0;
    do {
      log.trace("OUTGOING REFERENCES: loop enter");
      refs.forEach(
          (from, tos) -> tos.forEach(it -> nodeAdditionService.add(graph, from, it.a(), it.b())));

      final Set<StorageEntry> candidates = refs.values().stream()
          .flatMap(Set::stream)
          .map(Pair::a)
          .collect(toSet());
      refs = candidates.stream()
          .collect(toMap(
              Function.identity(),
              it -> outgoingEdgeDiscoveryService
                  .execute(graph, it, index)
                  .collect(toSet())));
      reportGraphState(graph);
    } while (!Thread.currentThread().isInterrupted()
             && (++c < limit || limit < 0)
             && hasValues(refs));
    log.trace("OUTGOING REFERENCES: loop exit");
  }

  private void renderIncomingReferences(Graph graph, StorageEntry storageEntry,
                                        AdjacencyAware index) {
    final var limit = settings.getGraphTraversalInboundLimit();
    Map<StorageEntry, Set<StorageEntry>> referrers = Map.of(
        storageEntry,
        incomingEdgeDiscoveryService.execute(graph, storageEntry, index).
            collect(toSet()));
    int c = 0;
    do {
      log.trace("INCOMING REFERENCES: loop enter");
      Set<StorageEntry> candidates = referrers.values().stream()
          .flatMap(Set::stream)
          .collect(toSet());
      candidates.forEach(it -> outgoingEdgeDiscoveryService
          .findConnectionsWithExistingNodes(graph, it, index)
          .forEach(target -> nodeAdditionService.add(graph, it, target.a(), target.b())));
      referrers = candidates.stream().collect(toMap(
          Function.identity(),
          it -> incomingEdgeDiscoveryService
              .execute(graph, it, index)
              .filter(r -> NodeAdditionService.edgeMissing(graph, r, it))
              .collect(toSet())));
      reportGraphState(graph);
    } while (!Thread.currentThread().isInterrupted()
             && (++c < limit || limit < 1)
             && hasValues(referrers));
    log.trace("INCOMING REFERENCES: loop exit");
  }

  private static boolean hasValues(Map<?, ? extends Set<?>> m) {
    return m.values().stream().flatMap(Set::stream).findAny().isPresent();
  }

  public Optional<StorageEntry> getStorageEntry(String uriString) {
    return EntryUri.parse(uriString)
        .map(storageInstance::resolve)
        .flatMap(res -> switch (res) {
          case EntryResolution.Known(var entry) -> Optional.of(entry);
          case EntryResolution.Unknown u -> Optional.<StorageEntry>empty();
        });
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

  public void showLabels(final Graph graph, final boolean show) {
    final String styleToAdd = show ? "text-mode:normal;" : "text-mode:hidden;";
    graph.nodes().forEach(node -> {
      final String inlineStyle = node.getAttribute(Attributes.INLINE_STYLE, String.class);
      final String newStyle = safeConcat(inlineStyle, styleToAdd);
      node.setAttribute(Attributes.INLINE_STYLE, newStyle);
    });
    graph.edges().forEach(edge -> {
      final String inlineStyle = edge.getAttribute(Attributes.INLINE_STYLE, String.class);
      final String newStyle = safeConcat(inlineStyle, styleToAdd);
      edge.setAttribute(Attributes.INLINE_STYLE, newStyle);
    });
  }

  private static String safeConcat(String inlineStyle, String styleToAdd) {
    final String newStyle;
    if (inlineStyle != null) {
      newStyle = inlineStyle + styleToAdd;
    } else {
      newStyle = styleToAdd;
    }
    return newStyle;
  }

  private void styleGraph(final Graph graph, final MetadataAware stats) {
    final var sStrategy = createNodeSizingStrategy(graph, settings.getNodeSizing(), stats);
    final var cStrategy = createNodeColouringStrategy(graph, settings.getNodeColouring());
    graph.nodes().forEach(node -> {
      final String sizing = sStrategy.compute(node);
      final String colouring = cStrategy.compute(node);
      if (!colouring.isBlank()) {
        attributeMap.set(node, "colouring", colouring);
      }

      if (!sizing.isBlank() || !colouring.isBlank()) {
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
                                                       final GraphSettings.NodeSizing sizing,
                                                       final MetadataAware stats) {
    return switch (sizing) {
      case DEGREE -> new DegreeNodeSizingStrategy(graph);
      case IN_DEGREE -> new InDegreeNodeSizingStrategy(graph);
      case OUT_DEGREE -> new OutDegreeNodeSizingStrategy(graph);
      case VERSION_COUNT -> new VersionCountNodeSizingStrategy(graph, stats);
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
    private final MetadataAware stats;

    VersionCountNodeSizingStrategy(Graph graph, MetadataAware stats) {
      super(graph);
      this.stats = stats;
    }

    @Override
    protected int computeSize(Node node) {
      return getStorageEntry(node.getId())
          .map(stats::metadataOf)
          .map(res -> switch (res) {
            case MetadataResult.Ok(var e, var meta) ->
                10 + (meta.versionNr() == null ? 0 : meta.versionNr().intValue());
            case MetadataResult.Err err -> 10;
          })
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
      int blue = Float.compare(ratio, 0.5f) <= 0 ? red : green;
      blue = (int) (1.3f * blue);

      return String.format("%02x%02x%02xFF", red, green, blue);
    }

  }

}

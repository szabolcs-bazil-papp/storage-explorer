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

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import com.aestallon.storageexplorer.client.BatchLoaderExecutor;
import com.aestallon.storageexplorer.client.graph.event.GraphState;
import com.aestallon.storageexplorer.client.userconfig.model.UmlExportSettings;
import com.aestallon.storageexplorer.client.userconfig.service.NominalTypeService;
import com.aestallon.storageexplorer.client.util.OpResult;
import com.aestallon.storageexplorer.core.model.EntryUri;
import com.aestallon.storageexplorer.core.model.entry.ObjectEntry;
import com.aestallon.storageexplorer.core.model.entry.StorageEntry;
import com.aestallon.storageexplorer.core.model.entry.UriProperty;
import com.aestallon.storageexplorer.core.model.instance.StorageInstance;
import com.aestallon.storageexplorer.core.model.type.Association;
import com.aestallon.storageexplorer.core.model.type.EntityType;
import com.aestallon.storageexplorer.core.model.type.StructuredType;
import com.aestallon.storageexplorer.core.service.StorageInstanceExaminer;
import com.aestallon.storageexplorer.core.service.index.UriLeafScanner;
import com.aestallon.storageexplorer.core.service.index.result.ContentLoadResult;
import com.aestallon.storageexplorer.core.service.index.result.EntryResolution;
import com.aestallon.storageexplorer.core.util.Uris;
import prefuse.data.Edge;
import prefuse.data.Graph;
import prefuse.data.Node;

public final class UmlRenderingService {

  public static final String COL_NODE_TYPE = "structured_type";
  public static final String COL_EDGE_ASSOC = "association";

  private final StorageInstance storageInstance;
  private final Map<String, StructuredType> typesByTypeName;
  private final Map<String, Association> assocationsBySourceTypeName;
  private final Map<String, Set<URI>> instanceCandidatesByTypeName;
  private final Consumer<GraphState> graphStateListener;
  private final Graph graph;
  private final Map<String, Node> nodesByTypeName;
  private final StorageInstanceExaminer.ObjectEntryLookupTable cache;
  private final NominalTypeService nominalTypeService;

  private String originTypename;


  public UmlRenderingService(final StorageInstance storageInstance,
                             final Consumer<GraphState> graphStateListener,
                             final NominalTypeService nominalTypeService) {
    this.storageInstance = storageInstance;
    this.graphStateListener = graphStateListener;
    this.nominalTypeService = nominalTypeService;
    typesByTypeName = new HashMap<>();
    assocationsBySourceTypeName = new HashMap<>();
    instanceCandidatesByTypeName = new HashMap<>();
    graph = createGraph();
    nodesByTypeName = new HashMap<>();
    cache = StorageInstanceExaminer.ObjectEntryLookupTable.newInstance();
  }

  public Graph graph() {
    return graph;
  }

  public Map<String, Node> nodesByTypeName() {
    return Collections.unmodifiableMap(nodesByTypeName);
  }

  private Graph createGraph() {
    final Graph graph = new Graph(true);
    graph.getNodeTable().addColumn(COL_NODE_TYPE, StructuredType.class);
    graph.getEdgeTable().addColumn(COL_EDGE_ASSOC, Association.class);
    return graph;
  }

  public String originTypename() {
    return originTypename;
  }

  public void render(final ObjectEntry objectEntry) {
    final ContentLoadResult result = storageInstance.read(objectEntry);
    final StructuredType type = storageInstance.getOrDescribeTypeOf(objectEntry);
    cacheInstanceCandidates(objectEntry, result);
    addType(type);
    originTypename = type.name();

    graphStateListener.accept(new GraphState(
        graph.getNodeCount(),
        graph.getEdgeCount()));
  }

  private Node addType(final StructuredType type) {
    if (typesByTypeName.containsKey(type.name())) {
      return nodesByTypeName.get(type.name());
    }

    typesByTypeName.put(type.name(), type);

    final Node node = graph.addNode();
    node.set(COL_NODE_TYPE, type);
    nodesByTypeName.put(type.name(), node);

    addAssociations(type, node);

    return node;
  }

  private void addAssociations(StructuredType type, Node node) {
    final var associations = type.associations();
    for (final var association : associations) {
      final Node toNode = addType(association.to());
      final Edge edge = graph.addEdge(node, toNode);
      edge.set(COL_EDGE_ASSOC, association);
      assocationsBySourceTypeName.put(type.name(), association);
    }
  }

  private Node addType(final String typeName) {
    return addType(new StructuredType.Unknown(typeName));
  }

  public boolean determineStructure(final StructuredType.Unknown type) {
    final var typeName = type.name();
    if (!typesByTypeName.containsKey(typeName)) {
      return false;
    }

    final var candidates = instanceCandidatesByTypeName
        .computeIfAbsent(typeName, k -> new HashSet<>());
    final var candidateEntries = candidates.stream()
        .flatMap(it -> resolve(it).stream())
        .collect(Collectors.toSet());
    final StructuredType describedType;
    try (var index = storageInstance.newQueryIndex()) {
      final var loadResults = BatchLoaderExecutor
          .builder(new StorageInstanceExaminer(), index, candidateEntries)
          .useCache(cache)
          .build()
          .execute();

      loadResults.forEach(it -> cacheInstanceCandidates(it.entry(), it.result()));
      describedType = storageInstance.getOrDescribeTypeOf(type.name());
    }
    if (!(describedType instanceof EntityType entity)) {
      return false;
    }

    typesByTypeName.put(typeName, entity);
    nodesByTypeName.get(typeName).set(COL_NODE_TYPE, entity);

    addAssociations(entity, nodesByTypeName.get(typeName));
    return true;
  }

  /** {@code oe}'s outgoing references, scanned straight from its already-loaded content — the
   *  replacement for the removed {@code ObjectEntry#uriProperties()} (T4.1): since the caller
   *  already holds the {@link ContentLoadResult} from either a single {@link #render} or a
   *  {@link BatchLoaderExecutor} batch, re-reading via a fresh {@code AdjacencyAware} index would
   *  waste the read already done. */
  private void cacheInstanceCandidates(final ObjectEntry oe, final ContentLoadResult result) {
    if (!(result instanceof ContentLoadResult.Ok ok)) {
      return;
    }

    final Set<UriProperty> refs;
    try (InputStream in = ok.content().openStream()) {
      refs = UriLeafScanner.scan(in);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }

    refs.stream()
        .map(UriProperty::uri)
        .collect(Collectors.groupingBy(Uris::getTypeName))
        .forEach((typeName, uris) -> instanceCandidatesByTypeName
            .computeIfAbsent(typeName, k -> new HashSet<>())
            .addAll(uris));
  }

  private Optional<StorageEntry> resolve(final URI uri) {
    return EntryUri.parse(uri.toString())
        .map(storageInstance::resolve)
        .flatMap(res -> switch (res) {
          case EntryResolution.Known(var entry) -> Optional.of(entry);
          case EntryResolution.Unknown u -> Optional.<StorageEntry>empty();
        });
  }

  public OpResult exportEntityRelationshipDiagram(final Path target,
                                                  final UmlExportSettings settings) {
    final var types = new HashSet<>(typesByTypeName.values());
    final var associations = new HashSet<>(assocationsBySourceTypeName.values());
    return new PlantUmlExportService(types, associations, settings, nominalTypeService)
        .exportEntityRelationshipDiagram(target);
  }

}

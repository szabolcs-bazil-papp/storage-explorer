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

import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import com.aestallon.storageexplorer.client.BatchLoaderExecutor;
import com.aestallon.storageexplorer.client.graph.event.GraphState;
import com.aestallon.storageexplorer.core.model.entry.ObjectEntry;
import com.aestallon.storageexplorer.core.model.entry.UriProperty;
import com.aestallon.storageexplorer.core.model.instance.StorageInstance;
import com.aestallon.storageexplorer.core.model.type.Association;
import com.aestallon.storageexplorer.core.model.type.EntityType;
import com.aestallon.storageexplorer.core.model.type.StructuredType;
import com.aestallon.storageexplorer.core.service.StorageInstanceExaminer;
import com.aestallon.storageexplorer.core.util.Uris;
import prefuse.data.Edge;
import prefuse.data.Graph;
import prefuse.data.Node;

public final class UmlRenderingService {

  public static final String COL_NODE_TYPE = "structured_type";
  public static final String COL_EDGE_ASSOC = "association";

  public static final String COL_TOOLTIP = "tooltip_label";

  private final StorageInstance storageInstance;
  private final Map<String, StructuredType> typesByTypeName;
  private final Map<String, Association> assocationsBySourceTypeName;
  private final Map<String, Set<URI>> instanceCandidatesByTypeName;
  private final Consumer<GraphState> graphStateListener;
  private final Graph graph;
  private final Map<String, Node> nodesByTypeName;
  private final StorageInstanceExaminer.ObjectEntryLookupTable cache;


  public UmlRenderingService(final StorageInstance storageInstance,
                             final Consumer<GraphState> graphStateListener) {
    this.storageInstance = storageInstance;
    this.graphStateListener = graphStateListener;
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
    graph.getNodeTable().addColumn(COL_TOOLTIP, String.class);
    graph.getEdgeTable().addColumn(COL_EDGE_ASSOC, Association.class);
    graph.getEdgeTable().addColumn(COL_TOOLTIP, String.class);
    return graph;
  }

  public void render(final ObjectEntry objectEntry) {
    final StructuredType type = storageInstance
        .index()
        .getOrDescribeTypeOf(objectEntry);
    cacheInstanceCandidates(objectEntry);
    addType(type);

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
    node.set(COL_TOOLTIP, type.name());
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
      edge.set(COL_TOOLTIP, association.propertyPath());
      assocationsBySourceTypeName.put(type.name(), association);
    }
  }

  private Node addType(final String typeName) {
    return addType(new StructuredType.Unknown(typeName));
  }

  public void determineStructure(final StructuredType.Unknown type) {
    final var typeName = type.name();
    if (!typesByTypeName.containsKey(typeName)) {
      return;
    }

    final var candidates = instanceCandidatesByTypeName
        .computeIfAbsent(typeName, k -> new HashSet<>());
    final var candidateEntries = candidates.stream()
        .flatMap(it -> storageInstance.discover(it).stream())
        .collect(Collectors.toSet());
    final var loadResults = BatchLoaderExecutor
        .builder(storageInstance.examiner(), candidateEntries)
        .useCache(cache)
        .build()
        .execute();
    if(loadResults.isEmpty()) {
      return;
    }

    loadResults.stream()
        .map(BatchLoaderExecutor.EntryWithLoadResult::entry)
        .forEach(this::cacheInstanceCandidates);
    final StructuredType describedType = storageInstance
        .index()
        // TODO: Add method to describe type of all loaded entries, and merge structured type:
        .getOrDescribeTypeOf(loadResults.stream().findFirst().orElseThrow().entry());
    if (!(describedType instanceof EntityType entity)) {
      return;
    }

    typesByTypeName.put(typeName, entity);
    nodesByTypeName.get(typeName).set(COL_NODE_TYPE, entity);

    addAssociations(entity, nodesByTypeName.get(typeName));
  }

  private void cacheInstanceCandidates(ObjectEntry oe) {
    oe.uriProperties().stream()
        .map(UriProperty::uri)
        .collect(Collectors.groupingBy(Uris::getTypeName))
        .forEach((typeName, uris) -> instanceCandidatesByTypeName
            .computeIfAbsent(typeName, k -> new HashSet<>())
            .addAll(uris));
  }



}

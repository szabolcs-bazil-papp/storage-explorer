package com.aestallon.storageexplorer.client.graph.service;


import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.jgrapht.Graph;
import org.jgrapht.graph.DirectedPseudograph;
import org.jgrapht.nio.Attribute;
import org.jgrapht.nio.DefaultAttribute;
import org.jgrapht.nio.gexf.GEXFAttributeType;
import org.jgrapht.nio.gexf.GEXFExporter;
import com.aestallon.storageexplorer.core.model.EntryUri;
import com.aestallon.storageexplorer.core.model.entry.StorageEntry;
import com.aestallon.storageexplorer.core.model.entry.UriProperty;
import com.aestallon.storageexplorer.core.model.instance.StorageInstance;
import com.aestallon.storageexplorer.core.model.loading.IndexingTarget;
import com.aestallon.storageexplorer.core.service.index.AdjacencyAware;
import com.aestallon.storageexplorer.core.service.index.result.AdjacencyResult;
import com.aestallon.storageexplorer.core.service.index.result.EntryResolution;

public class GraphExportService {

  public boolean export(final StorageInstance instance, final Path target) throws IOException {

    final Graph<StorageEntry, UriProperty> graph = new DirectedPseudograph<>(UriProperty.class);
    try (AdjacencyAware index = instance.newGraphIndex();
         Stream<StorageEntry> all = instance.query(IndexingTarget.any())) {
      all.forEach(it -> {
        graph.addVertex(it);
        outgoingReferences(it, index)
            .forEach(uriProp -> resolve(instance, uriProp.uri).ifPresent(t -> {
              if (!graph.containsVertex(t)) {
                graph.addVertex(t);
              }
              graph.addEdge(it, t, uriProp);
            }));
      });

      final GEXFExporter<StorageEntry, UriProperty> exporter = getGexfExporter(index);
      try (final var writer = Files.newBufferedWriter(target, StandardCharsets.UTF_8,
          StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
          StandardOpenOption.WRITE)) {
        exporter.exportGraph(graph, writer);
        return true;
      }
    }
  }

  private static java.util.Set<UriProperty> outgoingReferences(final StorageEntry entry,
                                                                final AdjacencyAware index) {
    return switch (index.outgoingReferences(entry)) {
      case AdjacencyResult.Ok(var e, var refs) -> refs;
      case AdjacencyResult.Err err -> java.util.Set.of();
    };
  }

  private static Optional<StorageEntry> resolve(final StorageInstance instance, final URI uri) {
    return EntryUri.parse(uri.toString())
        .map(instance::resolve)
        .flatMap(res -> switch (res) {
          case EntryResolution.Known(var entry) -> Optional.of(entry);
          case EntryResolution.Unknown u -> Optional.<StorageEntry>empty();
        });
  }

  private static GEXFExporter<StorageEntry, UriProperty> getGexfExporter(final AdjacencyAware index) {
    GEXFExporter<StorageEntry, UriProperty> exporter = new GEXFExporter<>();
    exporter.setVertexAttributeProvider(it -> {
      Map<String, Attribute> map = new LinkedHashMap<>();
      map.put("label", DefaultAttribute.createAttribute(it.toString()));

      final var typeColour = NodeColours.ofType(it);
      map.put("color", DefaultAttribute.createAttribute(typeColour));
      map.put("viz:color", DefaultAttribute.createAttribute(typeColour));

      final var schemaColour = NodeColours.ofSchema(it);
      map.put("viz:schemacolor", DefaultAttribute.createAttribute(schemaColour));

      final var size = outgoingReferences(it, index).size();
      map.put("size", DefaultAttribute.createAttribute(size));

      return map;
    });
    exporter.setEdgeAttributeProvider(it -> {
      Map<String, Attribute> map = new LinkedHashMap<>();
      map.put("label", DefaultAttribute.createAttribute(it.toString()));
      map.put("standalone",  DefaultAttribute.createAttribute(it.isStandalone()));
      return map;
    });
    exporter.registerAttribute("viz:color", GEXFExporter.AttributeCategory.NODE, GEXFAttributeType.STRING);
    exporter.registerAttribute("viz:schemacolor", GEXFExporter.AttributeCategory.NODE, GEXFAttributeType.STRING);
    return exporter;
  }

}

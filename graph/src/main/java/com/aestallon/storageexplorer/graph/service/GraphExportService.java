package com.aestallon.storageexplorer.graph.service;


import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jgrapht.Graph;
import org.jgrapht.graph.DirectedPseudograph;
import org.jgrapht.nio.Attribute;
import org.jgrapht.nio.DefaultAttribute;
import org.jgrapht.nio.gexf.GEXFAttributeType;
import org.jgrapht.nio.gexf.GEXFExporter;
import com.aestallon.storageexplorer.core.model.entry.StorageEntry;
import com.aestallon.storageexplorer.core.model.entry.UriProperty;
import com.aestallon.storageexplorer.core.model.instance.StorageInstance;

public class GraphExportService {

  public boolean export(final StorageInstance instance, final Path target) throws IOException {

    final Graph<StorageEntry, UriProperty> graph = new DirectedPseudograph<>(UriProperty.class);
    instance.entities().forEach(graph::addVertex);
    instance.entities().forEach(it -> it.uriProperties()
        .forEach(uriProp -> instance.index().get(uriProp.uri()).ifPresent(t -> {
          if (!graph.containsVertex(t)) {
            graph.addVertex(t);
          }
          graph.addEdge(it, t, uriProp);
        })));

    final GEXFExporter<StorageEntry, UriProperty> exporter = getGexfExporter();
    try (final var writer = Files.newBufferedWriter(target, StandardCharsets.UTF_8,
        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
        StandardOpenOption.WRITE)) {
      exporter.exportGraph(graph, writer);
      return true;
    }

  }

  private static GEXFExporter<StorageEntry, UriProperty> getGexfExporter() {
    GEXFExporter<StorageEntry, UriProperty> exporter = new GEXFExporter<>();
    exporter.setVertexAttributeProvider(it -> {
      Map<String, Attribute> map = new LinkedHashMap<>();
      map.put("label", DefaultAttribute.createAttribute(it.toString()));
      
      final var typeColour = NodeColour.ofType(it);
      map.put("color", DefaultAttribute.createAttribute(typeColour));
      map.put("viz:color", DefaultAttribute.createAttribute(typeColour));
      
      final var schemaColour = NodeColour.ofSchema(it);
      map.put("viz:schemacolor", DefaultAttribute.createAttribute(schemaColour));
      
      final var size = it.uriPropertiesStrict().size();
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
    return exporter;
  }

}

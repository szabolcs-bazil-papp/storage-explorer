package com.aestallon.storageexplorer.client.graph.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import com.aestallon.storageexplorer.client.userconfig.model.UmlExportSettings;
import com.aestallon.storageexplorer.client.util.OpResult;
import com.aestallon.storageexplorer.common.util.NotImplementedException;
import com.aestallon.storageexplorer.core.model.type.Association;
import com.aestallon.storageexplorer.core.model.type.EntityType;
import com.aestallon.storageexplorer.core.model.type.Property;
import com.aestallon.storageexplorer.core.model.type.PropertyType;
import com.aestallon.storageexplorer.core.model.type.StructuredType;

public final class PlantUmlExportService {

  private record PumlDiagram(
      List<PumlEntity> entities,
      List<PumlComplex> dataClasses,
      List<PumlRelation> relations) {

    public PumlDiagram() {
      this(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
    }

  }


  private record PumlEntity(String name, List<PumlProp> props) {}


  private record PumlProp(String name, String type) {}


  private record PumlComplex(String symbolicName, String legibleName, List<PumlProp> props) {}


  private enum ArrowType { COMPOSITION, SIMPLE }


  private enum LineType { DASHED, SOLID }


  private enum ArityMarker { ONE, MANY }


  private record PumlRelation(
      String symbolicNameFrom,
      String propFrom,
      String symbolicNameTo,
      ArrowType arrowType,
      LineType lineType,
      ArityMarker arityMarker,
      String label) {}


  private static final String TITLE_EXPORT_ERD = "ERD Export";
  private static final String TITLE_EXPORT_EERD = "Extended ERD Export";


  private final Set<StructuredType> entities;
  private final Set<Association> associations;
  private final UmlExportSettings settings;
  private int internalTypeNum = 0;



  PlantUmlExportService(Set<StructuredType> entities, Set<Association> associations,
                        UmlExportSettings settings) {
    this.entities = entities;
    this.associations = associations;
    this.settings = settings;
  }

  OpResult exportEntityRelationshipDiagram(final Path target) {
    try {
      return exportEntityRelationshipDiagramInternal(target);
    } catch (final Exception e) {
      return new OpResult.Err.Exc(TITLE_EXPORT_ERD, e);
    }
  }

  private OpResult exportEntityRelationshipDiagramInternal(final Path target) throws IOException {
    final var diagram = buildDiagram();
    writeDiagramToFile(diagram, target);
    return new OpResult.Ok(TITLE_EXPORT_EERD, "Great success!");
  }

  private PumlDiagram buildDiagram() {
    PumlDiagram diagram = new PumlDiagram();
    for (final var entity : entities) {
      processEntity(diagram, entity);
    }
    return diagram;
  }

  private void processEntity(PumlDiagram diagram, StructuredType entity) {
    final PumlEntity pumlEntity = new PumlEntity(entity.name(), new ArrayList<>());
    if (entity instanceof EntityType(var name, var properties)) {
      for (final var p : properties) {
        processProperty(diagram, pumlEntity, p.key(), p.key(), null, p, -1);
      }
    }

    diagram.entities.add(pumlEntity);
  }

  private void processProperty(PumlDiagram diagram,
                               PumlEntity hostEntity,
                               String hostPropKey,
                               String propPath,
                               PumlComplex hostDataClass,
                               Property property,
                               int complexVariant) {
    final var key = property.key();
    final var type = switch (property.type()) {
      case PropertyType.Primitive primitive -> primitive.toString();
      case PropertyType.Ref ref -> {

        final PumlRelation relation = new PumlRelation(
            hostDataClass != null
                ? (settings.isDetailComplexProps()
                ? hostDataClass.symbolicName()
                : hostEntity.name())
                : hostEntity.name(),
            settings.isDrawEdgesFromProperty()
                ? (settings.isDetailComplexProps() ? key : hostPropKey)
                : null,
            ref.entityName(),
            ArrowType.SIMPLE,
            LineType.SOLID,
            switch (property.arity()) {
              case ONE -> ArityMarker.ONE;
              case MANY -> ArityMarker.MANY;
            },
            settings.isLabelEdges() ? (settings.isDetailComplexProps() ? key : propPath) : null);
        diagram.relations.add(relation);
        yield ref.toString();
      }
      case PropertyType.Complex complex -> {
        final var dataClass = new PumlComplex(
            symbolicName(hostEntity.name(), propPath, complexVariant),
            typify(hostEntity.name(), propPath),
            new ArrayList<>());
        if (settings.isDetailComplexProps()) {
          diagram.dataClasses.add(dataClass);
          final var relation = new PumlRelation(
              hostDataClass != null
                  ? (settings.isDetailComplexProps()
                  ? hostDataClass.symbolicName()
                  : hostEntity.name())
                  : hostEntity.name(),
              settings.isDrawEdgesFromProperty()
                  ? key
                  : null,
              dataClass.symbolicName(),
              ArrowType.COMPOSITION,
              LineType.DASHED,
              switch (property.arity()) {
                case ONE -> ArityMarker.ONE;
                case MANY -> ArityMarker.MANY;
              },
              settings.isLabelEdges() ? key : null);
          diagram.relations.add(relation);
          for (final var innerP : complex.properties()) {
            processProperty(
                diagram,
                hostEntity,
                hostPropKey,
                propPath + "." + innerP.key(),
                dataClass,
                innerP,
                -1);
          }

          yield dataClass.legibleName();
        } else {
          for (final var innerP : complex.properties()) {
            processProperty(
                diagram,
                hostEntity,
                hostPropKey,
                propPath + "." + innerP.key(),
                dataClass,
                innerP,
                -1);
          }
          yield complexVariant < 0 ? typify(hostEntity.name(), propPath) : null;
        }
      }
      case PropertyType.Union union -> {
        final var complexes = union.types().stream()
            .filter(PropertyType.Complex.class::isInstance)
            .map(PropertyType.Complex.class::cast)
            .toList();
        for (int i = 0; i < complexes.size(); i++) {
          processProperty(
              diagram, hostEntity,
              hostPropKey, propPath,
              hostDataClass,
              new Property(key, complexes.get(i)), i);
        }
        yield union.toString();
      }
    };

    if (type == null) {
      return;
    }

    final boolean include = hostDataClass == null || settings.isDetailComplexProps();
    if (!include) {
      return;
    }

    final var pumlProp = new PumlProp(key, type);
    if (hostDataClass != null) {
      hostDataClass.props.add(pumlProp);
    } else {
      hostEntity.props.add(pumlProp);
    }
  }

  private String stringifyDiagram(PumlDiagram diagram) {
    final StringBuilder sb = new StringBuilder();
    sb.append("@startuml\n");
    sb.append("skinparam defaultFontName \"Jetbrains Mono\"\n");
    for (final var entity : diagram.entities) {
      writeEntity(entity, sb);
    }

    for (final var dataClass : diagram.dataClasses) {
      writeComplex(dataClass, sb);
    }

    for (final var relation : diagram.relations) {
      writeRelation(relation, sb);
    }

    sb.append("@enduml\n");
    return sb.toString();
  }

  private void writeEntity(PumlEntity entity, StringBuilder sb) {
    sb.append("entity ").append(entity.name()).append(" {\n");
    sb.append("uri\n");
    sb.append("---\n");
    for (final var prop : entity.props) {
      sb.append(prop.type()).append(" ").append(prop.name()).append("\n");
    }
    sb.append("}\n");
  }

  private void writeComplex(PumlComplex complex, StringBuilder sb) {
    sb.append("dataclass ").append(complex.symbolicName())
        .append(" as \"").append(complex.legibleName()).append("\"")
        .append(" {\n");

    for (final var prop : complex.props) {
      sb.append(prop.type()).append(" ").append(prop.name()).append("\n");
    }
    sb.append("}\n");

  }

  private void writeRelation(PumlRelation relation, StringBuilder sb) {
    sb.append(relation.symbolicNameFrom());
    if (relation.propFrom() != null) {
      sb.append("::").append(relation.propFrom()).append(" ");
    }

    if (settings.isDisplayArity()) {
      sb.append("\"1\" ");
    }

    String arrow = switch (relation.arrowType) {
      case SIMPLE -> switch (relation.lineType) {
        case SOLID -> "-->";
        case DASHED -> "..>";
      };
      case COMPOSITION -> switch (relation.lineType) {
        case SOLID -> "*--";
        case DASHED -> "*..";
      };
    };
    sb.append(arrow);
    if (settings.isDisplayArity()) {
      sb.append(switch (relation.arityMarker) {
        case ONE -> "\"1\" ";
        case MANY -> "\"*\" ";
      });
    }

    sb.append(relation.symbolicNameTo());
    if (relation.label() != null && settings.isLabelEdges()) {
      sb.append(" : ").append(relation.label());
    }
    sb.append("\n");
  }

  private void writeDiagramToFile(PumlDiagram diagram, Path target) throws IOException {
    try (final var writer = Files.newBufferedWriter(target, StandardOpenOption.CREATE,
        StandardOpenOption.TRUNCATE_EXISTING,
        StandardOpenOption.WRITE)) {
      writer.write(stringifyDiagram(diagram));
      writer.flush();
    }
  }


  private static String capitalise(String s) {
    return s.substring(0, 1).toUpperCase() + s.substring(1);
  }

  private static String typify(String entityName, String propPath) {
    return entityName + "." + Arrays
        .stream(propPath.split("\\."))
        .map(PlantUmlExportService::capitalise)
        .collect(Collectors.joining("."));
  }

  private static String symbolicName(String entityName, String propPath, int variant) {
    final var raw = "__d_" + entityName + "_" + Arrays
        .stream(propPath.split("\\."))
        .map(PlantUmlExportService::capitalise)
        .collect(Collectors.joining("_"));
    return variant >= 0 ? raw + "_v" + variant : raw;
  }

}

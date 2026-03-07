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

package com.aestallon.storageexplorer.core.service.type;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.aestallon.storageexplorer.core.model.type.NominalType;
import com.aestallon.storageexplorer.core.model.type.PropertyType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

public final class YamlSchemaExtractor {

  private static final YAMLMapper YAML_MAPPER = new YAMLMapper();
  public static final String YAML_PROP_COMPONENTS = "components";
  public static final String YAML_PROP_SCHEMAS = "schemas";
  public static final String YAML_PROP_TYPE = "type";
  public static final String YAML_VALUE_OBJECT = "object";
  public static final String YAML_PROP_PROPERTIES = "properties";
  public static final String YAML_VALUE_ARRAY = "array";
  public static final String YAML_PROP_DESCRIPTION = "description";
  public static final String YAML_PROP_ITEMS = "items";
  public static final String YAML_PROP_REF_MARKER = "$ref";
  public static final String YAML_PROP_FORMAT = "format";

  public Map<String, NominalType.Root> extract(String yaml) throws IOException {
    final var root = YAML_MAPPER.readTree(yaml);
    final var schemas = root.path(YAML_PROP_COMPONENTS).path(YAML_PROP_SCHEMAS);
    if (schemas.isMissingNode() || schemas.isNull()) {
      return Map.of();
    }

    Map<String, NominalType.Root> result = new LinkedHashMap<>();
    for (Iterator<Map.Entry<String, JsonNode>> it = schemas.fields(); it.hasNext(); ) {
      Map.Entry<String, JsonNode> entry = it.next();
      String name = entry.getKey();
      JsonNode node = entry.getValue();
      if (isEnumSchema(node)) {
        result.put(name, parseEnumSchema(name, node));
      } else if (isObjectSchema(node)) {
        result.put(name, parseObjectSchema(name, node));
      }
    }
    return result;
  }



  private boolean isObjectSchema(JsonNode node) {
    String type = node.path(YAML_PROP_TYPE).asText(null);
    return YAML_VALUE_OBJECT.equals(type) || (type == null && node.has(YAML_PROP_PROPERTIES));
  }

  private boolean isEnumSchema(JsonNode node) {
    final var $enum = node.path("enum");
    return !$enum.isMissingNode() && !$enum.isNull() && $enum.isArray();
  }

  private NominalType.Obj parseObjectSchema(String typeName, JsonNode schemaNode) {
    String description = textOrEmpty(schemaNode, YAML_PROP_DESCRIPTION);
    List<String> requiredKeys = collectRequired(schemaNode);

    List<NominalType.ObjProperty> props = new ArrayList<>();
    JsonNode properties = schemaNode.path(YAML_PROP_PROPERTIES);
    if (!properties.isMissingNode()) {
      for (Iterator<Map.Entry<String, JsonNode>> it = properties.fields(); it.hasNext(); ) {
        Map.Entry<String, JsonNode> entry = it.next();
        props.add(parseProperty(entry.getKey(), entry.getValue(), requiredKeys));
      }
    }

    return new NominalType.Obj(typeName, description, List.copyOf(props));
  }

  private NominalType.Enumeration parseEnumSchema(String typeName, JsonNode schemaNode) {
    String description = textOrEmpty(schemaNode, YAML_PROP_DESCRIPTION);
    List<String> values = new ArrayList<>();
    JsonNode $enum = schemaNode.path("enum");
    if (!$enum.isMissingNode() && !$enum.isNull() && $enum.isArray()) {
      for (JsonNode n : $enum) {
        values.add(n.asText());
      }
    }
    final String variantDescription = String.join(" | ", values);
    if (!description.isEmpty()) {
      description += "\n";
    }

    description += "Possible values: " + variantDescription;
    return new NominalType.Enumeration(typeName, description, List.copyOf(values));
  }

  private NominalType.ObjProperty parseProperty(
      String key, JsonNode propNode, List<String> requiredKeys) {

    boolean required = requiredKeys.contains(key);
    String description = textOrEmpty(propNode, YAML_PROP_DESCRIPTION);
    String type = propNode.path(YAML_PROP_TYPE).asText(null);

    if (YAML_VALUE_ARRAY.equals(type)) {
      NominalType itemType = resolveType(propNode.path(YAML_PROP_ITEMS));
      return new NominalType.ObjProperty(key, description, itemType, PropertyType.Arity.MANY,
          required);
    }

    return new NominalType.ObjProperty(
        key, description, resolveType(propNode), PropertyType.Arity.ONE, required);
  }

  private NominalType resolveType(JsonNode node) {
    if (node == null || node.isMissingNode()) {
      return unknownObj();
    }

    // $ref takes priority
    String ref = node.path(YAML_PROP_REF_MARKER).asText(null);
    if (ref != null) {
      String refName = ref.contains("/")
          ? ref.substring(ref.lastIndexOf('/') + 1)
          : ref;
      return new NominalType.Ref(refName);
    }

    if (!node.path("enum").isMissingNode() && node.path("enum").isArray()) {
      final var enumVariants = new ArrayList<String>();
      node.path("enum").forEach(n -> enumVariants.add(n.asText()));
      String enumDescription = textOrEmpty(node, YAML_PROP_DESCRIPTION);
      for (final var it : node) {
        enumVariants.add(it.asText());
      }
      return new NominalType.Enumeration(
          String.join(" | ", enumVariants),
          enumDescription,
          enumVariants);
    }

    String type = node.path(YAML_PROP_TYPE).asText(null);
    String format = node.path(YAML_PROP_FORMAT).asText(null);

    // Inline anonymous object
    if (YAML_VALUE_OBJECT.equals(type) || (type == null && node.has(YAML_PROP_PROPERTIES))) {
      return unknownObj();
    }

    if ("string".equals(type))
      return mapStringFormat(format);
    if ("integer".equals(type))
      return mapIntegerFormat(format);
    if ("number".equals(type))
      return mapNumberFormat(format);
    if ("boolean".equals(type))
      return NominalType.Primitive.BOOL;

    // allOf / oneOf / anyOf / unknown
    return new NominalType.Ref(type);
  }

  private static NominalType unknownObj() {
    return new NominalType.Ref("UnknownObj");
  }

  private NominalType.Primitive mapStringFormat(String format) {
    if (format == null)
      return NominalType.Primitive.STR;
    return switch (format.toLowerCase()) {
      case "date" -> NominalType.Primitive.DATE;
      case "date-time" -> NominalType.Primitive.TIME;
      case "uri", "url" -> NominalType.Primitive.URI;
      case "uuid" -> NominalType.Primitive.UUID;
      default -> NominalType.Primitive.STR;
    };
  }

  private NominalType.Primitive mapIntegerFormat(String format) {
    if (format == null)
      return NominalType.Primitive.I32;
    return switch (format.toLowerCase()) {
      case "int64" -> NominalType.Primitive.I64;
      default -> NominalType.Primitive.I32;
    };
  }

  private NominalType.Primitive mapNumberFormat(String format) {
    if (format == null)
      return NominalType.Primitive.NUM;
    return switch (format.toLowerCase()) {
      case "float" -> NominalType.Primitive.F32;
      case "double" -> NominalType.Primitive.F64;
      default -> NominalType.Primitive.NUM;
    };
  }

  private static String textOrEmpty(JsonNode node, String fieldName) {
    JsonNode field = node.path(fieldName);
    return field.isTextual() ? field.asText() : "";
  }

  /** Collects entries from the schema's {@code required} array. */
  private static List<String> collectRequired(JsonNode schemaNode) {
    List<String> keys = new ArrayList<>();
    JsonNode req = schemaNode.path("required");
    if (req.isArray()) {
      req.forEach(n -> keys.add(n.asText()));
    }
    return keys;
  }
}

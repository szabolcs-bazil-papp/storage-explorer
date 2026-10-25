/*
 * Copyright (C) 2026 Szabolcs Bazil Papp
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

package com.aestallon.storageexplorer.client.userconfig.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.aestallon.storageexplorer.core.model.type.NominalType;
import com.aestallon.storageexplorer.core.model.type.Property;
import com.aestallon.storageexplorer.core.model.type.PropertyType;

class NominalTypeServiceTest {

  private static NominalTypeService newService(final Path settingsDir) {
    final var persistenceService = new UserConfigPersistenceService(settingsDir.toString());
    final var userConfigService = new UserConfigService(persistenceService, event -> {});
    return new NominalTypeService(userConfigService);
  }

  private static NominalType.ObjProperty propertyOf(final NominalType.Obj obj, final String key) {
    return obj.properties().stream()
        .filter(p -> p.key().equals(key))
        .findFirst()
        .orElseThrow();
  }

  @Test
  void additionalPropertiesRefResolvesToRecordOfTheReferencedComplex(@TempDir Path tempDir)
      throws IOException {
    final var yaml = """
        components:
          schemas:
            Attribute:
              type: object
              properties:
                name:
                  type: string
            Host:
              type: object
              properties:
                attributesById:
                  type: object
                  additionalProperties:
                    $ref: '#/components/schemas/Attribute'
              required: [ attributesById ]""";
    final var yamlFile = tempDir.resolve("source.yaml");
    Files.writeString(yamlFile, yaml, StandardCharsets.UTF_8);

    final var service = newService(tempDir.resolve("settings"));
    service.save(yamlFile);

    final var host = (NominalType.Obj) service.get("Host").orElseThrow();
    final var attributesById = propertyOf(host, "attributesById");

    final var structuralType = service.asPropertyType("Host", attributesById);
    assertThat(structuralType).isInstanceOf(PropertyType.Record.class);
    final var record = (PropertyType.Record) structuralType;
    assertThat(record.valueType()).isInstanceOf(PropertyType.Complex.class);
    final var attributeComplex = (PropertyType.Complex) record.valueType();
    assertThat(attributeComplex.properties()).hasSize(1);
    assertThat(attributeComplex.properties().get(0).key()).isEqualTo("name");
  }

  @Test
  void additionalPropertiesWithPrimitiveValueType(@TempDir Path tempDir) throws IOException {
    final var yaml = """
        components:
          schemas:
            Host:
              type: object
              properties:
                countsByLabel:
                  additionalProperties:
                    type: integer
                    format: int32
              required: [ countsByLabel ]""";
    final var yamlFile = tempDir.resolve("source.yaml");
    Files.writeString(yamlFile, yaml, StandardCharsets.UTF_8);

    final var service = newService(tempDir.resolve("settings"));
    service.save(yamlFile);

    final var host = (NominalType.Obj) service.get("Host").orElseThrow();
    final var countsByLabel = propertyOf(host, "countsByLabel");

    final var structuralType = service.asPropertyType("Host", countsByLabel);
    assertThat(structuralType)
        .isEqualTo(new PropertyType.Record(PropertyType.NUM, PropertyType.Arity.ONE));
  }

  @Test
  void nestedMapOfMapsRecursesThroughBothLevels(@TempDir Path tempDir) throws IOException {
    final var yaml = """
        components:
          schemas:
            Host:
              type: object
              properties:
                nestedMapOfMaps:
                  additionalProperties:
                    additionalProperties:
                      type: string
              required: [ nestedMapOfMaps ]""";
    final var yamlFile = tempDir.resolve("source.yaml");
    Files.writeString(yamlFile, yaml, StandardCharsets.UTF_8);

    final var service = newService(tempDir.resolve("settings"));
    service.save(yamlFile);

    final var host = (NominalType.Obj) service.get("Host").orElseThrow();
    final var nestedMapOfMaps = propertyOf(host, "nestedMapOfMaps");

    final var structuralType = service.asPropertyType("Host", nestedMapOfMaps);
    assertThat(structuralType).isInstanceOf(PropertyType.Record.class);
    final var outer = (PropertyType.Record) structuralType;
    assertThat(outer.valueType()).isInstanceOf(PropertyType.Record.class);
    final var inner = (PropertyType.Record) outer.valueType();
    assertThat(inner.valueType()).isEqualTo(PropertyType.STR);
  }

  @Test
  void aComplexWithUniformValuesSatisfiesTheDeclaredRecord(@TempDir Path tempDir)
      throws IOException {
    final var yaml = """
        components:
          schemas:
            Host:
              type: object
              properties:
                countsByLabel:
                  additionalProperties:
                    type: integer
                    format: int32
              required: [ countsByLabel ]""";
    final var yamlFile = tempDir.resolve("source.yaml");
    Files.writeString(yamlFile, yaml, StandardCharsets.UTF_8);

    final var service = newService(tempDir.resolve("settings"));
    service.save(yamlFile);

    final var host = (NominalType.Obj) service.get("Host").orElseThrow();
    final var countsByLabel = propertyOf(host, "countsByLabel");
    final var declaredType = service.asPropertyType("Host", countsByLabel);

    final var discoveredUniform = PropertyType.ofComplex(
        new Property("en", PropertyType.NUM),
        new Property("hu", PropertyType.NUM));
    final var discoveredMismatch = PropertyType.ofComplex(
        new Property("en", PropertyType.STR));

    assertThat(discoveredUniform.satisfies(declaredType)).isTrue();
    assertThat(discoveredMismatch.satisfies(declaredType)).isFalse();
  }

}

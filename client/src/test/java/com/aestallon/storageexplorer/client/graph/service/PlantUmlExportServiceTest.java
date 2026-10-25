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

package com.aestallon.storageexplorer.client.graph.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.aestallon.storageexplorer.client.userconfig.model.UmlExportSettings;
import com.aestallon.storageexplorer.client.userconfig.service.NominalTypeService;
import com.aestallon.storageexplorer.client.userconfig.service.UserConfigPersistenceService;
import com.aestallon.storageexplorer.client.userconfig.service.UserConfigService;
import com.aestallon.storageexplorer.core.model.type.Association;
import com.aestallon.storageexplorer.core.model.type.EntityType;
import com.aestallon.storageexplorer.core.model.type.Property;
import com.aestallon.storageexplorer.core.model.type.PropertyType;
import com.aestallon.storageexplorer.core.model.type.StructuredType;

class PlantUmlExportServiceTest {

  private static NominalTypeService nominalTypeServiceLoadedWith(final Path tempDir,
                                                                  final String yaml)
      throws IOException {
    final var yamlFile = tempDir.resolve("source.yaml");
    Files.writeString(yamlFile, yaml, StandardCharsets.UTF_8);

    final var persistenceService = new UserConfigPersistenceService(
        tempDir.resolve("settings").toString());
    final var userConfigService = new UserConfigService(persistenceService, event -> {});
    final var service = new NominalTypeService(userConfigService);
    service.save(yamlFile);
    return service;
  }

  private static final String YAML = """
      components:
        schemas:
          Data:
            type: object
            properties:
              name:
                type: string
            required: [ name ]
          Foo:
            type: object
            properties:
              data:
                $ref: '#/components/schemas/Data'
            required: [ data ]""";

  @Test
  void substitutesTheNominalTypeNameWhenTheDiscoveredShapeMatches(@TempDir Path tempDir)
      throws IOException {
    final var nominalTypeService = nominalTypeServiceLoadedWith(tempDir, YAML);

    final var dataComplex = PropertyType.ofComplex(new Property("name", PropertyType.STR));
    final var foo = new EntityType("Foo", List.of(new Property("data", dataComplex)));

    final var settings = new UmlExportSettings();
    final var target = tempDir.resolve("out.puml");
    final var result = new PlantUmlExportService(
        Set.<StructuredType>of(foo), Set.<Association>of(), settings, nominalTypeService)
        .exportEntityRelationshipDiagram(target);

    final var puml = Files.readString(target, StandardCharsets.UTF_8);
    assertThat(puml).contains("Data data").doesNotContain("Foo.Data");
  }

  @Test
  void fallsBackToTheSyntheticNameWhenTheDiscoveredShapeDoesNotMatch(@TempDir Path tempDir)
      throws IOException {
    final var nominalTypeService = nominalTypeServiceLoadedWith(tempDir, YAML);

    // "Data" requires a "name" string property; this discovered shape has none in common.
    final var mismatchingComplex = PropertyType.ofComplex(new Property("unrelated", PropertyType.NUM));
    final var foo = new EntityType("Foo", List.of(new Property("data", mismatchingComplex)));

    final var settings = new UmlExportSettings();
    final var target = tempDir.resolve("out.puml");
    new PlantUmlExportService(
        Set.<StructuredType>of(foo), Set.<Association>of(), settings, nominalTypeService)
        .exportEntityRelationshipDiagram(target);

    final var puml = Files.readString(target, StandardCharsets.UTF_8);
    assertThat(puml).contains("Foo.Data data");
  }

  @Test
  void worksWithoutANominalTypeServiceAtAll(@TempDir Path tempDir) throws IOException {
    final var dataComplex = PropertyType.ofComplex(new Property("name", PropertyType.STR));
    final var foo = new EntityType("Foo", List.of(new Property("data", dataComplex)));

    final var settings = new UmlExportSettings();
    final var target = tempDir.resolve("out.puml");
    new PlantUmlExportService(Set.<StructuredType>of(foo), Set.<Association>of(), settings, null)
        .exportEntityRelationshipDiagram(target);

    final var puml = Files.readString(target, StandardCharsets.UTF_8);
    assertThat(puml).contains("Foo.Data data");
  }

}

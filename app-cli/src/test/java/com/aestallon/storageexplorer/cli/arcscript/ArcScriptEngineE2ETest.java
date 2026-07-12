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

package com.aestallon.storageexplorer.cli.arcscript;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.AfterAll;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.smartbit4all.api.collection.CollectionApi;
import org.smartbit4all.api.config.PlatformApiConfig;
import org.smartbit4all.core.object.ObjectApi;
import org.smartbit4all.core.object.ObjectDefinitionApi;
import org.smartbit4all.domain.data.storage.ObjectStorage;
import org.smartbit4all.storage.fs.StorageFS;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import com.aestallon.storageexplorer.arcscript.api.Arc;
import com.aestallon.storageexplorer.arcscript.engine.ArcScriptResult;
import com.aestallon.storageexplorer.arcscript.engine.QueryEngineSettings;
import com.aestallon.storageexplorer.core.model.instance.StorageInstance;
import com.aestallon.storageexplorer.core.model.instance.dto.Availability;
import com.aestallon.storageexplorer.core.model.instance.dto.FsStorageLocation;
import com.aestallon.storageexplorer.core.model.instance.dto.IndexingStrategyType;
import com.aestallon.storageexplorer.core.model.instance.dto.StorageInstanceDto;
import com.aestallon.storageexplorer.core.model.instance.dto.StorageInstanceType;
import com.aestallon.storageexplorer.core.service.FileSystemStorageIndex;

/**
 * End-to-end LEGACY vs PIPELINED characterization of ArcScript query execution against a real,
 * file-system based smartbit4all storage.
 *
 * <p>
 * The storage is imported programmatically, the same way the interactive {@code import fs} CLI
 * command does it under the hood (a platform application context wrapping a {@link StorageFS}
 * instance). Every script is executed through both query engines, and their results must agree.
 *
 * <p>
 * These tests are skipped when the reference storage is not present on the machine.
 */
class ArcScriptEngineE2ETest {

  private static final Path STORAGE_PATH = Path.of("C:/Users/papps/Downloads/dev-fs/dev-fs");
  private static final Logger log = LoggerFactory.getLogger(ArcScriptEngineE2ETest.class);

  private static AnnotationConfigApplicationContext ctx;
  private static StorageInstance storageInstance;

  @BeforeAll
  static void setUp() {
    assumeTrue(
        Files.isDirectory(STORAGE_PATH),
        () -> "Reference storage not found at " + STORAGE_PATH);

    final var dto = new StorageInstanceDto()
        .id(UUID.randomUUID())
        .name("dev-fs")
        .availability(Availability.AVAILABLE)
        .indexingStrategy(IndexingStrategyType.ON_DEMAND)
        .type(StorageInstanceType.FS)
        .fs(new FsStorageLocation().path(STORAGE_PATH));
    storageInstance = StorageInstance.fromDto(dto);

    ctx = new AnnotationConfigApplicationContext();
    ctx.register(PlatformApiConfig.class);
    ctx.registerBean(
        storageInstance.id().toString(),
        ObjectStorage.class,
        () -> new StorageFS(
            STORAGE_PATH.toFile(),
            ctx.getBean(ObjectDefinitionApi.class)));
    final Map<String, Object> props = new HashMap<>();
    props.put("applicationruntime.maintain.enabled", "false");
    props.put("invocationregistry.refresh.enabled", "false");
    props.put("application.setup.enabled", "false");
    props.put("dataseries.mgmt.enabled", "false");
    ctx.getEnvironment().getPropertySources().addFirst(new MapPropertySource("default", props));
    ctx.refresh();

    storageInstance.setIndex(new FileSystemStorageIndex(
        storageInstance.id(),
        ctx.getBean(ObjectApi.class),
        ctx.getBean(CollectionApi.class),
        STORAGE_PATH,
        false));
  }

  @AfterAll
  static void tearDown() {
    if (ctx != null) {
      ctx.close();
    }
  }

  private static QueryEngineSettings legacy() {
    return QueryEngineSettings.builder()
        .engineMode(QueryEngineSettings.EngineMode.LEGACY)
        .build();
  }

  private static QueryEngineSettings pipelined() {
    return QueryEngineSettings.builder()
        .engineMode(QueryEngineSettings.EngineMode.PIPELINED)
        .build();
  }

  private static ArcScriptResult.QueryPerformed run(final String script,
                                                    final QueryEngineSettings settings) {
    final var result = Arc.evaluate(script, storageInstance, settings);
    assertThat(result)
        .withFailMessage("Expected successful execution, but got: %s", result)
        .isInstanceOf(ArcScriptResult.Ok.class);
    final var queries = ((ArcScriptResult.Ok) result).elements().stream()
        .filter(ArcScriptResult.QueryPerformed.class::isInstance)
        .map(ArcScriptResult.QueryPerformed.class::cast)
        .toList();
    assertThat(queries).hasSize(1);
    return queries.getFirst();
  }

  private static List<String> cellValues(final ArcScriptResult.QueryPerformed query,
                                         final String prop) {
    return query.resultSet().rows().stream()
        .map(row -> row.cells().get(prop))
        .map(cell -> cell == null || cell.value() == null ? null : String.valueOf(cell.value()))
        .toList();
  }

  @Test
  void unfilteredTypeQuery_bothEnginesReturnTheSameEntries() {
    final String script = """
        query {
          every 'User'
          from 'org'
        }""";

    final var legacy = run(script, legacy());
    final var pipelined = run(script, pipelined());

    assertThat(legacy.resultSet().size()).isPositive();
    assertThat(pipelined.resultSet().entries())
        .containsExactlyInAnyOrderElementsOf(legacy.resultSet().entries());
  }

  @Test
  void filteredQueryWithProjection_bothEnginesReturnTheSameRows() {
    final String script = """
        query {
          every 'User'
          from 'org'
          where { bool 'inactive' is false }
          show 'name', 'email'
        }""";

    final var legacy = run(script, legacy());
    final var pipelined = run(script, pipelined());

    assertThat(legacy.resultSet().size()).isPositive();
    assertThat(pipelined.resultSet().rows())
        .containsExactlyInAnyOrderElementsOf(legacy.resultSet().rows());
  }

  @Test
  void stringPredicate_bothEnginesReturnTheSameRows() {
    final String script = """
        query {
          every 'Group'
          from 'org'
          where { str 'name' contains 'a' }
          show 'name'
        }""";

    final var legacy = run(script, legacy());
    final var pipelined = run(script, pipelined());

    assertThat(pipelined.resultSet().rows())
        .containsExactlyInAnyOrderElementsOf(legacy.resultSet().rows());
  }

  @Test
  void orderedQuery_bothEnginesAgreeOnOrderingAndContent() {
    final String script = """
        query {
          every 'User'
          from 'org'
          order { by 'name' }
          show 'name'
        }""";

    final var legacy = run(script, legacy());
    final var pipelined = run(script, pipelined());

    final var legacyNames = cellValues(legacy, "name");
    final var pipelinedNames = cellValues(pipelined, "name");

    // multisets must agree...
    assertThat(pipelinedNames).containsExactlyInAnyOrderElementsOf(legacyNames);
    // ...and both must observe the ordering (ties may resolve differently, so we assert
    // sortedness rather than positional equality):
    assertThat(pipelinedNames).isSortedAccordingTo(
        Comparator.nullsLast(Comparator.naturalOrder()));
    assertThat(legacyNames).isSortedAccordingTo(
        Comparator.nullsLast(Comparator.naturalOrder()));
  }

  @Test
  void orderedAndLimitedQuery_bothEnginesAgreeOnTheTopK() {
    final String script = """
        query {
          every 'Group'
          from 'org'
          order { by 'name' desc }
          show 'name'
          limit 5
        }""";

    final var legacy = run(script, legacy());
    final var pipelined = run(script, pipelined());

    assertThat(pipelined.resultSet().size()).isEqualTo(5);
    assertThat(legacy.resultSet().size()).isEqualTo(5);
    // ties at the cut-off may resolve to different entries, but the projected values of the
    // top-K must be identical:
    assertThat(cellValues(pipelined, "name"))
        .containsExactlyElementsOf(cellValues(legacy, "name"));
  }

  @Test
  void limitedQueryWithoutOrder_pipelinedEngineCapsTheResultSetExactly() {
    final String script = """
        query {
          every 'Group'
          from 'org'
          where { str 'name' contains 'a' }
          limit 3
        }""";

    final var pipelined = run(script, pipelined());
    assertThat(pipelined.resultSet().size()).isEqualTo(3);

    final var stats = pipelined.pipelineStats();
    assertThat(stats).isNotNull();
  }

  @Test
  void volumeQuery_bothEnginesReturnTheSameEntries() {
    final String script = """
        query {
          from 'branch'
        }""";

    final var legacy = run(script, legacy());
    final var pipelined = run(script, pipelined());

    assertThat(legacy.resultSet().size()).isPositive();
    assertThat(pipelined.resultSet().entries())
        .containsExactlyInAnyOrderElementsOf(legacy.resultSet().entries());
  }

  @Test
  void tightlyBoundedCache_doesNotAffectResults() {
    final String script = """
        query {
          every 'User'
          from 'org'
          where { bool 'inactive' is false }
          show 'name'
        }""";

    final var reference = run(script, pipelined());
    final var bounded = run(script, QueryEngineSettings.builder()
        .engineMode(QueryEngineSettings.EngineMode.PIPELINED)
        .cacheMaxEntries(4L)
        .build());

    assertThat(bounded.resultSet().rows())
        .containsExactlyInAnyOrderElementsOf(reference.resultSet().rows());
  }

  @Test
  void pipelineStats_reflectTheExecution() {
    final String script = """
        query {
          every 'User'
          from 'org'
          order { by 'name' }
          show 'name'
        }""";

    final var pipelined = run(script, pipelined());
    final var stats = pipelined.pipelineStats();
    assertThat(stats).isNotNull();
    assertThat(stats.stages())
        .extracting(ArcScriptResult.StageStats::stage)
        .containsExactly("source", "where", "order", "yield");
    final long sourceOut = stats.stages().getFirst().entriesOut();
    assertThat(sourceOut).isEqualTo(pipelined.resultSet().size());
    assertThat(stats.stages()).allSatisfy(s -> assertThat(s.timeTaken()).isPositive());
  }

  @Test
  void limitedQueryWithUriThroughNavigation_bothEnginesAgree() {
    final var script = """
        def _id = "data.id"
        def _title = "data.name"
        def _owner = "owner.name"
        def _ownerUsername = "owner.username"
        query {
          every DocumentDossierContent
           from documentdossier
          where {
            str _ownerUsername not 'Migration'
          }
             order {
               by _id
               by _owner
               by _title
             }
          yield {
            col _id, _title, _owner
          }
        }""";
    final var pipelined = run(script, pipelined());
    final var legacy = run(script, legacy());
    assertThat(legacy.resultSet().size()).isPositive();
    assertThat(legacy.resultSet().size()).describedAs(() -> "Sizes match legacy -> pipelined")
        .isEqualTo(pipelined.resultSet().size());
    assertThat(pipelined.resultSet().entries())
        .containsExactlyInAnyOrderElementsOf(legacy.resultSet().entries());
    log.info("Sizes: {}", legacy.resultSet().size());

    final var legacyIds = cellValues(legacy, "data.id");
    final var pipelinedIds = cellValues(pipelined, "data.id");
    assertThat(pipelinedIds).isEqualTo(legacyIds);
  }

}

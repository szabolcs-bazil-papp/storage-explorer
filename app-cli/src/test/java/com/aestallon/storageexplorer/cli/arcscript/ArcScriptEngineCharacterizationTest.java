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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.smartbit4all.api.collection.CollectionApi;
import org.smartbit4all.api.config.PlatformApiConfig;
import org.smartbit4all.api.org.bean.User;
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
 * LEGACY vs PIPELINED characterization of ArcScript query execution against a synthetic,
 * <em>loadable</em> file-system storage built at test time - unlike
 * {@link ArcScriptEngineE2ETest}, this suite has no external dependency and runs everywhere.
 *
 * <p>
 * A platform context backed by a {@link StorageFS} over a temporary directory persists a set of
 * {@link User} objects with unique names, so {@code where} predicates, {@code order} clauses and
 * {@code show} projections all exercise real object loading through both engines.
 */
class ArcScriptEngineCharacterizationTest {

  private static final String SCHEMA = "chartest";
  private static final int USER_COUNT = 60;

  private static Path storagePath;
  private static AnnotationConfigApplicationContext ctx;
  private static StorageInstance storageInstance;

  @BeforeAll
  static void setUp() throws Exception {
    storagePath = Files.createTempDirectory("se-chartest");

    final var dto = new StorageInstanceDto()
        .id(UUID.randomUUID())
        .name("chartest")
        .availability(Availability.AVAILABLE)
        .indexingStrategy(IndexingStrategyType.ON_DEMAND)
        .type(StorageInstanceType.FS)
        .fs(new FsStorageLocation().path(storagePath));
    storageInstance = StorageInstance.fromDto(dto);

    ctx = new AnnotationConfigApplicationContext();
    ctx.register(PlatformApiConfig.class);
    ctx.registerBean(
        storageInstance.id().toString(),
        ObjectStorage.class,
        () -> new StorageFS(
            storagePath.toFile(),
            ctx.getBean(ObjectDefinitionApi.class)));
    final Map<String, Object> props = new HashMap<>();
    props.put("applicationruntime.maintain.enabled", "false");
    props.put("invocationregistry.refresh.enabled", "false");
    props.put("application.setup.enabled", "false");
    props.put("dataseries.mgmt.enabled", "false");
    ctx.getEnvironment().getPropertySources().addFirst(new MapPropertySource("default", props));
    ctx.refresh();

    final ObjectApi objectApi = ctx.getBean(ObjectApi.class);
    final List<java.net.URI> userUris = new java.util.ArrayList<>();
    for (int i = 0; i < USER_COUNT; i++) {
      userUris.add(objectApi.saveAsNew(SCHEMA, new User()
          .username("user_%02d".formatted(i))
          .name("User %02d".formatted(i))
          .email("user%02d@example.com".formatted(i))
          .inactive(i % 3 == 0)));
    }

    final CollectionApi collectionApi = ctx.getBean(CollectionApi.class);
    collectionApi.list(SCHEMA, "firstTen").addAll(userUris.subList(0, 10));
    final Map<String, java.net.URI> byUsername = new HashMap<>();
    for (int i = 0; i < 8; i++) {
      byUsername.put("user_%02d".formatted(i), userUris.get(i));
    }
    collectionApi.map(SCHEMA, "byUsername").putAll(byUsername);

    storageInstance.setIndex(new FileSystemStorageIndex(
        storageInstance.id(),
        objectApi,
        ctx.getBean(CollectionApi.class),
        storagePath,
        false));
  }

  @AfterAll
  static void tearDown() {
    if (ctx != null) {
      ctx.close();
    }
  }

  private static QueryEngineSettings.Builder settings(final QueryEngineSettings.EngineMode mode) {
    return QueryEngineSettings.builder().engineMode(mode);
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

  private static ArcScriptResult.QueryPerformed runLegacy(final String script) {
    return run(script, settings(QueryEngineSettings.EngineMode.LEGACY).build());
  }

  private static ArcScriptResult.QueryPerformed runPipelined(final String script) {
    return run(script, settings(QueryEngineSettings.EngineMode.PIPELINED).build());
  }

  private static List<String> names(final ArcScriptResult.QueryPerformed query) {
    return query.resultSet().rows().stream()
        .map(row -> row.cells().get("name"))
        .map(cell -> cell == null || cell.value() == null ? null : String.valueOf(cell.value()))
        .toList();
  }

  @Test
  void unfilteredQuery_bothEnginesReturnEveryUser() {
    final String script = """
        query {
          every 'User'
          from '%s'
        }""".formatted(SCHEMA);

    final var legacy = runLegacy(script);
    final var pipelined = runPipelined(script);

    assertThat(legacy.resultSet().size()).isEqualTo(USER_COUNT);
    assertThat(pipelined.resultSet().entries())
        .containsExactlyInAnyOrderElementsOf(legacy.resultSet().entries());
  }

  @Test
  void booleanPredicateWithProjection_bothEnginesReturnTheSameRows() {
    final String script = """
        query {
          every 'User'
          from '%s'
          where { bool 'inactive' is false }
          show 'name', 'email'
        }""".formatted(SCHEMA);

    final var legacy = runLegacy(script);
    final var pipelined = runPipelined(script);

    // every third user (i % 3 == 0) is inactive:
    assertThat(legacy.resultSet().size()).isEqualTo(USER_COUNT - USER_COUNT / 3);
    assertThat(pipelined.resultSet().rows())
        .containsExactlyInAnyOrderElementsOf(legacy.resultSet().rows());
  }

  @Test
  void stringPredicate_bothEnginesReturnTheSameRows() {
    final String script = """
        query {
          every 'User'
          from '%s'
          where { str 'name' contains '1' }
          show 'name'
        }""".formatted(SCHEMA);

    final var legacy = runLegacy(script);
    final var pipelined = runPipelined(script);

    assertThat(legacy.resultSet().size()).isPositive();
    assertThat(pipelined.resultSet().rows())
        .containsExactlyInAnyOrderElementsOf(legacy.resultSet().rows());
  }

  @Test
  void orderedQuery_bothEnginesReturnTheExactSameOrdering() {
    final String script = """
        query {
          every 'User'
          from '%s'
          order { by 'name' }
          show 'name'
        }""".formatted(SCHEMA);

    final var legacy = runLegacy(script);
    final var pipelined = runPipelined(script);

    // names are unique, so the two orderings must agree positionally:
    assertThat(names(pipelined))
        .hasSize(USER_COUNT)
        .isSortedAccordingTo(Comparator.naturalOrder())
        .containsExactlyElementsOf(names(legacy));
  }

  @Test
  void orderedAndLimitedQuery_bothEnginesReturnTheExactSameTopK() {
    final String script = """
        query {
          every 'User'
          from '%s'
          order { by 'name' desc }
          show 'name'
          limit 7
        }""".formatted(SCHEMA);

    final var legacy = runLegacy(script);
    final var pipelined = runPipelined(script);

    assertThat(names(pipelined))
        .hasSize(7)
        .containsExactlyElementsOf(names(legacy));
  }

  @Test
  void singleEntryQueryForm_returnsExactlyOneRow() {
    final String script = """
        query {
          a 'User'
          from '%s'
        }""".formatted(SCHEMA);

    // the pipelined engine enforces the limit exactly; the legacy engine merely stops STARTING
    // work once the limit is reached, so already-dispatched evaluations may race it past the
    // limit - this deliberate behavioural improvement is why the two engines are not compared
    // for equality here:
    assertThat(runPipelined(script).resultSet().size()).isEqualTo(1);
    assertThat(runLegacy(script).resultSet().size()).isGreaterThanOrEqualTo(1);
  }

  @Test
  void sortHardCap_neverTruncatesAnExplicitLimit_onLoadableData() {
    final String script = """
        query {
          every 'User'
          from '%s'
          order { by 'name' }
          show 'name'
          limit 20
        }""".formatted(SCHEMA);

    final var pipelined = run(script, settings(QueryEngineSettings.EngineMode.PIPELINED)
        .unboundedSortHardCap(5)
        .build());
    final var reference = runLegacy(script);

    assertThat(names(pipelined))
        .hasSize(20)
        .containsExactlyElementsOf(names(reference));
  }

  @Test
  void sortHardCap_capsAnUnboundedSort_keepingTheBestSortingEntries() {
    final String script = """
        query {
          every 'User'
          from '%s'
          order { by 'name' }
          show 'name'
        }""".formatted(SCHEMA);

    final var pipelined = run(script, settings(QueryEngineSettings.EngineMode.PIPELINED)
        .unboundedSortHardCap(5)
        .build());

    // the hard cap degrades the unbounded sort to top-K semantics: the 5 best-sorting entries
    // must be retained, in order:
    assertThat(names(pipelined))
        .containsExactly("User 00", "User 01", "User 02", "User 03", "User 04");
  }

  @Test
  void listSourcedQuery_bothEnginesReturnTheListedEntries() {
    final String script = """
        query {
          list 'firstTen'
          from '%s'
        }""".formatted(SCHEMA);

    final var legacy = runLegacy(script);
    final var pipelined = runPipelined(script);

    assertThat(legacy.resultSet().size()).isEqualTo(10);
    assertThat(pipelined.resultSet().entries())
        .containsExactlyInAnyOrderElementsOf(legacy.resultSet().entries());
  }

  @Test
  void listSourcedQueryWithPredicateAndProjection_bothEnginesReturnTheSameRows() {
    final String script = """
        query {
          list 'firstTen'
          from '%s'
          where { bool 'inactive' is false }
          order { by 'name' }
          show 'name'
        }""".formatted(SCHEMA);

    final var legacy = runLegacy(script);
    final var pipelined = runPipelined(script);

    // of the first ten users, indices 0, 3, 6 and 9 are inactive:
    assertThat(names(pipelined))
        .hasSize(6)
        .containsExactlyElementsOf(names(legacy));
  }

  @Test
  void mapSourcedQuery_bothEnginesReturnTheMappedEntries() {
    final String script = """
        query {
          map 'byUsername'
          from '%s'
        }""".formatted(SCHEMA);

    final var legacy = runLegacy(script);
    final var pipelined = runPipelined(script);

    assertThat(legacy.resultSet().size()).isEqualTo(8);
    assertThat(pipelined.resultSet().entries())
        .containsExactlyInAnyOrderElementsOf(legacy.resultSet().entries());
  }

  @Test
  void nonExistentCollection_failsTheQueryOnBothEngines() {
    final String script = """
        query {
          list 'noSuchList'
          from '%s'
        }""".formatted(SCHEMA);

    for (final var mode : QueryEngineSettings.EngineMode.values()) {
      final var result = Arc.evaluate(script, storageInstance, settings(mode).build());
      assertThat(result)
          .withFailMessage("Expected failure on %s engine, but got: %s", mode, result)
          .isInstanceOf(ArcScriptResult.UnknownError.class);
      assertThat(((ArcScriptResult.UnknownError) result).msg())
          .contains("noSuchList")
          .contains("does not exist");
    }
  }

  @Test
  void tightBackpressureAndTinyCache_produceTheSameRowsAsTheLegacyEngine() {
    final String script = """
        query {
          every 'User'
          from '%s'
          where { bool 'inactive' is false }
          order { by 'name' }
          show 'name', 'email'
        }""".formatted(SCHEMA);

    final var pipelined = run(script, settings(QueryEngineSettings.EngineMode.PIPELINED)
        .queueCapacity(1)
        .sourcePrefetch(1)
        .whereConcurrency(8)
        .yieldConcurrency(2)
        .cacheMaxEntries(2L)
        .build());
    final var legacy = runLegacy(script);

    assertThat(names(pipelined)).containsExactlyElementsOf(names(legacy));
    assertThat(pipelined.resultSet().rows())
        .containsExactlyInAnyOrderElementsOf(legacy.resultSet().rows());
  }

  /**
   * Since source acquisition became streaming, a query's source set is exactly what discovery
   * finds at execution time: entries deleted from the storage after a previous run no longer
   * linger in query results (historically they survived in the index cache until a full
   * re-index). Uses a dedicated schema so the shared fixture is unaffected.
   */
  @Test
  void deletedEntries_dropOutOfSubsequentQueryResults() throws Exception {
    final String schema = "deltest";
    final ObjectApi objectApi = ctx.getBean(ObjectApi.class);
    final List<java.net.URI> uris = new java.util.ArrayList<>();
    for (int i = 0; i < 3; i++) {
      uris.add(objectApi.saveAsNew(schema, new User()
          .username("doomed_%02d".formatted(i))
          .name("Doomed %02d".formatted(i))));
    }

    final String script = """
        query {
          every 'User'
          from '%s'
        }""".formatted(schema);
    assertThat(runPipelined(script).resultSet().size()).isEqualTo(3);
    assertThat(runLegacy(script).resultSet().size()).isEqualTo(3);

    // remove one persisted object from discovery by deleting its .o file (discovery keys on .o
    // files exclusively; the version artifacts stay behind, as the platform may still hold them
    // open on Windows - irrelevant here, an entry without its .o file is gone from the storage's
    // point of view):
    final java.net.URI victim = uris.getFirst();
    // saveAsNew returns a versioned URI (…uuid.v0); the .o file carries the unversioned name:
    final String victimPath = victim.getPath().replaceAll("\\.v\\d+$", "");
    final Path oFile = storagePath.resolve(Path.of(victim.getScheme() + victimPath + ".o"));
    assertThat(oFile).exists();
    Files.delete(oFile);

    assertThat(runPipelined(script).resultSet().size()).isEqualTo(2);
    assertThat(runLegacy(script).resultSet().size()).isEqualTo(2);
  }

}

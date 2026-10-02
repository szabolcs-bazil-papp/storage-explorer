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

package com.aestallon.storageexplorer.arcscript.engine;

import java.net.URI;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.aestallon.storageexplorer.arcscript.api.Arc;
import com.aestallon.storageexplorer.common.util.IO;
import com.aestallon.storageexplorer.core.model.entry.StorageEntry;
import com.aestallon.storageexplorer.core.model.entry.UriProperty;
import com.aestallon.storageexplorer.core.model.instance.StorageInstance;
import com.aestallon.storageexplorer.core.model.instance.dto.Availability;
import com.aestallon.storageexplorer.core.model.instance.dto.FsStorageLocation;
import com.aestallon.storageexplorer.core.model.instance.dto.IndexingStrategyType;
import com.aestallon.storageexplorer.core.model.instance.dto.StorageInstanceDto;
import com.aestallon.storageexplorer.core.model.instance.dto.StorageInstanceType;
import com.aestallon.storageexplorer.core.service.FileSystemStorageIndex;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises {@link ArrowQueryEngine} against a synthetic storage instance holding pre-minted,
 * never-loaded entries - mirroring {@link PipelinedQueryEngineTest}'s fixture. Predicate/projection
 * correctness against real, loadable data is covered elsewhere; this suite focuses on the engine's
 * own mechanics: source iteration, limit/order, cross-engine agreement, the Arrow-backed
 * {@link PropertySource} wiring into {@link ConditionEvaluator}, and the spill/pagination path.
 */
class ArrowQueryEngineTest {

  private static final int FOO_COUNT = 100;
  private static final int BAR_COUNT = 20;

  @TempDir
  Path tempDir;

  private StorageInstance storageInstance;

  @BeforeEach
  void setUp() {
    final var dto = new StorageInstanceDto()
        .id(UUID.randomUUID())
        .name("synthetic")
        .availability(Availability.AVAILABLE)
        .indexingStrategy(IndexingStrategyType.ON_DEMAND)
        .type(StorageInstanceType.FS)
        .fs(new FsStorageLocation().path(tempDir));
    storageInstance = StorageInstance.fromDto(dto);

    final var index = new FileSystemStorageIndex(
        storageInstance.id(),
        null,
        null,
        tempDir,
        false);
    storageInstance.setIndex(index);

    IntStream.range(0, FOO_COUNT)
        .mapToObj(i -> URI.create("test:/com_example_Foo/2026/1/1/1/1/foo-" + i))
        .forEach(this::mint);
    IntStream.range(0, BAR_COUNT)
        .mapToObj(i -> URI.create("test:/com_example_Bar/2026/1/1/1/1/bar-" + i))
        .forEach(this::mint);
  }

  private void mint(final URI uri) {
    try {
      final Path path = IO.uriToPath(tempDir, uri);
      java.nio.file.Files.createDirectories(path.getParent());
      java.nio.file.Files.createFile(path);
    } catch (final java.io.IOException e) {
      throw new java.io.UncheckedIOException(e);
    }
    final var result = storageInstance.index().getOrCreate(uri);
    if (result instanceof com.aestallon.storageexplorer.core.service.StorageIndex
        .EntryAcquisitionResult.New(StorageEntry entry)) {
      storageInstance.index().accept(uri, entry);
    }
  }

  private void mintCollection(final URI uri, final int size,
                              final java.util.function.IntFunction<UriProperty.Segment> segment) {
    final var result = storageInstance.index().getOrCreate(uri);
    assertThat(result)
        .isInstanceOf(com.aestallon.storageexplorer.core.service.StorageIndex
            .EntryAcquisitionResult.New.class);
    final var entry = ((com.aestallon.storageexplorer.core.service.StorageIndex
        .EntryAcquisitionResult.New) result).entry();
    entry.setUriProperties(IntStream.range(0, size)
        .mapToObj(i -> UriProperty.of(
            new UriProperty.Segment[] { segment.apply(i) },
            URI.create("test:/com_example_Foo/2026/1/1/1/1/foo-" + i)))
        .collect(java.util.stream.Collectors.toSet()));
    storageInstance.index().accept(uri, entry);
  }

  private static QueryEngineSettings arrow() {
    return QueryEngineSettings.builder()
        .engineMode(QueryEngineSettings.EngineMode.ARROW)
        .build();
  }

  private static ArcScriptResult.QueryPerformed soleQueryResult(final ArcScriptResult result) {
    assertThat(result).isInstanceOf(ArcScriptResult.Ok.class);
    final var ok = (ArcScriptResult.Ok) result;
    final var queries = ok.elements().stream()
        .filter(ArcScriptResult.QueryPerformed.class::isInstance)
        .map(ArcScriptResult.QueryPerformed.class::cast)
        .toList();
    assertThat(queries).hasSize(1);
    return queries.getFirst();
  }

  @Test
  void unfilteredQuery_returnsEveryEntryOfTheSchema() {
    final var result = Arc.evaluate("""
        query {
          from 'test'
        }""", storageInstance, arrow());

    final var query = soleQueryResult(result);
    assertThat(query.resultSet().size()).isEqualTo(FOO_COUNT + BAR_COUNT);
  }

  @Test
  void typeFilteredQuery_returnsOnlyMatchingEntries() {
    final var result = Arc.evaluate("""
        query {
          every 'Foo'
          from 'test'
        }""", storageInstance, arrow());

    final var query = soleQueryResult(result);
    assertThat(query.resultSet().size()).isEqualTo(FOO_COUNT);
    assertThat(query.resultSet().entries())
        .allSatisfy(it -> assertThat(it.uri().toString()).contains("Foo"));
  }

  @Test
  void unknownSchema_yieldsEmptyResultSet() {
    final var result = Arc.evaluate("""
        query {
          from 'nosuchschema'
        }""", storageInstance, arrow());

    final var query = soleQueryResult(result);
    assertThat(query.resultSet().size()).isZero();
  }

  @Test
  void limitWithoutOrder_capsTheResultSetExactly() {
    final var result = Arc.evaluate("""
        query {
          every 'Foo'
          from 'test'
          limit 7
        }""", storageInstance, arrow());

    final var query = soleQueryResult(result);
    assertThat(query.resultSet().size()).isEqualTo(7);
  }

  @Test
  void singleEntryQueryForm_returnsExactlyOneEntry() {
    final var result = Arc.evaluate("""
        query {
          a 'Foo'
          from 'test'
        }""", storageInstance, arrow());

    final var query = soleQueryResult(result);
    assertThat(query.resultSet().size()).isEqualTo(1);
  }

  @Test
  void sortedQuery_returnsEveryEntry() {
    final var result = Arc.evaluate("""
        query {
          every 'Foo'
          from 'test'
          order { by 'name' }
        }""", storageInstance, arrow());

    final var query = soleQueryResult(result);
    assertThat(query.resultSet().size()).isEqualTo(FOO_COUNT);
  }

  @Test
  void sortedQueryWithLimit_capsTheResultSetExactly() {
    final var result = Arc.evaluate("""
        query {
          every 'Foo'
          from 'test'
          order { by 'name' }
          limit 5
        }""", storageInstance, arrow());

    final var query = soleQueryResult(result);
    assertThat(query.resultSet().size()).isEqualTo(5);
  }

  @Test
  void arrowAndLegacyEngines_returnTheSameEntries() {
    final String script = """
        query {
          every 'Bar'
          from 'test'
        }""";

    final var arrowResult = soleQueryResult(Arc.evaluate(script, storageInstance, arrow()));
    final var legacy = soleQueryResult(Arc.evaluate(
        script,
        storageInstance,
        QueryEngineSettings.builder()
            .engineMode(QueryEngineSettings.EngineMode.LEGACY)
            .build()));

    assertThat(arrowResult.resultSet().entries())
        .containsExactlyInAnyOrderElementsOf(legacy.resultSet().entries());
  }

  @Test
  void listSourcedQuery_returnsTheEntriesReferencedByTheStoredList() {
    mintCollection(
        URI.create("test-collections:/storedlist/mylist-s"),
        10,
        UriProperty.Segment::idx);

    final var result = Arc.evaluate("""
        query {
          list 'mylist'
          from 'test'
        }""", storageInstance, arrow());

    final var query = soleQueryResult(result);
    assertThat(query.resultSet().size()).isEqualTo(10);
    assertThat(query.resultSet().entries())
        .allSatisfy(it -> assertThat(it.uri().toString()).contains("Foo"));
  }

  @Test
  void mapSourcedQuery_returnsTheEntriesReferencedByTheStoredMap() {
    mintCollection(
        URI.create("test-collections:/storedmap/mymap-s"),
        5,
        i -> UriProperty.Segment.key("key-" + i));

    final var result = Arc.evaluate("""
        query {
          map 'mymap'
          from 'test'
        }""", storageInstance, arrow());

    final var query = soleQueryResult(result);
    assertThat(query.resultSet().size()).isEqualTo(5);
  }

  @Test
  void nonExistentCollection_failsTheQuery() {
    final var result = Arc.evaluate("""
        query {
          list 'no-such-list'
          from 'test'
        }""", storageInstance, arrow());

    assertThat(result).isInstanceOf(ArcScriptResult.UnknownError.class);
    assertThat(((ArcScriptResult.UnknownError) result).msg())
        .contains("no-such-list")
        .contains("does not exist");
  }

  @Test
  void collectionQueryWithMultipleSchemas_isImpermissible() {
    final var result = Arc.evaluate("""
        query {
          list 'mylist'
          from 'test', 'other'
        }""", storageInstance, arrow());

    assertThat(result).isInstanceOf(ArcScriptResult.ImpermissibleInstruction.class);
  }

  @Test
  void wherePredicate_isEmpty_matchesEveryUnloadableEntry() {
    // entries are never loaded from disk in this fixture, so property discovery on any of them
    // always yields "no value" - this exercises the Arrow-backed PropertySource feeding into
    // ConditionEvaluator end to end, not just a trivially-true null condition:
    final var result = Arc.evaluate("""
        query {
          every 'Foo'
          from 'test'
          where { str 'name' is_empty() }
        }""", storageInstance, arrow());

    final var query = soleQueryResult(result);
    assertThat(query.resultSet().size()).isEqualTo(FOO_COUNT);
  }

  @Test
  void wherePredicate_isPresent_matchesNothingForUnloadableEntries() {
    final var result = Arc.evaluate("""
        query {
          every 'Foo'
          from 'test'
          where { str 'name' is_present() }
        }""", storageInstance, arrow());

    final var query = soleQueryResult(result);
    assertThat(query.resultSet().size()).isZero();
  }

  @Test
  void hugeResult_spillsToDiskAndPaginatesConsistently() {
    final var settings = QueryEngineSettings.builder()
        .engineMode(QueryEngineSettings.EngineMode.ARROW)
        .arrowSpillRowLimit(5)
        .arrowBatchSize(3)
        .arrowPageSize(10)
        .build();

    final var config = ArcScriptEngineConfiguration.of(settings);
    final var engine = new ArcScriptEngine(config);
    final var script = Arc.compile("""
        query {
          from 'test'
          order { by 'name' }
        }""");
    final var result = engine.execute(script, storageInstance);

    final var query = soleQueryResult(result);
    assertThat(query.resultSet().size()).isEqualTo(10);

    final var pageInfo = query.pageInfo();
    assertThat(pageInfo).isNotNull();
    assertThat(pageInfo.totalRows()).isEqualTo(FOO_COUNT + BAR_COUNT);
    assertThat(pageInfo.pageSize()).isEqualTo(10);
    assertThat(pageInfo.totalPages()).isEqualTo((FOO_COUNT + BAR_COUNT) / 10);

    final var store = config.arrowResultStore().orElseThrow();
    final Set<StorageEntry> seen = new HashSet<>(query.resultSet().entries());
    for (int page = 1; page < pageInfo.totalPages(); page++) {
      final var fetched = store.fetchPage(pageInfo.resultToken(), page, pageInfo.pageSize())
          .orElseThrow();
      assertThat(fetched.size()).isEqualTo(pageInfo.pageSize());
      seen.addAll(fetched.entries());
    }
    assertThat(seen).hasSize(FOO_COUNT + BAR_COUNT);

    // a page beyond the end is empty rather than an error:
    final var beyond = store.fetchPage(pageInfo.resultToken(), pageInfo.totalPages(), pageInfo.pageSize());
    assertThat(beyond).isPresent();
    assertThat(beyond.orElseThrow().size()).isZero();
  }

  @Test
  void smallResult_neverGetsAPageInfo() {
    final var settings = QueryEngineSettings.builder()
        .engineMode(QueryEngineSettings.EngineMode.ARROW)
        .arrowSpillRowLimit(5)
        .arrowBatchSize(3)
        .arrowPageSize(1_000)
        .build();

    final var result = Arc.evaluate("""
        query {
          every 'Bar'
          from 'test'
        }""", storageInstance, settings);

    final var query = soleQueryResult(result);
    assertThat(query.resultSet().size()).isEqualTo(BAR_COUNT);
    assertThat(query.pageInfo()).isNull();
  }

}

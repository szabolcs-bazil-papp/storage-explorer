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
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.aestallon.storageexplorer.arcscript.api.Arc;
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
 * Exercises the pipeline mechanics (source iteration, where fan-out, limit capping, early
 * termination, result assembly and instrumentation) of the pipelined engine against a synthetic
 * storage instance holding pre-minted entries.
 *
 * <p>
 * The entries are never loaded from disk (there is no {@code where} predicate or {@code show}
 * column touching their properties), so no smartbit4all platform context is required. Predicate and
 * projection <em>correctness</em> against real, loadable data is covered by the LEGACY vs PIPELINED
 * characterization tests in the {@code app-cli} module.
 */
class PipelinedQueryEngineTest {

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
    final var result = storageInstance.index().getOrCreate(uri);
    if (result instanceof com.aestallon.storageexplorer.core.service.StorageIndex
        .EntryAcquisitionResult.New(StorageEntry entry)) {
      storageInstance.index().accept(uri, entry);
    }
  }

  /**
   * Mints a stored collection entry holding references to the first {@code size} Foo entries -
   * pre-validated, as the synthetic storage holds no loadable content.
   */
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

  private static QueryEngineSettings pipelined() {
    return QueryEngineSettings.builder()
        .engineMode(QueryEngineSettings.EngineMode.PIPELINED)
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
        }""", storageInstance, pipelined());

    final var query = soleQueryResult(result);
    assertThat(query.resultSet().size()).isEqualTo(FOO_COUNT + BAR_COUNT);
  }

  @Test
  void typeFilteredQuery_returnsOnlyMatchingEntries() {
    final var result = Arc.evaluate("""
        query {
          every 'Foo'
          from 'test'
        }""", storageInstance, pipelined());

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
        }""", storageInstance, pipelined());

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
        }""", storageInstance, pipelined());

    final var query = soleQueryResult(result);
    assertThat(query.resultSet().size()).isEqualTo(7);
  }

  @Test
  void limitWithoutOrder_withEarlyTerminationDisabled_stillCapsTheResultSetExactly() {
    final var settings = QueryEngineSettings.builder()
        .engineMode(QueryEngineSettings.EngineMode.PIPELINED)
        .earlyTerminationEnabled(false)
        .build();
    final var result = Arc.evaluate("""
        query {
          every 'Foo'
          from 'test'
          limit 7
        }""", storageInstance, settings);

    final var query = soleQueryResult(result);
    assertThat(query.resultSet().size()).isEqualTo(7);
  }

  @Test
  void pipelinedAndLegacyEngines_returnTheSameEntries() {
    final String script = """
        query {
          every 'Bar'
          from 'test'
        }""";

    final var pipelined = soleQueryResult(Arc.evaluate(script, storageInstance, pipelined()));
    final var legacy = soleQueryResult(Arc.evaluate(
        script,
        storageInstance,
        QueryEngineSettings.builder()
            .engineMode(QueryEngineSettings.EngineMode.LEGACY)
            .build()));

    assertThat(pipelined.resultSet().entries())
        .containsExactlyInAnyOrderElementsOf(legacy.resultSet().entries());
  }

  @Test
  void stageTimings_areAttachedWhenEnabled() {
    final var result = Arc.evaluate("""
        query {
          every 'Foo'
          from 'test'
        }""", storageInstance, pipelined());

    final var query = soleQueryResult(result);
    final var stats = query.pipelineStats();
    assertThat(stats).isNotNull();
    assertThat(stats.stages())
        .extracting(ArcScriptResult.StageStats::stage)
        .containsExactly("source", "where", "yield");

    final var source = stats.stages().get(0);
    final var where = stats.stages().get(1);
    final var yield = stats.stages().get(2);
    assertThat(source.entriesOut()).isEqualTo(FOO_COUNT);
    assertThat(where.entriesIn()).isEqualTo(FOO_COUNT);
    assertThat(where.entriesOut()).isEqualTo(FOO_COUNT);
    assertThat(yield.entriesIn()).isEqualTo(FOO_COUNT);
    assertThat(yield.entriesOut()).isEqualTo(FOO_COUNT);
    assertThat(stats.stages()).allSatisfy(
        it -> assertThat(it.timeTaken()).isPositive());
  }

  @Test
  void stageTimings_areOmittedWhenDisabled() {
    final var settings = QueryEngineSettings.builder()
        .engineMode(QueryEngineSettings.EngineMode.PIPELINED)
        .collectStageTimings(false)
        .build();
    final var result = Arc.evaluate("""
        query {
          from 'test'
        }""", storageInstance, settings);

    final var query = soleQueryResult(result);
    assertThat(query.pipelineStats()).isNull();
  }

  @Test
  void sortedQuery_containsOrderStage() {
    final var result = Arc.evaluate("""
        query {
          every 'Foo'
          from 'test'
          order { by 'name' }
        }""", storageInstance, pipelined());

    final var query = soleQueryResult(result);
    // the entries are not loadable, so every sort property discovery yields "no value" - the
    // point here is that the ordering stage is part of the pipeline and every entry passes
    // through it without loss:
    assertThat(query.resultSet().size()).isEqualTo(FOO_COUNT);
    assertThat(query.pipelineStats()).isNotNull();
    assertThat(query.pipelineStats().stages())
        .extracting(ArcScriptResult.StageStats::stage)
        .containsExactly("source", "where", "order", "yield");
  }

  @Test
  void sortedQueryWithLimit_capsTheResultSetExactly() {
    final var result = Arc.evaluate("""
        query {
          every 'Foo'
          from 'test'
          order { by 'name' }
          limit 5
        }""", storageInstance, pipelined());

    final var query = soleQueryResult(result);
    assertThat(query.resultSet().size()).isEqualTo(5);
  }

  @Test
  void singleEntryQueryForm_returnsExactlyOneEntry() {
    final var result = Arc.evaluate("""
        query {
          a 'Foo'
          from 'test'
        }""", storageInstance, pipelined());

    final var query = soleQueryResult(result);
    assertThat(query.resultSet().size()).isEqualTo(1);
  }

  @Test
  void tightBackpressureSettings_deliverEveryEntry() {
    final var settings = QueryEngineSettings.builder()
        .engineMode(QueryEngineSettings.EngineMode.PIPELINED)
        .queueCapacity(1)
        .sourcePrefetch(1)
        .whereConcurrency(64)
        .yieldConcurrency(2)
        .build();
    final var result = Arc.evaluate("""
        query {
          from 'test'
        }""", storageInstance, settings);

    final var query = soleQueryResult(result);
    assertThat(query.resultSet().size()).isEqualTo(FOO_COUNT + BAR_COUNT);
  }

  @Test
  void earlyTermination_stopsTheSourceBeforeExhaustion() {
    // sequential evaluation (concurrency 1) with minimal demand and buffering makes the
    // observable effect deterministic: the source cannot have raced far ahead of a limit-1
    // query's first survivor:
    final var settings = QueryEngineSettings.builder()
        .engineMode(QueryEngineSettings.EngineMode.PIPELINED)
        .queueCapacity(1)
        .sourcePrefetch(1)
        .whereConcurrency(1)
        .build();
    final var result = Arc.evaluate("""
        query {
          every 'Foo'
          from 'test'
          limit 1
        }""", storageInstance, settings);

    final var query = soleQueryResult(result);
    assertThat(query.resultSet().size()).isEqualTo(1);

    final var stats = query.pipelineStats();
    assertThat(stats).isNotNull();
    final var source = stats.stages().getFirst();
    final var where = stats.stages().get(1);
    assertThat(where.earlyTerminated()).isTrue();
    assertThat(source.entriesOut()).isLessThan(FOO_COUNT);
  }

  @Test
  void sortHardCap_capsAnOtherwiseUnboundedSort_andFlagsTheTruncation() {
    final var settings = QueryEngineSettings.builder()
        .engineMode(QueryEngineSettings.EngineMode.PIPELINED)
        .unboundedSortHardCap(10)
        .build();
    final var result = Arc.evaluate("""
        query {
          every 'Foo'
          from 'test'
          order { by 'name' }
        }""", storageInstance, settings);

    final var query = soleQueryResult(result);
    assertThat(query.resultSet().size()).isEqualTo(10);
    final var orderStage = query.pipelineStats().stages().stream()
        .filter(it -> "order".equals(it.stage()))
        .findFirst()
        .orElseThrow();
    assertThat(orderStage.earlyTerminated()).isTrue();
  }

  @Test
  void sortHardCap_neverTruncatesAnExplicitQueryLimit() {
    final var settings = QueryEngineSettings.builder()
        .engineMode(QueryEngineSettings.EngineMode.PIPELINED)
        .unboundedSortHardCap(10)
        .build();
    final var result = Arc.evaluate("""
        query {
          every 'Foo'
          from 'test'
          order { by 'name' }
          limit 25
        }""", storageInstance, settings);

    final var query = soleQueryResult(result);
    // the user asked for 25 rows and there are enough survivors - the hard cap (10) must not
    // override the explicit limit:
    assertThat(query.resultSet().size()).isEqualTo(25);
  }

  @Test
  void unboundedSortWarnThreshold_doesNotAffectResults() {
    final var settings = QueryEngineSettings.builder()
        .engineMode(QueryEngineSettings.EngineMode.PIPELINED)
        .unboundedSortWarnThreshold(10)
        .build();
    final var result = Arc.evaluate("""
        query {
          every 'Foo'
          from 'test'
          order { by 'name' }
        }""", storageInstance, settings);

    final var query = soleQueryResult(result);
    assertThat(query.resultSet().size()).isEqualTo(FOO_COUNT);
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
        }""", storageInstance, pipelined());

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
        }""", storageInstance, pipelined());

    final var query = soleQueryResult(result);
    assertThat(query.resultSet().size()).isEqualTo(5);
  }

  @Test
  void listSourcedQuery_agreesAcrossEngines() {
    mintCollection(
        URI.create("test-collections:/storedlist/sharedlist-s"),
        8,
        UriProperty.Segment::idx);

    final String script = """
        query {
          list 'sharedlist'
          from 'test'
        }""";
    final var pipelined = soleQueryResult(Arc.evaluate(script, storageInstance, pipelined()));
    final var legacy = soleQueryResult(Arc.evaluate(
        script,
        storageInstance,
        QueryEngineSettings.builder()
            .engineMode(QueryEngineSettings.EngineMode.LEGACY)
            .build()));

    assertThat(pipelined.resultSet().entries())
        .containsExactlyInAnyOrderElementsOf(legacy.resultSet().entries());
  }

  @Test
  void nonExistentCollection_failsTheQuery() {
    final var result = Arc.evaluate("""
        query {
          list 'no-such-list'
          from 'test'
        }""", storageInstance, pipelined());

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
        }""", storageInstance, pipelined());

    assertThat(result).isInstanceOf(ArcScriptResult.ImpermissibleInstruction.class);
  }

  @Test
  void boundedCache_doesNotAffectResults() {
    final var settings = QueryEngineSettings.builder()
        .engineMode(QueryEngineSettings.EngineMode.PIPELINED)
        .cacheMaxEntries(2L)
        .build();
    final var result = Arc.evaluate("""
        query {
          from 'test'
        }""", storageInstance, settings);

    final var query = soleQueryResult(result);
    assertThat(query.resultSet().size()).isEqualTo(FOO_COUNT + BAR_COUNT);
  }

}

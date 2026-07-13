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

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.aestallon.storageexplorer.arcscript.internal.query.QueryInstructionImpl;
import com.aestallon.storageexplorer.core.model.entry.StorageEntry;
import com.aestallon.storageexplorer.core.model.instance.StorageInstance;
import com.aestallon.storageexplorer.core.service.StorageInstanceExaminer;

/**
 * Push-pull, pipelined {@link QueryEngine} implementation.
 *
 * <p>
 * Instead of executing the {@code where} / {@code order} / {@code yield} phases as sequential,
 * barrier-synchronized passes (see {@link QueryEngineImpl}), this engine wires them into a
 * {@link java.util.concurrent.Flow} pipeline sharing a single virtual thread executor and a single
 * (optionally size-bounded) load cache:
 *
 * <pre>{@code
 * EntrySourcePublisher --> WhereProcessor --> [OrderingStage] --> YieldProcessor --> ResultCollector
 * }</pre>
 *
 * Entries flow through the stages individually: {@code where} evaluation starts on the first entry
 * before the source set iteration finishes, survivors are presorted into a bounded heap while
 * upstream evaluation is still in progress, and {@code show} columns are rendered as ordered
 * entries become available. Every knob is drawn from {@link QueryEngineSettings}.
 *
 * <p>
 * Timing semantics deliberately differ from the legacy engine:
 * {@link ArcScriptResult.QueryPerformed#timeTaken()} covers the <em>entire</em> execution,
 * including column rendering (legacy excluded the render pass), and
 * {@link ArcScriptResult.ResultSetMeta#timeTaken()} spans from the yield stage's first processed
 * element until its completion - with overlapping stages, an exact "render-only" duration does not
 * exist. Cross-engine timing comparisons should therefore rely on total wall time, or on the
 * per-stage {@link ArcScriptResult.PipelineStats}.
 */
public final class PipelinedQueryEngine implements QueryEngine {

  private static final Logger log = LoggerFactory.getLogger(PipelinedQueryEngine.class);
  private final QueryEngineSettings settings;

  public PipelinedQueryEngine(final QueryEngineSettings settings) {
    this.settings = Objects.requireNonNull(settings, "settings cannot be null!");
  }

  @Override
  public ArcScriptResult.InstructionResult execute(final StorageInstance storageInstance,
                                                   final QueryInstructionImpl query) {
    final long start = System.nanoTime();

    // collection-existence failures throw here, before any pipeline machinery spins up; for
    // type-based queries this is a lazily populated discovery stream - the source stage owns and
    // closes it (with a belt-and-braces close below for pipelines failing before the producer
    // finishes):
    final Stream<StorageEntry> entries = QuerySourceResolver.resolveStream(storageInstance, query);
    final var examiner = storageInstance.examiner();
    final var cache = settings.cacheMaxEntries() > 0L
        ? StorageInstanceExaminer.ObjectEntryLookupTable.adaptive(
        settings.cacheMaxEntries(),
        settings.cacheExpireAfterAccess())
        : StorageInstanceExaminer.ObjectEntryLookupTable.newInstance();

    final boolean sorted = !query._sortKeys.isEmpty();
    final long limit = query._limit;
    final List<ArcScriptResult.ColumnDescriptor> columns = query._columns.stream()
        .map(it -> new ArcScriptResult.ColumnDescriptor(
            it.propertyInternal(),
            it.displayNameInternal()))
        .toList();

    final var metrics = new PipelineMetrics();
    final List<ArcScriptResult.QueryResultRow> rows;
    final PipelineMetrics.StageMetrics yieldMetrics;
    // deliberately NOT try-with-resources: ExecutorService.close() awaits task termination, so on
    // a failed or timed-out pipeline it would block on the very tasks that wedged - the executor
    // is closed orderly on success and abandoned via shutdownNow() on failure instead:
    final var executor = Executors.newVirtualThreadPerTaskExecutor();
    boolean orderly = false;
    try {
      final var source = new EntrySourcePublisher(
          entries,
          executor,
          settings.queueCapacity(),
          metrics.register("source"));
      final var where = new WhereProcessor(
          executor,
          settings,
          metrics.register("where"),
          examiner,
          cache,
          query.condition,
          // when sorting, the limit selects the top of the full ordering, so every survivor must
          // still be examined - the cap is applied by the ordering stage instead:
          sorted ? -1L : limit);
      final var ordering = sorted
          ? new OrderingStage(
          executor,
          settings,
          metrics.register("order"),
          examiner,
          cache,
          query._sortKeys,
          limit)
          : null;
      yieldMetrics = metrics.register("yield");
      final var y = new YieldProcessor(
          executor,
          settings,
          yieldMetrics,
          examiner,
          cache,
          columns);
      final var collector = new ResultCollector();

      source.subscribe(where);
      if (ordering != null) {
        where.subscribe(ordering);
        ordering.subscribe(y);
      } else {
        where.subscribe(y);
      }
      y.subscribe(collector);

      source.start();
      try {
        final var timeout = settings.queryTimeout();
        rows = (timeout == null)
            ? collector.future().join()
            : collector.future().get(timeout.toNanos(), TimeUnit.NANOSECONDS);
        orderly = true;
      } catch (final CompletionException | ExecutionException e) {
        throw new IllegalStateException(
            "Query pipeline execution failed: " + e.getCause(),
            e.getCause());
      } catch (final TimeoutException e) {
        throw new IllegalStateException(
            "Query pipeline failed to complete within " + settings.queryTimeout()
            + " - aborting instead of blocking the caller indefinitely.",
            e);
      } catch (final InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("Query pipeline execution interrupted.", e);
      }
    } finally {
      // idempotent double close on the happy path; on timeout/failure this releases the walker
      // threads / database cursor even if the producer virtual thread is wedged:
      entries.close();
      if (orderly) {
        executor.close();
      } else {
        executor.shutdownNow();
      }
    }

    final var meta = new ArcScriptResult.ResultSetMeta(
        columns.isEmpty() ? Collections.emptyList() : columns,
        columns.isEmpty() ? -1L : yieldMetrics.timeTaken());
    final var resultSet = new ArcScriptResult.ResultSet(meta, rows);
    final var stats = metrics.toStats();
    log.info("Stats: {}", stats);
    return new ArcScriptResult.QueryPerformed(
        query.toString(),
        resultSet,
        System.nanoTime() - start,
        settings.collectStageTimings() ? stats : null);
  }

}

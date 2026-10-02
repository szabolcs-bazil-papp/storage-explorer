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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.arrow.memory.RootAllocator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.aestallon.storageexplorer.arcscript.api.SortInstruction;
import com.aestallon.storageexplorer.arcscript.internal.query.Assertion;
import com.aestallon.storageexplorer.arcscript.internal.query.QueryConditionImpl;
import com.aestallon.storageexplorer.arcscript.internal.query.QueryElement;
import com.aestallon.storageexplorer.arcscript.internal.query.QueryInstructionImpl;
import com.aestallon.storageexplorer.core.model.entry.StorageEntry;
import com.aestallon.storageexplorer.core.model.instance.StorageInstance;
import com.aestallon.storageexplorer.core.service.StorageInstanceExaminer;

/**
 * Columnar {@link QueryEngine} implementation backed by {@link ArrowColumnStore}.
 *
 * <p>
 * Unlike {@link QueryEngineImpl} (eager, sequential passes) and {@link PipelinedQueryEngine}
 * (concurrent reactive stages, wholly in-memory), this engine discovers the union of every property
 * referenced by the query's {@code where}, {@code order} and {@code show}/{@code yield} clauses
 * exactly once per source entry into a shared Arrow-columnar projection ({@link ArrowColumnStore}),
 * which transparently spills to a temp file once
 * {@link QueryEngineSettings#arrowSpillRowLimit()} rows have been produced. The {@code where}
 * predicate, {@code order} comparison and final {@code yield} projection are then all evaluated by
 * reading back from that same store, resident or spilled.
 *
 * <p>
 * Execution proceeds in three phases:
 * <ol>
 * <li><b>Projection</b>: the source stream is discovered with bounded virtual-thread concurrency,
 * each entry contributing one row to the store;</li>
 * <li><b>Filter + order</b>: a single scan over the (possibly spilled) store evaluates {@code where}
 * for every row via a patched {@link ConditionEvaluator} sourcing single-valued assertions from the
 * store instead of re-discovering them, and maintains the survivor ordering - a bounded top-K heap
 * when a limit (or unbounded-sort hard cap) applies, a single final sort otherwise;</li>
 * <li><b>Yield</b>: the {@code show} columns of the final, bounded row selection are materialised
 * from the store. If that selection is larger than {@link QueryEngineSettings#arrowPageSize()}, the
 * store is (if it had not already) spilled and handed off to {@link ArrowResultSetStore} under a
 * token, and only the first page is returned eagerly - the rest is fetched on demand.</li>
 * </ol>
 *
 * <p>
 * List-quantifier assertions ({@code any_match}/{@code all_match}/{@code none_match}) have no flat
 * per-row columnar representation and are evaluated exactly as {@link QueryEngineImpl} and
 * {@link PipelinedQueryEngine} evaluate them: directly against the examiner.
 */
public final class ArrowQueryEngine implements QueryEngine {

  private static final Logger log = LoggerFactory.getLogger(ArrowQueryEngine.class);

  private final QueryEngineSettings settings;
  private final ArrowResultSetStore resultStore;

  public ArrowQueryEngine(final QueryEngineSettings settings) {
    this.settings = settings;
    this.resultStore = new ArrowResultSetStore(settings.arrowResultRetention());
  }

  /** The pagination registry backing this engine's large results - shared across every query. */
  public ArrowResultSetStore resultStore() {
    return resultStore;
  }

  @Override
  public ArcScriptResult.InstructionResult execute(final StorageInstance storageInstance,
                                                   final QueryInstructionImpl query) {
    final long start = System.nanoTime();
    final var examiner = storageInstance.examiner();
    final var cache = settings.cacheMaxEntries() > 0L
        ? StorageInstanceExaminer.ObjectEntryLookupTable.adaptive(
        settings.cacheMaxEntries(), settings.cacheExpireAfterAccess())
        : StorageInstanceExaminer.ObjectEntryLookupTable.newInstance();

    final List<ArcScriptResult.ColumnDescriptor> showColumns = query._columns.stream()
        .map(it -> new ArcScriptResult.ColumnDescriptor(
            it.propertyInternal(), it.displayNameInternal()))
        .toList();
    final Map<String, String> displayNames = new LinkedHashMap<>();
    for (final var c : showColumns) {
      displayNames.put(c.prop(), c.title());
    }
    final List<String> showColumnNames = List.copyOf(displayNames.keySet());

    final Set<String> planned = new LinkedHashSet<>();
    collectSingleValueProps(query.condition, planned);
    for (final SortInstruction.SortKey sk : query._sortKeys) {
      planned.add(sk.target());
    }
    planned.addAll(showColumnNames);
    final List<String> properties = List.copyOf(planned);

    final var allocator = new RootAllocator(Long.MAX_VALUE);
    final var store = new ArrowColumnStore(
        allocator, properties, settings.arrowSpillRowLimit(), settings.arrowBatchSize(),
        settings.arrowSpillDirectory());

    boolean handedOff = false;
    try {
      project(storageInstance, query, examiner, cache, properties, store);
      store.finish();

      final List<Candidate> survivors = filterSortLimit(store, query, examiner, cache);
      final long[] globalIndices = new long[survivors.size()];
      final StorageEntry[] finalEntries = new StorageEntry[survivors.size()];
      for (int i = 0; i < survivors.size(); i++) {
        globalIndices[i] = survivors.get(i).globalIndex();
        finalEntries[i] = survivors.get(i).entry();
      }

      final int pageSize = settings.arrowPageSize();
      final ArcScriptResult.ResultSet resultSet;
      ArcScriptResult.PageInfo pageInfo = null;
      final long yieldStart = System.nanoTime();

      if (globalIndices.length <= pageSize) {
        final var rows = materializeEagerly(store, globalIndices, finalEntries, showColumnNames);
        resultSet = new ArcScriptResult.ResultSet(
            new ArcScriptResult.ResultSetMeta(
                showColumns, showColumns.isEmpty() ? -1L : System.nanoTime() - yieldStart),
            rows);
      } else {
        if (!store.spilled()) {
          store.forceSpill();
        }
        final var spillFile = store.detachSpillFile();
        final var batchSizes = store.flushedBatchSizes();
        final String token = resultStore.register(
            spillFile, batchSizes, showColumnNames, displayNames, globalIndices, finalEntries,
            allocator);
        handedOff = true;

        final int totalPages = (int) Math.ceil((double) globalIndices.length / pageSize);
        pageInfo = new ArcScriptResult.PageInfo(token, pageSize, globalIndices.length, totalPages);
        resultSet = resultStore.fetchPage(token, 0, pageSize).orElseThrow();
      }

      return new ArcScriptResult.QueryPerformed(
          query.toString(), resultSet, System.nanoTime() - start, null, pageInfo);
    } catch (final RuntimeException e) {
      throw e;
    } catch (final Exception e) {
      throw new IllegalStateException("Arrow query execution failed: " + e.getMessage(), e);
    } finally {
      store.close();
      if (!handedOff) {
        final var f = store.spillFile();
        if (f != null) {
          try {
            Files.deleteIfExists(f);
          } catch (final IOException ignored) {
            // best effort cleanup
          }
        }
        allocator.close();
      }
    }
  }

  private void project(final StorageInstance storageInstance, final QueryInstructionImpl query,
                       final StorageInstanceExaminer examiner,
                       final StorageInstanceExaminer.ObjectEntryLookupTable cache,
                       final List<String> properties, final ArrowColumnStore store) {
    final int concurrency = settings.whereConcurrency();
    final Semaphore permits = concurrency > 0 ? new Semaphore(concurrency) : null;
    try (var executor = Executors.newVirtualThreadPerTaskExecutor();
         var entries = QuerySourceResolver.resolveStream(storageInstance, query)) {
      entries.forEach(entry -> executor.submit(() -> {
        if (permits != null) {
          permits.acquireUninterruptibly();
        }
        try {
          final var discovered = new HashMap<String, StorageInstanceExaminer.PropertyDiscoveryResult>(
              properties.size() * 2);
          for (final String prop : properties) {
            discovered.put(prop, examiner.discoverProperty(entry, prop, cache));
          }
          store.append(entry, discovered);
        } catch (final Exception e) {
          log.error("Error occurred during projection of [ {} ]: {}", entry, e.getMessage());
          log.debug(e.getMessage(), e);
        } finally {
          if (permits != null) {
            permits.release();
          }
        }
      }));
    }
  }

  private record Candidate(long globalIndex, StorageEntry entry,
      ArcScriptResult.QueryResultRow sortRow) {}

  private List<Candidate> filterSortLimit(final ArrowColumnStore store,
                                          final QueryInstructionImpl query,
                                          final StorageInstanceExaminer examiner,
                                          final StorageInstanceExaminer.ObjectEntryLookupTable cache)
      throws Exception {
    final boolean sorted = !query._sortKeys.isEmpty();
    final long limit = query._limit;
    final Comparator<ArcScriptResult.QueryResultRow> comparator = sorted
        ? RowComparators.of(query._sortKeys)
        : null;
    final long hardCap = settings.unboundedSortHardCap();
    final long effectiveCap = limit > 0L ? limit : (sorted ? hardCap : -1L);
    final boolean hardCapEngaged = sorted && limit <= 0L && hardCap > 0L;
    final int warnThreshold = settings.unboundedSortWarnThreshold();
    final AtomicBoolean warned = new AtomicBoolean(false);

    final PriorityQueue<Candidate> topK = (sorted && effectiveCap > 0L)
        ? new PriorityQueue<>(
        (int) Math.min(effectiveCap, 1_024L),
        Comparator.comparing(Candidate::sortRow, comparator).reversed())
        : null;
    final List<Candidate> buffer = (sorted && effectiveCap <= 0L) ? new ArrayList<>() : null;
    final List<Candidate> unsorted = sorted ? null : new ArrayList<>();

    store.forEachRow((globalIndex, entry, propertySource) -> {
      if (!sorted && limit > 0L && unsorted.size() >= limit) {
        return;
      }

      final boolean survives = new ConditionEvaluator(
          examiner, entry, cache, query.condition, propertySource).evaluate();
      if (!survives) {
        return;
      }

      if (!sorted) {
        unsorted.add(new Candidate(globalIndex, entry, null));
        return;
      }

      final Map<String, ArcScriptResult.DataCell> sortCells = new HashMap<>();
      for (final SortInstruction.SortKey sk : query._sortKeys) {
        sortCells.put(sk.target(), toDataCell(propertySource.get(sk.target())));
      }
      final var row = new ArcScriptResult.QueryResultRow(entry, sortCells);
      final var candidate = new Candidate(globalIndex, entry, row);

      if (topK != null) {
        if (topK.size() < effectiveCap) {
          topK.offer(candidate);
        } else {
          if (hardCapEngaged && warned.compareAndSet(false, true)) {
            log.warn(
                "Sort hard cap of [ {} ] entries reached - the worst-sorting entries of this "
                + "unbounded ordered query are being discarded. Add an explicit limit to the "
                + "query, or raise the unbounded sort hard cap.",
                effectiveCap);
          }
          if (comparator.compare(candidate.sortRow(), topK.peek().sortRow()) < 0) {
            topK.poll();
            topK.offer(candidate);
          }
        }
      } else {
        buffer.add(candidate);
        if (warnThreshold > 0 && buffer.size() > warnThreshold && warned.compareAndSet(false, true)) {
          log.warn(
              "Unbounded sort buffered more than [ {} ] entries. Consider adding a limit to the "
              + "query, or configuring an unbounded sort hard cap.",
              warnThreshold);
        }
      }
    });

    if (!sorted) {
      return unsorted;
    }
    if (topK != null) {
      final var arr = new Candidate[topK.size()];
      for (int i = arr.length - 1; i >= 0; i--) {
        arr[i] = topK.poll();
      }
      return List.of(arr);
    }
    buffer.sort(Comparator.comparing(Candidate::sortRow, comparator));
    return buffer;
  }

  private List<ArcScriptResult.QueryResultRow> materializeEagerly(final ArrowColumnStore store,
                                                                  final long[] globalIndices,
                                                                  final StorageEntry[] finalEntries,
                                                                  final List<String> showColumnNames) {
    try {
      final var raw = store.materialize(globalIndices);
      final var rows = new ArrayList<ArcScriptResult.QueryResultRow>(raw.size());
      for (int i = 0; i < raw.size(); i++) {
        final Map<String, ArcScriptResult.DataCell> cells = new HashMap<>();
        for (final String prop : showColumnNames) {
          cells.put(prop, toDataCell(raw.get(i).get(prop)));
        }
        rows.add(new ArcScriptResult.QueryResultRow(finalEntries[i], cells));
      }
      return rows;
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static ArcScriptResult.DataCell toDataCell(
      final StorageInstanceExaminer.PropertyDiscoveryResult pdr) {
    return switch (pdr) {
      case StorageInstanceExaminer.None none -> ArcScriptResult.DataCell.noValue();
      case StorageInstanceExaminer.Some some -> ArcScriptResult.DataCell.of(some.val());
    };
  }

  private static void collectSingleValueProps(final QueryConditionImpl condition,
                                              final Set<String> out) {
    if (condition == null) {
      return;
    }

    final var it = condition.assertionIterator();
    while (it.hasNext()) {
      final QueryElement element = it.next().element();
      switch (element) {
        case Assertion a -> {
          if (a.isSingle()) {
            out.add(a.prop());
          }
        }
        case QueryConditionImpl qc -> collectSingleValueProps(qc, out);
      }
    }
  }

}

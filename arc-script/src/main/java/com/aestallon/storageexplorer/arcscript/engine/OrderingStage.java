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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static com.aestallon.storageexplorer.arcscript.engine.ArcPropertyPaths.toPath;
import com.aestallon.storageexplorer.arcscript.api.SortInstruction;
import com.aestallon.storageexplorer.core.service.index.ColumnId;
import com.aestallon.storageexplorer.core.service.index.RowId;
import com.aestallon.storageexplorer.core.service.index.impl.ExaminerBackedProjection;

/**
 * Maintains the partial ordering of {@code where} survivors while upstream evaluation is still in
 * progress. Only part of the pipeline when the query carries an {@code order} clause.
 *
 * <p>
 * Sort key properties are discovered concurrently as entries arrive (reusing the query-wide load
 * cache); only the ordering bookkeeping itself is serialized:
 *
 * <ul>
 *   <li><b>With a {@code limit} (top-K)</b>: a bounded heap of the {@code limit} best entries seen
 *   so far is maintained incrementally - memory is bounded by the limit, not the survivor count,
 *   and the final drain costs O(K log K).</li>
 *   <li><b>Without a {@code limit}</b>: a total order requires every survivor, so rows are
 *   buffered (their projection already pipelined with upstream work) and sorted once on
 *   completion. The buffer growth is observable via {@code unboundedSortWarnThreshold} and may be
 *   hard-capped via {@code unboundedSortHardCap}, which degrades the sort to top-K semantics
 *   rather than risking memory exhaustion.</li>
 * </ul>
 */
final class OrderingStage extends PipelineStage<IndexedEntry, IndexedEntry> {

  private static final Logger log = LoggerFactory.getLogger(OrderingStage.class);

  /** A buffered/heaped row, carrying its {@link RowId} along - needed both to emit it onward
   *  unchanged and to {@link ExaminerBackedProjection#dropRow} it if it's later evicted. */
  private record Ranked(RowId row, ArcScriptResult.QueryResultRow queryRow) {}

  private final ExaminerBackedProjection projection;
  private final RowProjector projector;
  private final Set<ColumnId> sortKeyColumns;
  private final Set<ColumnId> yieldColumns;
  private final Comparator<Ranked> comparator;
  private final long limit;
  private final boolean hardCapEngaged;
  private final int warnThreshold;

  // ReentrantLock, not synchronized: contended monitor acquisition blocks a virtual thread's
  // carrier on JDK 21 (pre-JEP 491), while lock-based waiting parks the virtual thread only:
  private final ReentrantLock lock = new ReentrantLock();
  /** Bounded max-heap - worst row at head - when a limit caps the result set; null otherwise. */
  private final PriorityQueue<Ranked> topK;
  /** Unbounded buffer when every survivor must be totally ordered; null otherwise. */
  private final List<Ranked> buffer;
  private boolean warned = false;

  OrderingStage(final ExecutorService executor,
                final QueryEngineSettings settings,
                final PipelineMetrics.StageMetrics metrics,
                final ExaminerBackedProjection projection,
                final List<SortInstruction.SortKey> sortKeys,
                final List<ArcScriptResult.ColumnDescriptor> yieldColumnDescriptors,
                final long limit) {
    super(
        executor,
        settings.queueCapacity(),
        settings.yieldConcurrency(),
        settings.sourcePrefetch(),
        metrics);
    this.projection = projection;
    this.projector = new RowProjector(
        projection,
        sortKeys.stream()
            .map(it -> new ArcScriptResult.ColumnDescriptor(it.target(), it.target()))
            .toList());
    // minted eagerly (not lazily per-row) purely to know, once sorting is done, which sort-key
    // columns aren't also needed by yield and can be dropped (T8.2) - a path shared between order
    // and yield resolves to the same ColumnId either way, so this never duplicates work:
    this.sortKeyColumns = sortKeys.stream()
        .map(it -> projection.column(toPath(it.target())))
        .collect(Collectors.toCollection(HashSet::new));
    this.yieldColumns = yieldColumnDescriptors.stream()
        .map(it -> projection.column(toPath(it.prop())))
        .collect(Collectors.toCollection(HashSet::new));
    this.comparator = Comparator.comparing(Ranked::queryRow, RowComparators.of(sortKeys));
    this.warnThreshold = settings.unboundedSortWarnThreshold();

    // an explicit query limit always wins: it already bounds memory via the top-K heap, and the
    // hard cap exists solely to guard the OTHERWISE-unbounded sort path - it must never truncate
    // a result set the user explicitly sized:
    final long hardCap = settings.unboundedSortHardCap();
    this.limit = limit > 0L ? limit : hardCap;
    this.hardCapEngaged = limit <= 0L && hardCap > 0L;
    if (this.limit > 0L) {
      // the heap holds the K best rows seen so far, with the WORST of them at its head, so a
      // newcomer only has to beat the head to enter:
      topK = new PriorityQueue<>((int) Math.min(this.limit, 1_024L), comparator.reversed());
      buffer = null;
    } else {
      topK = null;
      buffer = new ArrayList<>();
    }
  }

  @Override
  protected void process(final IndexedEntry item) {
    final ArcScriptResult.QueryResultRow queryRow = projector.project(item.entry(), item.row());
    final Ranked row = new Ranked(item.row(), queryRow);
    Ranked evicted = null;
    lock.lock();
    try {
      if (topK != null) {
        if (topK.size() < limit) {
          topK.offer(row);
        } else {
          if (hardCapEngaged && !warned) {
            // the query had no explicit limit - rows are being discarded solely because of the
            // configured hard cap, which must not happen silently:
            warned = true;
            log.warn(
                "Sort hard cap of [ {} ] entries reached - the worst-sorting entries of this "
                + "unbounded ordered query are being discarded. Add an explicit limit to the "
                + "query, or raise the unbounded sort hard cap.",
                limit);
            metrics.earlyTerminated();
          }
          if (comparator.compare(row, topK.peek()) < 0) {
            evicted = topK.poll();
            topK.offer(row);
          } else {
            evicted = row;
          }
        }
      } else {
        buffer.add(row);
        if (warnThreshold > 0 && buffer.size() > warnThreshold && !warned) {
          warned = true;
          log.warn(
              "Unbounded sort buffered more than [ {} ] entries. Consider adding a limit to the "
              + "query, or configuring an unbounded sort hard cap.",
              warnThreshold);
        }
      }
    } finally {
      lock.unlock();
    }

    if (evicted != null) {
      // lost the top-K contest (or never entered it under the hard cap): its cells will never be
      // read again:
      projection.dropRow(evicted.row());
    }
  }

  @Override
  protected void onUpstreamComplete() {
    final List<Ranked> sorted;
    lock.lock();
    try {
      if (topK != null) {
        final var arr = new Ranked[topK.size()];
        // draining the worst-first heap fills the result back to front:
        for (int i = arr.length - 1; i >= 0; i--) {
          arr[i] = topK.poll();
        }
        sorted = List.of(arr);
      } else {
        buffer.sort(comparator);
        sorted = buffer;
      }
    } finally {
      lock.unlock();
    }

    // the final order is settled - every sort-key column not also needed by yield has served its
    // only purpose (T8.2):
    final Set<ColumnId> sortKeyOnly = new HashSet<>(sortKeyColumns);
    sortKeyOnly.removeAll(yieldColumns);
    sortKeyOnly.forEach(projection::dropColumn);

    for (int i = 0; i < sorted.size(); i++) {
      final Ranked ranked = sorted.get(i);
      emit(new IndexedEntry(i, ranked.queryRow().entry(), ranked.row()));
    }
  }

}

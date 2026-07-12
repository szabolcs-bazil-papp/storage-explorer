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
import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.aestallon.storageexplorer.arcscript.api.SortInstruction;
import com.aestallon.storageexplorer.core.model.entry.StorageEntry;
import com.aestallon.storageexplorer.core.service.StorageInstanceExaminer;

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

  private final RowProjector projector;
  private final Comparator<ArcScriptResult.QueryResultRow> comparator;
  private final long limit;
  private final boolean hardCapEngaged;
  private final int warnThreshold;

  // ReentrantLock, not synchronized: contended monitor acquisition blocks a virtual thread's
  // carrier on JDK 21 (pre-JEP 491), while lock-based waiting parks the virtual thread only:
  private final ReentrantLock lock = new ReentrantLock();
  /** Bounded max-heap - worst row at head - when a limit caps the result set; null otherwise. */
  private final PriorityQueue<ArcScriptResult.QueryResultRow> topK;
  /** Unbounded buffer when every survivor must be totally ordered; null otherwise. */
  private final List<ArcScriptResult.QueryResultRow> buffer;
  private boolean warned = false;

  OrderingStage(final ExecutorService executor,
                final QueryEngineSettings settings,
                final PipelineMetrics.StageMetrics metrics,
                final StorageInstanceExaminer examiner,
                final StorageInstanceExaminer.ObjectEntryLookupTable cache,
                final List<SortInstruction.SortKey> sortKeys,
                final long limit) {
    super(
        executor,
        settings.queueCapacity(),
        settings.yieldConcurrency(),
        settings.sourcePrefetch(),
        metrics);
    this.projector = new RowProjector(
        examiner,
        cache,
        sortKeys.stream()
            .map(it -> new ArcScriptResult.ColumnDescriptor(it.target(), it.target()))
            .toList());
    this.comparator = RowComparators.of(sortKeys);
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
    final ArcScriptResult.QueryResultRow row = projector.project(item.entry());
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
            topK.poll();
            topK.offer(row);
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
  }

  @Override
  protected void onUpstreamComplete() {
    final List<ArcScriptResult.QueryResultRow> sorted;
    lock.lock();
    try {
      if (topK != null) {
        final var arr = new ArcScriptResult.QueryResultRow[topK.size()];
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

    for (int i = 0; i < sorted.size(); i++) {
      final StorageEntry entry = sorted.get(i).entry();
      emit(new IndexedEntry(i, entry));
    }
  }

}

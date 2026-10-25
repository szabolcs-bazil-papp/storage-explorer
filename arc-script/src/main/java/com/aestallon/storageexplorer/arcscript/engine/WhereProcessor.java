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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicLong;
import com.aestallon.storageexplorer.arcscript.internal.query.QueryConditionImpl;
import com.aestallon.storageexplorer.core.model.entry.StorageEntry;
import com.aestallon.storageexplorer.core.service.StorageInstanceExaminer;

/**
 * Evaluates the {@code where} predicate on each incoming entry, pushing survivors downstream the
 * moment they pass.
 *
 * <p>
 * When the query carries a {@code limit} but no {@code order} clause, any {@code limit} entries
 * satisfy the query, so the stage hard-caps its emissions at {@code limit} and - when early
 * termination is enabled - cancels the upstream to stop source iteration and in-flight evaluation
 * dispatch as soon as the cap is reached. This is a genuine improvement over the legacy engine,
 * which only refused to <em>start</em> queued work on limit satisfaction (and could even emit a few
 * rows beyond the limit).
 */
final class WhereProcessor extends PipelineStage<StorageEntry, IndexedEntry> {

  private final StorageInstanceExaminer examiner;
  private final StorageInstanceExaminer.ObjectEntryLookupTable cache;
  private final QueryConditionImpl condition;
  private final long limit;
  private final boolean earlyTermination;
  private final AtomicLong survivors = new AtomicLong();

  WhereProcessor(final ExecutorService executor,
                 final QueryEngineSettings settings,
                 final PipelineMetrics.StageMetrics metrics,
                 final StorageInstanceExaminer examiner,
                 final StorageInstanceExaminer.ObjectEntryLookupTable cache,
                 final QueryConditionImpl condition,
                 final long limit) {
    super(
        executor,
        settings.queueCapacity(),
        settings.whereConcurrency(),
        settings.sourcePrefetch(),
        metrics);
    this.examiner = examiner;
    this.cache = cache;
    this.condition = condition;
    this.limit = limit;
    this.earlyTermination = settings.earlyTerminationEnabled();
  }

  @Override
  protected void process(final StorageEntry entry) {
    final var evaluator = new ConditionEvaluator(examiner, entry, cache, condition);
    if (!evaluator.evaluate()) {
      return;
    }

    final long seq = survivors.getAndIncrement();
    if (limit > 0L && seq >= limit) {
      // enough entries satisfied the query already; drop this one:
      if (earlyTermination) {
        cancelUpstream();
      }
      return;
    }

    emit(new IndexedEntry(seq, entry));
    if (earlyTermination && limit > 0L && seq + 1L >= limit) {
      cancelUpstream();
    }
  }

}

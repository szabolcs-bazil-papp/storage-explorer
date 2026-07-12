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

import java.util.List;
import java.util.concurrent.ExecutorService;
import com.aestallon.storageexplorer.core.service.StorageInstanceExaminer;

/**
 * Computes the {@code show} column projection of each incoming entry, concurrently, preserving the
 * result ordering via the sequence tag carried on each element. When the query has no columns to
 * render, entries pass through as bare rows without any property discovery.
 */
final class YieldProcessor extends PipelineStage<IndexedEntry, IndexedRow> {

  private final RowProjector projector;

  YieldProcessor(final ExecutorService executor,
                 final QueryEngineSettings settings,
                 final PipelineMetrics.StageMetrics metrics,
                 final StorageInstanceExaminer examiner,
                 final StorageInstanceExaminer.ObjectEntryLookupTable cache,
                 final List<ArcScriptResult.ColumnDescriptor> columns) {
    super(
        executor,
        settings.queueCapacity(),
        settings.yieldConcurrency(),
        settings.sourcePrefetch(),
        metrics);
    this.projector = columns.isEmpty() ? null : new RowProjector(examiner, cache, columns);
  }

  @Override
  protected void process(final IndexedEntry item) {
    final ArcScriptResult.QueryResultRow row = (projector == null)
        ? new ArcScriptResult.QueryResultRow(item.entry())
        : projector.project(item.entry());
    emit(new IndexedRow(item.seq(), row));
  }

}

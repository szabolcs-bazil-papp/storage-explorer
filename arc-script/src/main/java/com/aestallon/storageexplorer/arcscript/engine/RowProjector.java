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

import java.util.Collection;
import java.util.Map;
import static com.aestallon.storageexplorer.arcscript.engine.ArcPropertyPaths.toPath;
import com.aestallon.storageexplorer.common.util.Pair;
import com.aestallon.storageexplorer.core.model.entry.StorageEntry;
import com.aestallon.storageexplorer.core.service.index.CellValue;
import com.aestallon.storageexplorer.core.service.index.RowId;
import com.aestallon.storageexplorer.core.service.index.impl.ExaminerBackedProjection;

/**
 * Projects a {@link StorageEntry} onto a set of columns, forming a
 * {@link ArcScriptResult.QueryResultRow}.
 *
 * <p>
 * The pipelined counterpart of {@link QueryResultRowEvaluationExecutor}'s per-entry work: sort key
 * discovery and {@code show} column rendering both funnel through the same shared {@link
 * ExaminerBackedProjection} now (T8.2) - a path referenced by both {@code order} and {@code show}
 * mints (and resolves) the same column once, not twice.
 */
record RowProjector(
    ExaminerBackedProjection projection,
    Collection<ArcScriptResult.ColumnDescriptor> columns) {

  ArcScriptResult.QueryResultRow project(final StorageEntry entry, final RowId row) {
    final Map<String, ArcScriptResult.DataCell> cells = columns.stream()
        .map(it -> discoverCell(it, row))
        .collect(Pair.toMap());
    return new ArcScriptResult.QueryResultRow(entry, cells);
  }

  private Pair<String, ArcScriptResult.DataCell> discoverCell(
      final ArcScriptResult.ColumnDescriptor column,
      final RowId row) {
    final String prop = column.prop();
    final CellValue value = projection.get(row, projection.column(toPath(prop)));
    final ArcScriptResult.DataCell cell = switch (value) {
      case CellValue.Missing ignored -> ArcScriptResult.DataCell.noValue();
      case CellValue.Scalar(var v) -> ArcScriptResult.DataCell.of(v);
    };
    return Pair.of(prop, cell);
  }

}

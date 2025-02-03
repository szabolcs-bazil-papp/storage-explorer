/*
 * Copyright (C) 2025 Szabolcs Bazil Papp
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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import com.aestallon.storageexplorer.common.util.Pair;
import com.aestallon.storageexplorer.core.model.entry.StorageEntry;
import com.aestallon.storageexplorer.core.service.StorageInstanceExaminer;

public class QueryResultRowEvaluationExecutor extends
    AbstractEntryEvaluationExecutor<ArcScriptResult.QueryResultRow, QueryResultRowEvaluationExecutor> {

  static final class Builder
      extends AbstractEntryEvaluationExecutor.Builder<QueryResultRowEvaluationExecutor, Builder> {

    private final Set<ArcScriptResult.ColumnDescriptor> columns;

    private Builder(final StorageInstanceExaminer examiner,
                    final Set<StorageEntry> entries,
                    final Collection<ArcScriptResult.ColumnDescriptor> columns) {
      super(examiner, entries);
      this.columns = new HashSet<>(columns);
    }

    @Override
    Builder self() {
      return this;
    }

    @Override
    QueryResultRowEvaluationExecutor build() {
      return new QueryResultRowEvaluationExecutor(this);
    }
  }

  static Builder builder(final StorageInstanceExaminer examiner,
                         final Set<StorageEntry> entries,
                         final Collection<ArcScriptResult.ColumnDescriptor> columns) {
    return new Builder(examiner, entries, columns);
  }

  private final Set<ArcScriptResult.ColumnDescriptor> columns;

  private QueryResultRowEvaluationExecutor(Builder builder) {
    super(builder);
    columns = builder.columns;
  }

  @Override
  protected boolean shortCircuit() {
    return columns.isEmpty();
  }

  @Override
  protected boolean doNotExecute() {
    return false;
  }

  @Override
  protected void work(StorageEntry entry) {
    final Map<String, ArcScriptResult.DataCell> cells = columns.stream()
        .map(it -> discoverCell(it, entry))
        .collect(Pair.toMap());
    results.add(new ArcScriptResult.QueryResultRow(entry, cells));
  }

  private Pair<String, ArcScriptResult.DataCell> discoverCell(
      final ArcScriptResult.ColumnDescriptor column,
      final StorageEntry entry) {
    final String prop = column.prop();
    final ArcScriptResult.DataCell cell = switch (examiner.discoverProperty(entry, prop, cache)) {
      case StorageInstanceExaminer.None none -> ArcScriptResult.DataCell.noValue();
      case StorageInstanceExaminer.Some some -> switch (some) {
        case StorageInstanceExaminer.ComplexFound complex ->
            ArcScriptResult.DataCell.complex(complex.value());
        case StorageInstanceExaminer.ListFound list ->
            ArcScriptResult.DataCell.complex(list.value());
        case StorageInstanceExaminer.PrimitiveDiscoveryResult primitive ->
            ArcScriptResult.DataCell.simple(primitive.toString());
      };
    };
    return Pair.of(prop, cell);
  }

}

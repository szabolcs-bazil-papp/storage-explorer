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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import com.aestallon.storageexplorer.arcscript.api.SortInstruction;
import com.aestallon.storageexplorer.arcscript.internal.query.QueryInstructionImpl;
import com.aestallon.storageexplorer.core.model.entry.StorageEntry;
import com.aestallon.storageexplorer.core.model.instance.StorageInstance;
import com.aestallon.storageexplorer.core.model.instance.dto.StorageInstanceType;
import com.aestallon.storageexplorer.core.model.loading.IndexingTarget;
import com.aestallon.storageexplorer.core.service.StorageInstanceExaminer;

public class QueryEngineImpl implements QueryEngine {
  @Override
  public ArcScriptResult.InstructionResult execute(StorageInstance storageInstance,
                                                   QueryInstructionImpl query) {
    final long start = System.nanoTime();

    final IndexingTarget target = new IndexingTarget(query._schemas, query._types);
    final Set<StorageEntry> entries = storageInstance.index().get(target);
    final var examiner = storageInstance.examiner();
    final var condition = query.condition;
    final var limit = query._limit;
    final var sortKeys = query._sortKeys;
    final var runningOnFs = StorageInstanceType.FS == storageInstance.type();
    final var cache = StorageInstanceExaminer.ObjectEntryLookupTable.newInstance();
    final var executorLimit = sortKeys.isEmpty() ? limit : -1L;
    final var res = ConditionEvaluationExecutor
        .builder(examiner, entries, condition, executorLimit)
        //.useSemaphore(!runningOnFs)
        .useCache(cache)
        .build()
        .execute();
    final long end = System.nanoTime();

    final Map<StorageEntry, Integer> sortedIndices;
    if (!sortKeys.isEmpty()) {
      final var sorter = new StorageEntrySorter(examiner, cache, sortKeys);
      sortedIndices = sorter.sort(res);
      if (limit > 0 && sortedIndices.size() > limit) {
        sortedIndices.values().removeIf(it -> it >= limit);
        res.retainAll(sortedIndices.keySet());
      }
    } else {
      sortedIndices = Collections.emptyMap();
    }
    final long sortEnd = System.nanoTime();

    final var showColumns = query._columns;
    final ArcScriptResult.ResultSet resultSet;
    if (showColumns.isEmpty()) {
      resultSet = new ArcScriptResult.ResultSet(
          new ArcScriptResult.ResultSetMeta(Collections.emptyList(), -1L),
          res.stream().map(ArcScriptResult.QueryResultRow::new).toList());
    } else {
      final long renderStart = System.nanoTime();
      final var columns = showColumns.stream()
          .map(it -> new ArcScriptResult.ColumnDescriptor(
              it.propertyInternal(),
              it.displayNameInternal()))
          .toList();
      final var rows = QueryResultRowEvaluationExecutor.builder(examiner, res, columns)
          //.useSemaphore(!runningOnFs)
          .useCache(cache)
          .build()
          .execute();
      final var resultRows = returnSorted(rows, sortedIndices);
      final long renderEnd = System.nanoTime();
      final var meta = new ArcScriptResult.ResultSetMeta(columns, renderEnd - renderStart);
      resultSet = new ArcScriptResult.ResultSet(meta, resultRows);
    }

    return new ArcScriptResult.QueryPerformed(
        query.toString(),
        resultSet,
        sortEnd - start);
  }

  private List<ArcScriptResult.QueryResultRow> returnSorted(
      final Set<ArcScriptResult.QueryResultRow> rows,
      final Map<StorageEntry, Integer> indices) {
    if (indices.isEmpty()) {
      return new ArrayList<>(rows);
    }

    final var ret = new ArcScriptResult.QueryResultRow[rows.size()];
    final var lookup = rows.stream()
        .collect(Collectors.toMap(ArcScriptResult.QueryResultRow::entry, it -> it));
    indices.forEach((entry, idx) -> ret[idx] = lookup.get(entry));
    return Arrays.asList(ret);
  }

  private record StorageEntrySorter(
      StorageInstanceExaminer examiner,
      StorageInstanceExaminer.ObjectEntryLookupTable cache,
      List<SortInstruction.SortKey> sortKeys) {

    public Map<StorageEntry, Integer> sort(Set<StorageEntry> entries) {
      final var projection = discoverSortProperties(entries);
      final var sortedProjection = sortProjection(projection);
      return determineIndices(sortedProjection);
    }

    private Set<ArcScriptResult.QueryResultRow> discoverSortProperties(Set<StorageEntry> entries) {
      return QueryResultRowEvaluationExecutor
          .builder(
              examiner,
              entries,
              sortKeys.stream()
                  .map(it -> new ArcScriptResult.ColumnDescriptor(it.target(), it.target()))
                  .toList())
          .useCache(cache)
          .build()
          .execute();
    }

    private List<ArcScriptResult.QueryResultRow> sortProjection(
        Set<ArcScriptResult.QueryResultRow> rows) {
      if (sortKeys.isEmpty()) {
        return new ArrayList<>(rows);
      }

      return rows.stream()
          .sorted(RowComparators.of(sortKeys))
          .toList();
    }

    private Map<StorageEntry, Integer> determineIndices(List<ArcScriptResult.QueryResultRow> rows) {
      final var ret = new HashMap<StorageEntry, Integer>();
      for (int i = 0; i < rows.size(); i++) {
        ret.put(rows.get(i).entry(), i);
      }
      return ret;
    }

  }
}

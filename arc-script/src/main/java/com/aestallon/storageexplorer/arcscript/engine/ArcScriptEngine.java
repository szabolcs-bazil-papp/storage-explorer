package com.aestallon.storageexplorer.arcscript.engine;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import com.aestallon.storageexplorer.arcscript.api.ArcInterpreterFlag;
import com.aestallon.storageexplorer.arcscript.api.ArcScript;
import com.aestallon.storageexplorer.arcscript.api.SortInstruction;
import com.aestallon.storageexplorer.arcscript.internal.ArcScriptImpl;
import com.aestallon.storageexplorer.arcscript.internal.Instruction;
import com.aestallon.storageexplorer.arcscript.internal.index.IndexInstructionImpl;
import com.aestallon.storageexplorer.arcscript.internal.query.QueryInstructionImpl;
import com.aestallon.storageexplorer.arcscript.internal.update.UpdateInstructionImpl;
import com.aestallon.storageexplorer.core.model.entry.StorageEntry;
import com.aestallon.storageexplorer.core.model.instance.StorageInstance;
import com.aestallon.storageexplorer.core.model.instance.dto.StorageInstanceType;
import com.aestallon.storageexplorer.core.model.loading.IndexingTarget;
import com.aestallon.storageexplorer.core.service.IndexingStrategy;
import com.aestallon.storageexplorer.core.service.StorageInstanceExaminer;

public class ArcScriptEngine {

  private final ArcScriptEngineConfiguration config;

  public ArcScriptEngine(ArcScriptEngineConfiguration config) {
    this.config = config;
  }

  public ArcScriptResult execute(ArcScript arcScript, StorageInstance storageInstance) {
    if (!(arcScript instanceof ArcScriptImpl as)) {
      return new ArcScriptResult.UnknownError("ArcScript is not an ArcScriptImpl");
    }

    final List<Instruction> instructions = new ArrayList<>(as.instructions);
    if (instructions.isEmpty()) {
      return ArcScriptResult.empty();
    }

    final boolean verbose = as.isFlagSet(ArcInterpreterFlag.VERBOSE);
    // we must find missing or incomplete indexing instructions and amend them...
    record IndexInsert(int idx, ImplicitIndexInstruction instruction) {}
    final List<IndexInsert> inserts = new ArrayList<>();
    OUTER:
    for (int i = 0; i < instructions.size(); i++) {
      final Instruction instruction = instructions.get(i);
      if (instruction instanceof IndexInstructionImpl index && index._schemas.isEmpty()) {
        return ArcScriptResult.impermissible("Specify at least one schema for indexing: ", index);
      }

      if (instruction instanceof QueryInstructionImpl query) {
        Set<String> schemas = query._schemas;
        Set<String> types = query._types;
        if (schemas.isEmpty()) {
          return ArcScriptResult.impermissible("Specify at least one schema for query: ", query);
        }

        for (int j = 0; j < i; j++) {
          final Instruction instruction2 = instructions.get(j);
          if (instruction2 instanceof IndexInstructionImpl index) {
            if (index._schemas.equals(schemas) && index._types.equals(types)) {
              // everything perfectly matches for this query, nothing to be done!
              continue OUTER;
            }
          }
        }

        // here would come a complex implicit indexing check, where we widen the indexing 
        // instructions not covering the full landscape of later queries, and as a last resort, we 
        // add an extra, implicit index. I don't have the energy to properly implement that, maybe 
        // later...
        final var implicit = new ImplicitIndexInstruction();
        implicit._schemas.addAll(schemas);
        implicit._types.addAll(types);
        inserts.addFirst(new IndexInsert(i, implicit));
      }
    }
    inserts.forEach(it -> instructions.add(it.idx, it.instruction));

    final List<ArcScriptResult.InstructionResult> instructionResults = new ArrayList<>();
    for (final Instruction instruction : instructions) {
      switch (instruction) {
        case QueryInstructionImpl query -> {
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

          instructionResults.add(new ArcScriptResult.QueryPerformed(
              query.toString(),
              resultSet,
              sortEnd - start));
        }
        case IndexInstructionImpl index -> {
          final long start = System.nanoTime();
          final IndexingTarget target = new IndexingTarget(index._schemas, index._types);
          final int size = storageInstance
              .index()
              .refresh(IndexingStrategy.of(index._strategy), target);
          final long end = System.nanoTime();
          instructionResults.add(new ArcScriptResult.IndexingPerformed(
              index instanceof ImplicitIndexInstruction,
              index._schemas,
              index._types,
              index.toString(),
              size,
              end - start));
        }
        case UpdateInstructionImpl update ->
            throw new IllegalArgumentException("Updates are not yet supported!");
        default -> throw new IllegalStateException("Unexpected value: " + instruction);
      }
    }

    return ArcScriptResult.ok(instructionResults, verbose);
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


  private static final class ImplicitIndexInstruction extends IndexInstructionImpl {}


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

      Comparator<ArcScriptResult.QueryResultRow> comparator = null;
      for (final var sortKey : sortKeys) {
        final var c = rowComparator(sortKey);
        comparator = comparator == null ? c : comparator.thenComparing(c);
      }

      return rows.stream()
          .sorted(comparator)
          .toList();
    }

    private Comparator<ArcScriptResult.QueryResultRow> rowComparator(
        final SortInstruction.SortKey sortKey) {
      return (a, b) -> {
        final var prop = sortKey.target();
        final var asc = sortKey.asc();

        final Object aProp = a.cells().get(prop).value();
        final Object bProp = b.cells().get(prop).value();

        if (aProp == null && bProp == null) {
          // nulls are equivalent:
          return 0;
        }

        if (aProp == null) {
          // following Oracle, if the ordering is ascending, the default is NULLS LAST:
          return asc ? 1 : -1;
        }

        if (bProp == null) {
          // as per the same convention:
          return asc ? -1 : 1;
        }

        switch (aProp) {
          case Number aNum when bProp instanceof Number bNum -> {
            final var res = Double.compare(aNum.doubleValue(), bNum.doubleValue());
            return asc ? res : -res;
          }
          case String aStr when bProp instanceof String bStr -> {
            final var res = aStr.compareTo(bStr);
            return asc ? res : -res;
          }
          case Boolean aBool when bProp instanceof Boolean bBool -> {
            final var res = Boolean.compare(aBool, bBool);
            return asc ? res : -res;
          }
          case OffsetDateTime aDate when bProp instanceof OffsetDateTime bDate -> {
            final var res = aDate.compareTo(bDate);
            return asc ? res : -res;
          }
          case LocalDateTime aDate when bProp instanceof LocalDateTime bDate -> {
            final var res = aDate.compareTo(bDate);
            return asc ? res : -res;
          }
          case LocalDate aDate when bProp instanceof LocalDate bDate -> {
            final var res = aDate.compareTo(bDate);
            return asc ? res : -res;
          }
          default -> {}
        }

        // these were the comparison we support for same types. For diverging types, the following
        // ascending sort order is observed:
        // num -> str -> bool -> date -> any
        final var aIdx = getTypeIdx(aProp);
        final var bIdx = getTypeIdx(bProp);
        final var res = Integer.compare(aIdx, bIdx);
        return asc ? res : -res;
      };
    }

    private static final Class<?>[] TYPE_ORDER =
        { Number.class, String.class, Boolean.class, Temporal.class };

    private static int getTypeIdx(Object o) {
      for (int i = 0; i < TYPE_ORDER.length; i++) {
        if (TYPE_ORDER[i].isInstance(o)) {
          return i;
        }
      }

      return TYPE_ORDER.length;
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

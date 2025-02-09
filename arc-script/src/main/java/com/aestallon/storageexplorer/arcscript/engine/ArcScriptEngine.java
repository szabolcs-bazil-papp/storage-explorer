package com.aestallon.storageexplorer.arcscript.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import com.aestallon.storageexplorer.arcscript.api.ArcScript;
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
          final var runningOnFs = StorageInstanceType.FS == storageInstance.type();
          final var cache = StorageInstanceExaminer.ObjectEntryLookupTable.newInstance();
          final var res = ConditionEvaluationExecutor.builder(examiner, entries, condition, limit)
              //.useSemaphore(!runningOnFs)
              .useCache(cache)
              .build()
              .execute();
          final long end = System.nanoTime();

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
            final long renderEnd = System.nanoTime();
            final var meta = new ArcScriptResult.ResultSetMeta(columns, renderEnd - renderStart);
            resultSet = new ArcScriptResult.ResultSet(meta, new ArrayList<>(rows));
          }

          instructionResults.add(new ArcScriptResult.QueryPerformed(
              query.toString(),
              resultSet,
              end - start));
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

    return new ArcScriptResult.Ok(instructionResults);
  }


  private static final class ImplicitIndexInstruction extends IndexInstructionImpl {}

}

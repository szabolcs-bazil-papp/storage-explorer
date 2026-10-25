package com.aestallon.storageexplorer.arcscript.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import com.aestallon.storageexplorer.arcscript.api.ArcInterpreterFlag;
import com.aestallon.storageexplorer.arcscript.api.ArcScript;
import com.aestallon.storageexplorer.arcscript.internal.ArcScriptImpl;
import com.aestallon.storageexplorer.arcscript.internal.Instruction;
import com.aestallon.storageexplorer.arcscript.internal.index.IndexInstructionImpl;
import com.aestallon.storageexplorer.arcscript.internal.query.QueryInstructionImpl;
import com.aestallon.storageexplorer.arcscript.internal.update.UpdateInstructionImpl;
import com.aestallon.storageexplorer.core.model.instance.StorageInstance;
import com.aestallon.storageexplorer.core.model.loading.IndexingTarget;
import com.aestallon.storageexplorer.core.service.IndexingStrategy;

/**
 * Interprets and executes a compiled {@link ArcScript}.
 *
 * <p>
 * Historically this engine inserted an <em>implicit</em> {@code index} instruction before every
 * type-sourced query, barrier-waiting on a full discovery pass so query engines could draw their
 * source set from the populated index. Query source acquisition is now streaming
 * ({@code StorageIndex.find} via {@code QuerySourceResolver}): each query lazily discovers - and
 * indexes as a side effect - exactly the entries it needs, so no implicit indexing is performed
 * anymore. Consequences:
 *
 * <ul>
 * <li>script results no longer contain implicit {@code IndexingPerformed} elements; discovery
 * cost is part of {@link ArcScriptResult.QueryPerformed#timeTaken()},</li>
 * <li>a query's source set is exactly what discovery finds at execution time - entries deleted
 * from the storage since a previous indexing run no longer linger in query results,</li>
 * <li>explicit {@code index} instructions keep working unchanged, and remain useful for warming
 * the index on demand.</li>
 * </ul>
 */
public class ArcScriptEngine {

  private final ArcScriptEngineConfiguration config;

  public ArcScriptEngine(ArcScriptEngineConfiguration config) {
    this.config = Objects.requireNonNull(config, "config cannot be null!");
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
    for (final Instruction instruction : instructions) {
      if (instruction instanceof IndexInstructionImpl index && index._schemas.isEmpty()) {
        return ArcScriptResult.impermissible("Specify at least one schema for indexing: ", index);
      }

      if (instruction instanceof QueryInstructionImpl query) {
        if (query._schemas.isEmpty()) {
          return ArcScriptResult.impermissible("Specify at least one schema for query: ", query);
        }

        if (query._collectionKind != null && query._schemas.size() != 1) {
          return ArcScriptResult.impermissible(
              "Specify exactly one schema for a collection query: ",
              query);
        }
      }
    }

    final List<ArcScriptResult.InstructionResult> instructionResults = new ArrayList<>();
    for (final Instruction instruction : instructions) {
      switch (instruction) {
        case QueryInstructionImpl query -> instructionResults.add(config.queryEngine.execute(
            storageInstance,
            query));
        case IndexInstructionImpl index -> {
          final long start = System.nanoTime();
          final IndexingTarget target = new IndexingTarget(index._schemas, index._types);
          final int size = storageInstance
              .index()
              .refresh(IndexingStrategy.of(index._strategy), target);
          final long end = System.nanoTime();
          instructionResults.add(new ArcScriptResult.IndexingPerformed(
              false,
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

}

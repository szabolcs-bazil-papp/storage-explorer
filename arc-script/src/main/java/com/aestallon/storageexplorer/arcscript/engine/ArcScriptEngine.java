package com.aestallon.storageexplorer.arcscript.engine;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import com.aestallon.storageexplorer.arcscript.api.ArcScript;
import com.aestallon.storageexplorer.arcscript.api.IndexInstruction;
import com.aestallon.storageexplorer.arcscript.api.Instruction;
import com.aestallon.storageexplorer.arcscript.api.QueryInstruction;
import com.aestallon.storageexplorer.arcscript.api.UpdateInstruction;
import com.aestallon.storageexplorer.arcscript.api.internal.ArcScriptImpl;
import com.aestallon.storageexplorer.arcscript.api.internal.index.IndexInstructionImpl;
import com.aestallon.storageexplorer.arcscript.api.internal.query.QueryInstructionImpl;
import com.aestallon.storageexplorer.core.model.instance.StorageInstance;
import com.aestallon.storageexplorer.core.model.loading.IndexingTarget;
import com.aestallon.storageexplorer.core.service.IndexingStrategy;
import com.aestallon.storageexplorer.core.service.StorageIndex;
import static java.util.stream.Collectors.toSet;

public class ArcScriptEngine {

  private final ArcScriptEngineConfiguration config;

  public ArcScriptEngine(ArcScriptEngineConfiguration config) {
    this.config = config;
  }

  public List<URI> execute(ArcScript arcScript, StorageInstance storageInstance) {
    if (!(arcScript instanceof ArcScriptImpl as)) {
      return Collections.emptyList();
    }

    for (final Instruction instruction : as.instructions) {
      switch (instruction) {
        case QueryInstruction queryIF -> {
          if (!(queryIF instanceof QueryInstructionImpl query)) {
            throw new IllegalArgumentException(
                "Query instructions must be instance of QueryInstructionImpl");
          }

        }
        case IndexInstruction indexIF -> {
          if (!(indexIF instanceof IndexInstructionImpl index)) {
            throw new IllegalArgumentException(
                "Index instructions must be instance of IndexInstructionImpl");
          }

          final IndexingTarget target = new IndexingTarget(index._schemas, index._types);
          storageInstance.index().printCount();
          storageInstance.index().refresh(IndexingStrategy.of(index._strategy), target);
          storageInstance.index().printCount();
        }
        case UpdateInstruction updateIF -> {
        }
        default -> throw new IllegalStateException("Unexpected value: " + instruction);
      }
    }


    return Collections.emptyList();
  }


  private <E> Set<E> arrToSet(final E[] es) {
    if (es == null) {
      return Collections.emptySet();
    }

    return Arrays.stream(es).filter(Objects::nonNull).collect(toSet());
  }
}

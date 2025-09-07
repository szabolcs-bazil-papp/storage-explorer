package com.aestallon.storageexplorer.arcscript.engine;

import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import com.aestallon.storageexplorer.core.model.entry.ObjectEntry;
import com.aestallon.storageexplorer.core.service.StorageInstanceExaminer;

class CachedExaminer {

  private final StorageInstanceExaminer examiner;
  private final StorageInstanceExaminer.ObjectEntryLookupTable cache;
  private final ExecutorService virtualThreads = Executors.newVirtualThreadPerTaskExecutor();

  CachedExaminer(StorageInstanceExaminer examiner) {
    this.examiner = examiner;
    cache = StorageInstanceExaminer.ObjectEntryLookupTable.newInstance();
  }

  public StorageInstanceExaminer examiner() {
    return examiner;
  }

  public StorageInstanceExaminer.ObjectEntryLookupTable cache() {
    return cache;
  }

  StorageInstanceExaminer.PropertyDiscoveryResult[] examine(ObjectEntry entry, ProjectionDef projectionDef) {
    final var fs = Arrays.stream(projectionDef.cols())
        .map(columnDef -> virtualThreads.submit(() -> {
          final var result = examiner.discoverProperty(entry, columnDef.arc(), cache);
          return columnDef.columnType().matches(result)
              ? result
              : new StorageInstanceExaminer.NotFound("Type mismatch");
        }))
        .toList();
    final var results = new StorageInstanceExaminer.PropertyDiscoveryResult[fs.size()];
    for (int i = 0; i < fs.size(); i++) {
      try {
        results[i] = fs.get(i).get();
      } catch (ExecutionException e) {
        results[i] = new StorageInstanceExaminer.NotFound("Exec error");
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new RuntimeException(e);
      }
    }
    return results;
  }
}

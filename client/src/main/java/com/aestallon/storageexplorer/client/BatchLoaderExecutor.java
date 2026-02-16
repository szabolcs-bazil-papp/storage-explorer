package com.aestallon.storageexplorer.client;

import java.util.Set;
import com.aestallon.storageexplorer.core.model.entry.ObjectEntry;
import com.aestallon.storageexplorer.core.model.entry.StorageEntry;
import com.aestallon.storageexplorer.core.model.loading.ObjectEntryLoadResult;
import com.aestallon.storageexplorer.core.service.StorageInstanceExaminer;
import com.aestallon.storageexplorer.core.util.AbstractEntryEvaluationExecutor;

public class BatchLoaderExecutor extends AbstractEntryEvaluationExecutor<BatchLoaderExecutor.EntryWithLoadResult, BatchLoaderExecutor> {

  public static BatchLoaderExecutorBuilder builder(StorageInstanceExaminer examiner,
                                                   Set<StorageEntry> entries) {
    return new BatchLoaderExecutorBuilder(examiner, entries);
  }

  public static final class BatchLoaderExecutorBuilder extends AbstractEntryEvaluationExecutor.Builder<BatchLoaderExecutor, BatchLoaderExecutorBuilder> {

    private BatchLoaderExecutorBuilder(
        StorageInstanceExaminer examiner,
        Set<StorageEntry> entries) {
      super(examiner, entries);
    }

    @Override
    protected BatchLoaderExecutorBuilder self() {
      return this;
    }

    @Override
    public BatchLoaderExecutor build() {
      return new BatchLoaderExecutor(this);
    }
  }

  private BatchLoaderExecutor(BatchLoaderExecutorBuilder builder) {
    super(builder);
  }

  @Override
  protected boolean shortCircuit() {
    return false;
  }

  @Override
  protected boolean doNotExecute() {
    return false;
  }

  @Override
  protected void work(StorageEntry entry) {
    if (!(entry instanceof ObjectEntry e)) {
      return;
    }

    results.add(new EntryWithLoadResult(e, e.tryLoad().get()));
  }

  public record EntryWithLoadResult(ObjectEntry entry, ObjectEntryLoadResult result) {}
}

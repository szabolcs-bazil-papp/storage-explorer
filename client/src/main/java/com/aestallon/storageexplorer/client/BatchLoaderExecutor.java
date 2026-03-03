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

package com.aestallon.storageexplorer.client;

import java.util.Set;
import com.aestallon.storageexplorer.core.model.entry.ObjectEntry;
import com.aestallon.storageexplorer.core.model.entry.StorageEntry;
import com.aestallon.storageexplorer.core.model.loading.ObjectEntryLoadResult;
import com.aestallon.storageexplorer.core.service.StorageInstanceExaminer;
import com.aestallon.storageexplorer.core.util.AbstractEntryEvaluationExecutor;

public class BatchLoaderExecutor extends
    AbstractEntryEvaluationExecutor<BatchLoaderExecutor.EntryWithLoadResult, BatchLoaderExecutor> {

  public record EntryWithLoadResult(ObjectEntry entry, ObjectEntryLoadResult result) {}


  public static final class BatchLoaderExecutorBuilder extends
      AbstractEntryEvaluationExecutor.Builder<BatchLoaderExecutor, BatchLoaderExecutorBuilder> {

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

  public static BatchLoaderExecutorBuilder builder(StorageInstanceExaminer examiner,
                                                   Set<StorageEntry> entries) {
    return new BatchLoaderExecutorBuilder(examiner, entries);
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

}

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

import java.util.Set;
import com.aestallon.storageexplorer.arcscript.internal.query.QueryConditionImpl;
import com.aestallon.storageexplorer.core.model.entry.StorageEntry;
import com.aestallon.storageexplorer.core.service.StorageInstanceExaminer;

public final class ConditionEvaluationExecutor
    extends AbstractEntryEvaluationExecutor<StorageEntry, ConditionEvaluationExecutor> {

  static final class Builder
      extends AbstractEntryEvaluationExecutor.Builder<ConditionEvaluationExecutor, Builder> {

    private final QueryConditionImpl c;
    private final long limit;

    private Builder(final StorageInstanceExaminer examiner,
                    final Set<StorageEntry> entries,
                    final QueryConditionImpl c,
                    final long limit) {
      super(examiner, entries);
      this.c = c;
      this.limit = limit;
    }

    @Override
    Builder self() {
      return this;
    }

    @Override
    ConditionEvaluationExecutor build() {
      return new ConditionEvaluationExecutor(this);
    }
  }

  static Builder builder(final StorageInstanceExaminer examiner,
                         final Set<StorageEntry> entries,
                         final QueryConditionImpl c,
                         final long limit) {
    return new Builder(examiner, entries, c, limit);
  }


  private final QueryConditionImpl c;
  private final long limit;

  private ConditionEvaluationExecutor(Builder builder) {
    super(builder);
    c = builder.c;
    limit = builder.limit;
  }

  @Override
  protected boolean shortCircuit() {
    return entries.isEmpty();
  }

  @Override
  protected boolean doNotExecute() {
    return limit > 0 && results.size() >= limit;
  }

  @Override
  protected void work(StorageEntry entry) {
    final var evaluator = new ConditionEvaluator(examiner, entry, cache, c);
    if (evaluator.evaluate() && !doNotExecute()) {
      results.add(entry);
    }
  }

}

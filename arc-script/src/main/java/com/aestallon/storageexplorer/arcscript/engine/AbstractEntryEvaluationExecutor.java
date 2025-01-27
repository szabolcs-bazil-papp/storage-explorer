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
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import com.aestallon.storageexplorer.core.model.entry.StorageEntry;
import com.aestallon.storageexplorer.core.service.StorageInstanceExaminer;

abstract class AbstractEntryEvaluationExecutor<RESULT, EXECUTOR extends AbstractEntryEvaluationExecutor<RESULT, EXECUTOR>> {

  abstract static class Builder<E extends AbstractEntryEvaluationExecutor<?, E>, BUILDER extends Builder<E, BUILDER>> {

    protected final StorageInstanceExaminer examiner;
    protected final Set<StorageEntry> entries;
    protected boolean useSemaphore;
    protected StorageInstanceExaminer.ObjectEntryLookupTable cache;

    protected Builder(final StorageInstanceExaminer examiner,
                      final Set<StorageEntry> entries) {
      this.examiner = Objects.requireNonNull(examiner, "StorageInstanceExaminer cannot be null!");
      this.entries = entries == null
          ? Collections.emptySet()
          : Collections.unmodifiableSet(entries);
    }

    BUILDER useSemaphore(boolean useSemaphore) {
      this.useSemaphore = useSemaphore;
      return self();
    }

    BUILDER useCache(StorageInstanceExaminer.ObjectEntryLookupTable cache) {
      this.cache = Objects.requireNonNull(
          cache,
          "Explicitly provided ObjectEntryLookupTable cannot be null!");
      return self();
    }

    abstract BUILDER self();

    abstract E build();

  }


  protected final StorageInstanceExaminer examiner;
  protected final Set<StorageEntry> entries;
  protected final boolean useSemaphore;
  protected final StorageInstanceExaminer.ObjectEntryLookupTable cache;
  protected final LinkedBlockingQueue<RESULT> results = new LinkedBlockingQueue<>();

  protected <B extends Builder<EXECUTOR, B>> AbstractEntryEvaluationExecutor(B builder) {
    examiner = builder.examiner;
    entries = builder.entries;
    useSemaphore = builder.useSemaphore;
    cache = (builder.cache == null)
        ? StorageInstanceExaminer.ObjectEntryLookupTable.newInstance()
        : builder.cache;
  }

  protected abstract boolean shortCircuit();

  protected abstract boolean doNotExecute();

  protected abstract void work(final StorageEntry entry);

  final Set<RESULT> execute() {
    if (shortCircuit()) {
      return Collections.emptySet();
    }

    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      final var semaphore = useSemaphore ? new Semaphore(5) : null;
      final List<Future<?>> futures = new ArrayList<>();
      for (final StorageEntry entry : entries) {
        futures.add(executor.submit(() -> {
          if (doNotExecute()) {
            return;
          }

          try {
            if (semaphore != null) {
              semaphore.acquire();
            }

            work(entry);


          } catch (final Exception e) {
            System.err.println(e.getMessage());
          } finally {
            if (semaphore != null) {
              semaphore.release();
            }
          }
        }));
      }

      for (final Future<?> future : futures) {
        future.get();
      }
    } catch (InterruptedException | ExecutionException e) {
      throw new IllegalStateException(e);
    }

    // there is no sense of ordering, because we are doing everything concurrently, in a
    // non-deterministic order:
    return new HashSet<>(results);
  }

}

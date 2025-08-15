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

package com.aestallon.storageexplorer.core.util;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.aestallon.storageexplorer.core.model.entry.StorageEntry;
import com.aestallon.storageexplorer.core.service.StorageInstanceExaminer;

public abstract class AbstractEntryEvaluationExecutor<RESULT, EXECUTOR extends AbstractEntryEvaluationExecutor<RESULT, EXECUTOR>> {

  private static final Logger log = LoggerFactory.getLogger(AbstractEntryEvaluationExecutor.class);


  public abstract static class Builder<E extends AbstractEntryEvaluationExecutor<?, E>, BUILDER extends Builder<E, BUILDER>> {

    protected final StorageInstanceExaminer examiner;
    protected final Set<StorageEntry> entries;
    protected boolean useSemaphore;
    protected StorageInstanceExaminer.ObjectEntryLookupTable cache;

    protected Builder(final StorageInstanceExaminer examiner,
                      final Set<StorageEntry> entries) {
      this.examiner = examiner;
      this.entries = entries == null
          ? Collections.emptySet()
          : Collections.unmodifiableSet(entries);
    }

    public final BUILDER useSemaphore(boolean useSemaphore) {
      this.useSemaphore = useSemaphore;
      return self();
    }

    public final BUILDER useCache(StorageInstanceExaminer.ObjectEntryLookupTable cache) {
      this.cache = Objects.requireNonNull(
          cache,
          "Explicitly provided ObjectEntryLookupTable cannot be null!");
      return self();
    }

    protected abstract BUILDER self();

    public abstract E build();

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

  public final Set<RESULT> execute() {
    if (shortCircuit()) {
      return Collections.emptySet();
    }

    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      final var semaphore = useSemaphore ? new Semaphore(5) : null;
      final var counter = new CountDownLatch(entries.size());
      for (final StorageEntry entry : entries) {
        executor.submit(() -> {
          if (doNotExecute()) {
            // this is our guard condition: if upon execution start the work is no longer required,
            // we can return immediately:
            counter.countDown();
            return;
          }

          try {
            if (semaphore != null) {
              semaphore.acquire();
              if (doNotExecute()) {
                // if we had a semaphore, we might have blocked above, while waiting for its
                // acquisition -> it's possible the work is no longer needed since, and we can
                // return early, without executing the work itself:
                return;
              }
            }

            work(entry);
          } catch (final InterruptedException e) {
            log.warn(e.getMessage(), e);
            Thread.currentThread().interrupt();
          } catch (final Exception e) {
            log.error(e.getMessage(), e);
          } finally {
            if (semaphore != null) {
              semaphore.release();
            }
            counter.countDown();
          }
        });
      }

      log.debug("Awaiting termination of executor...");
      counter.await();
      log.debug("Executor terminated.");

    } catch (Exception e) {
      log.warn(e.getMessage(), e);
      Thread.currentThread().interrupt();
    }

    // there is no sense of ordering, because we are doing everything concurrently, in a
    // non-deterministic order:
    return new HashSet<>(results);
  }

}

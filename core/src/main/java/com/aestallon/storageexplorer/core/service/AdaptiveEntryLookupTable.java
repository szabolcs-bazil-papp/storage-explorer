/*
 * Copyright (C) 2026 Szabolcs Bazil Papp
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

package com.aestallon.storageexplorer.core.service;

import java.time.Duration;
import java.util.function.Function;
import com.aestallon.storageexplorer.core.model.entry.ObjectEntry;
import com.aestallon.storageexplorer.core.model.loading.ObjectEntryLoadRequest;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

/**
 * A size-bounded, frequency-aware {@link StorageInstanceExaminer.ObjectEntryLookupTable}.
 *
 * <p>
 * Caffeine's W-TinyLFU eviction keeps entries which are accessed multiple times during a query
 * execution, while entries touched only once are the first to be discarded under size pressure.
 * Evicted entries are simply reloaded on next access, so eviction affects performance only, never
 * correctness.
 */
final class AdaptiveEntryLookupTable implements StorageInstanceExaminer.ObjectEntryLookupTable {

  private final Cache<ObjectEntry, ObjectEntryLoadRequest> inner;

  AdaptiveEntryLookupTable(final long maxEntries, final Duration expireAfterAccess) {
    if (maxEntries < 1L) {
      throw new IllegalArgumentException("maxEntries must be positive: " + maxEntries);
    }

    final Caffeine<Object, Object> builder = Caffeine.newBuilder()
        .maximumSize(maxEntries)
        // same-thread maintenance keeps eviction deterministic and avoids spawning background
        // threads for every query execution:
        .executor(Runnable::run);
    if (expireAfterAccess != null && !expireAfterAccess.isZero()
        && !expireAfterAccess.isNegative()) {
      builder.expireAfterAccess(expireAfterAccess);
    }
    inner = builder.build();
  }

  @Override
  public ObjectEntryLoadRequest computeIfAbsent(final ObjectEntry objectEntry,
                                                final Function<? super ObjectEntry, ? extends ObjectEntryLoadRequest> f) {
    return inner.get(objectEntry, f);
  }

  long estimatedSize() {
    inner.cleanUp();
    return inner.estimatedSize();
  }

}

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

package com.aestallon.storageexplorer.core.service.cache;

import java.net.URI;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.stream.Stream;
import com.aestallon.storageexplorer.core.model.entry.ObjectEntry;
import com.aestallon.storageexplorer.core.model.entry.ScopedEntry;
import com.aestallon.storageexplorer.core.model.entry.StorageEntry;
import com.aestallon.storageexplorer.core.model.entry.StorageEntryFactory;
import com.aestallon.storageexplorer.core.model.instance.dto.StorageId;

public interface StorageIndexCache {

  static StorageIndexCache inMemory() {
    return new StorageIndexCacheInMemory();
  }

  static StorageIndexCache persistent(final StorageId storageId, final StorageEntryFactory storageEntryFactory) {
    return new StorageIndexCacheCaffeineSqliteImpl(storageId, storageEntryFactory);
  }

  void put(final URI uri, final StorageEntry storageEntry);

  void putAll(final Map<URI, StorageEntry> storageEntries);

  void merge(final URI uri, final StorageEntry storageEntry);

  StorageEntry compute(final URI uri,
                       final BiFunction<? super URI, ? super StorageEntry, ? extends StorageEntry> f);

  void clear();

  Optional<StorageEntry> get(final URI uri);

  Stream<StorageEntry> stream();

  Stream<ObjectEntry> objectEntries();

  Stream<ScopedEntry> scopedEntries();
}

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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BiFunction;
import java.util.stream.Stream;
import com.aestallon.storageexplorer.core.model.entry.ObjectEntry;
import com.aestallon.storageexplorer.core.model.entry.ScopedEntry;
import com.aestallon.storageexplorer.core.model.entry.StorageEntry;

public final class StorageIndexCacheInMemory implements StorageIndexCache {

  private final ConcurrentMap<URI, StorageEntry> map;

  StorageIndexCacheInMemory() {
    this.map = new ConcurrentHashMap<>();
  }

  @Override
  public void put(URI uri, StorageEntry storageEntry) {
    map.put(uri, storageEntry);
  }

  @Override
  public void putAll(Map<URI, StorageEntry> storageEntries) {
    map.putAll(storageEntries);
  }

  @Override
  public void merge(URI uri, StorageEntry storageEntry) {
    map.putIfAbsent(uri, storageEntry);
  }

  @Override
  public StorageEntry compute(URI uri,
                              BiFunction<? super URI, ? super StorageEntry, ? extends StorageEntry> f) {
    return map.compute(uri, f);
  }

  @Override
  public void clear() {
    map.clear();
  }

  @Override
  public Optional<StorageEntry> get(URI uri) {
    return Optional.ofNullable(map.get(uri));
  }

  @Override
  public Stream<StorageEntry> stream() {
    return map.values().stream();
  }

  @Override
  public Stream<ObjectEntry> objectEntries() {
    return stream().filter(ObjectEntry.class::isInstance).map(ObjectEntry.class::cast);
  }

  @Override
  public Stream<ScopedEntry> scopedEntries() {
    return stream().filter(ScopedEntry.class::isInstance).map(ScopedEntry.class::cast);
  }

}

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

package com.aestallon.storageexplorer.core.service;

import java.net.URI;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.smartbit4all.api.collection.CollectionApi;
import org.smartbit4all.core.object.ObjectApi;
import com.aestallon.storageexplorer.common.util.IO;
import com.aestallon.storageexplorer.core.model.entry.StorageEntry;
import com.aestallon.storageexplorer.core.model.entry.StorageEntryFactory;
import com.aestallon.storageexplorer.core.model.instance.dto.StorageId;
import com.aestallon.storageexplorer.core.model.loading.IndexingTarget;

public final class FileSystemStorageIndex extends StorageIndex {

  private static final Logger log = LoggerFactory.getLogger(FileSystemStorageIndex.class);

  private final Path pathToStorage;

  public FileSystemStorageIndex(
      StorageId storageId,
      ObjectApi objectApi,
      CollectionApi collectionApi,
      Path pathToStorage) {
    super(storageId, objectApi, collectionApi);
    this.pathToStorage = pathToStorage;
  }

  @Override
  protected Stream<URI> fetchEntries() {
    return fetchEntries(IndexingTarget.any());
  }

  @Override
  protected Stream<URI> fetchEntries(IndexingTarget target) {
    return FileSystemStorageWalker.of(pathToStorage).walk(target);
  }

  @Override
  protected StorageEntryFactory storageEntryFactory() {
    return StorageEntryFactory.builder(storageId, objectApi, collectionApi)
        .pathToStorage(pathToStorage)
        .build();
  }
  // -----------------------------------------------------------------------------------------------
  // File system watching stuff. Copied here for later removal. All StorageIndices shall start and
  // stop watching for changes in the future by index.watcher().start(); and index.watcher().stop();

  private StorageWatchService watchService;

  void startFileSystemWatcher() {
    if (watchService != null) {
      return;
    }

    StorageWatchService.builder(pathToStorage)
        .onModified(it -> {
          final URI uri = IO.pathToUri(pathToStorage.relativize(it));
          final StorageEntry storageEntry = cache.get(uri);
          if (storageEntry != null) {
            storageEntry.refresh();
          }
        })
        .onCreated(it -> {
          final URI uri = IO.pathToUri(pathToStorage.relativize(it));
          storageEntryFactory.create(uri).ifPresent(e -> cache.put(uri, e));
        })
        .build()
        .ifPresent(it -> {
          watchService = it;
          watchService.start();
        });
  }

  void stopFileSystemWatcher() {
    if (watchService == null) {
      return;
    }

    watchService.stop();
    watchService = null;
  }

}

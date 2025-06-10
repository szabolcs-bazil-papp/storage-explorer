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

public final class FileSystemStorageIndex extends StorageIndex<FileSystemStorageIndex> {

  private static final Logger log = LoggerFactory.getLogger(FileSystemStorageIndex.class);

  private final Path pathToStorage;
  private final ObjectEntryLoadingService<FileSystemStorageIndex> objectEntryLoadingService;

  public FileSystemStorageIndex(
      StorageId storageId,
      ObjectApi objectApi,
      CollectionApi collectionApi,
      Path pathToStorage) {
    super(storageId, objectApi, collectionApi);
    this.pathToStorage = pathToStorage;
    this.objectEntryLoadingService = new ObjectEntryLoadingService.FileSystem(this);
  }

  @Override
  public ObjectEntryLoadingService<FileSystemStorageIndex> loader() {
    return objectEntryLoadingService;
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
    return StorageEntryFactory.builder(this, objectApi, collectionApi)
        .pathToStorage(pathToStorage)
        .build();
  }

}

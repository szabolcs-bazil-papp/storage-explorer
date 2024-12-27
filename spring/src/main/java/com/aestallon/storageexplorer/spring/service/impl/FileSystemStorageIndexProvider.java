package com.aestallon.storageexplorer.spring.service.impl;

import java.nio.file.Path;
import java.util.UUID;
import org.smartbit4all.api.collection.CollectionApi;
import org.smartbit4all.core.object.ObjectApi;
import com.aestallon.storageexplorer.spring.service.StorageIndexProvider;
import com.aestallon.storageexplorer.core.model.instance.dto.StorageId;
import com.aestallon.storageexplorer.core.service.FileSystemStorageIndex;
import com.aestallon.storageexplorer.core.service.StorageIndex;

public class FileSystemStorageIndexProvider implements StorageIndexProvider {
  
  private final StorageId id = new StorageId(UUID.randomUUID());
  
  private final ObjectApi objectApi;
  private final CollectionApi collectionApi;
  private final Path pathToStorage;

  public FileSystemStorageIndexProvider(final ObjectApi objectApi,
                                        final CollectionApi collectionApi,
                                        final Path pathToStorage) {
    this.objectApi = objectApi;
    this.collectionApi = collectionApi;
    this.pathToStorage = pathToStorage;
  }

  @Override
  public StorageIndex provide() {
    return new FileSystemStorageIndex(id, objectApi, collectionApi, pathToStorage);
  }
}

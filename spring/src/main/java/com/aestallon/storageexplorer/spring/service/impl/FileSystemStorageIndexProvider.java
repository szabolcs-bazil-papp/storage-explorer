package com.aestallon.storageexplorer.spring.service.impl;

import java.nio.file.Path;
import com.aestallon.storageexplorer.core.service.engine.EngineResult;
import com.aestallon.storageexplorer.core.service.engine.StorageEngine;
import com.aestallon.storageexplorer.core.service.fs.FsStorageEngine;
import com.aestallon.storageexplorer.spring.service.StorageIndexProvider;

/** Opens an {@link FsStorageEngine} directly from the host application's storage root — no
 *  platform {@code ObjectApi}/{@code CollectionApi} beans needed anymore (Milestone 1's
 *  from-scratch FS engine replaced that dependency entirely). */
public class FileSystemStorageIndexProvider implements StorageIndexProvider {

  private final Path pathToStorage;

  public FileSystemStorageIndexProvider(final Path pathToStorage) {
    this.pathToStorage = pathToStorage;
  }

  @Override
  public StorageEngine provide() {
    return switch (FsStorageEngine.open(pathToStorage.toAbsolutePath())) {
      case EngineResult.Ok<FsStorageEngine> ok -> ok.value().asStorageEngine();
      case EngineResult.Err<FsStorageEngine> err -> throw new IllegalStateException(
          "Could not open FS storage at [ " + pathToStorage + " ]: " + err.error());
    };
  }

}

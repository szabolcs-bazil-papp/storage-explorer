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

package com.aestallon.storageexplorer.core.service.walker;

import java.net.URI;
import java.nio.file.Path;
import java.util.stream.Stream;
import com.aestallon.storageexplorer.core.model.loading.IndexingTarget;

public abstract sealed class FileSystemStorageWalker permits
    FileSystemStorageWalkerImpl,
    StreamingFileSystemStorageWalker,
    VisitorFileSystemStorageWalker {

  protected final Path pathToStorage;

  protected FileSystemStorageWalker(final Path pathToStorage) {
    this.pathToStorage = pathToStorage;
  }

  public abstract Stream<URI> walk(final IndexingTarget target);

}

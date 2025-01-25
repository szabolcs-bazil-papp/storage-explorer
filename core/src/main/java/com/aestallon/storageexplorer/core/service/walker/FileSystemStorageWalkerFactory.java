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

import java.nio.file.Path;

public final class FileSystemStorageWalkerFactory {

  public enum Type { LEGACY_0, LEGACY_1, DEFAULT }

  public static FileSystemStorageWalker createDefault(final Path pathToStorage) {
    return create(pathToStorage, Type.DEFAULT);
  }

  public static FileSystemStorageWalker create(final Path pathToStorage, final Type type) {
    if (pathToStorage == null || !pathToStorage.isAbsolute()) {
      throw new IllegalArgumentException("Path to storage must be absolute!");
    }

    return switch (type) {
      case LEGACY_0 -> new StreamingFileSystemStorageWalker(pathToStorage);
      case LEGACY_1 -> new VisitorFileSystemStorageWalker(pathToStorage);
      case DEFAULT -> new FileSystemStorageWalkerImpl(pathToStorage);
    };
  }

}

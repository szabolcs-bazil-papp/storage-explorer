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

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.aestallon.storageexplorer.common.util.IO;
import com.aestallon.storageexplorer.core.model.loading.IndexingTarget;

public final class StreamingFileSystemStorageWalker extends FileSystemStorageWalker {
  
  private static final Logger log = LoggerFactory.getLogger(StreamingFileSystemStorageWalker.class);
  
  StreamingFileSystemStorageWalker(Path pathToStorage) {
    super(pathToStorage);
  }

  @Override
  public Stream<URI> walk(IndexingTarget target) {
    try (final var files = Files.walk(pathToStorage)) {
      final var uris = files
          .filter(p -> p.toFile().isFile())
          .filter(p -> p.getFileName().toString().endsWith(".o"))
          .map(pathToStorage::relativize)
          .map(IO::pathToUri)
          .filter(Objects::nonNull)
          .toList();
      // we have to do this -> we must consume the Stream before the resources are closed...
      return uris.stream();
    } catch (IOException e) {
      log.error(e.getMessage(), e);
      return Stream.empty();
    }
  }
  
  private static Function<String, Optional<URI>> findObjectUri() {
    return IO::findObjectUri;
  }
}

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
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.EnumSet;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.aestallon.storageexplorer.common.util.IO;
import com.aestallon.storageexplorer.core.model.loading.IndexingTarget;

public final class VisitorFileSystemStorageWalker extends FileSystemStorageWalker {

  private static final Logger log = LoggerFactory.getLogger(VisitorFileSystemStorageWalker.class);
  
  VisitorFileSystemStorageWalker(Path pathToStorage) {
    super(pathToStorage);
  }

  @Override
  public Stream<URI> walk(IndexingTarget target) {
    final BlockingQueue<URI> queue = new LinkedBlockingQueue<>();
    final StorageFileVisitor visitor = new StorageFileVisitor(pathToStorage, queue);
    try {
      Files.walkFileTree(pathToStorage, EnumSet.noneOf(FileVisitOption.class), 20, visitor);
    } catch (IOException e) {
      log.error(e.getMessage(), e);
    }

    return queue.stream();
  }

  private static final class StorageFileVisitor extends SimpleFileVisitor<Path> {

    private final Path pathToStorage;
    private final BlockingQueue<URI> queue;

    private StorageFileVisitor(Path pathToStorage, BlockingQueue<URI> queue) {
      this.pathToStorage = pathToStorage;
      this.queue = queue;
    }

    @Override
    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
        throws IOException {
      if (dir.toString().toLowerCase().contains("applicationruntimedata")) {
        return FileVisitResult.SKIP_SIBLINGS;
      }

      return super.preVisitDirectory(dir, attrs);
    }

    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
        throws IOException {
      if (file.toString().toLowerCase().contains("applicationruntimedata")) {
        return FileVisitResult.SKIP_SIBLINGS;
      }

      if (Files.isDirectory(file)) {
        return FileVisitResult.CONTINUE;
      }

      if (!file.getFileName().toString().endsWith(".o")) {
        return FileVisitResult.CONTINUE;
      }

      final Path relativePath = pathToStorage.relativize(file);
      final URI uri = IO.pathToUri(relativePath);
      if (uri != null) {
        queue.offer(uri);
      } else {
        log.warn("Encountered NULL when converting Path to URI: {}", relativePath);
      }

      return FileVisitResult.SKIP_SUBTREE;
    }

    @Override
    public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
      return FileVisitResult.SKIP_SUBTREE;
    }

  }
  
}

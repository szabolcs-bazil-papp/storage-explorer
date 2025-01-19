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

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.aestallon.storageexplorer.common.util.IO;
import com.aestallon.storageexplorer.core.model.loading.IndexingTarget;
import static java.util.stream.Collectors.toSet;

/**
 * Hand-rolled File System Storage Walker, which is 2.6-6.8 times faster than the legacy FileVisitor
 * implementation (which was already twice as fast as the first, naive
 * {@link Files#walk(Path, FileVisitOption...)} implementation).
 *
 * <p>
 * This walker uses virtual threads to efficiently span the entire FS base directory and look for
 * object files. It only continues on special directories, which may contain further nested object
 * files unexpected for user defined schemas.
 *
 * <p>
 * This implementation swallows every potential exception (the end user should detect the indexing
 * performed was only partial). A later implementation should be able to bubble up an error value.
 *
 * @author Szabolcs Bazil Papp
 */
public final class FileSystemStorageWalker {

  private static final Logger log = LoggerFactory.getLogger(FileSystemStorageWalker.class);

  private static final String SPECIAL_SCHEMA_APIS = "apis";
  private static final Set<String> SPECIAL_DIRS = Set.of(
      "objectDefinition",
      SPECIAL_SCHEMA_APIS,
      "storedSeq");

  static FileSystemStorageWalker of(final Path pathToStorage) {
    if (pathToStorage == null || !pathToStorage.isAbsolute()) {
      throw new IllegalArgumentException("Path to storage must be absolute!");
    }
    return new FileSystemStorageWalker(pathToStorage);
  }

  private final Path pathToStorage;

  private FileSystemStorageWalker(Path pathToStorage) {
    this.pathToStorage = pathToStorage;
  }

  Stream<URI> walk(final IndexingTarget target) {
    final LinkedBlockingQueue<URI> queue = new LinkedBlockingQueue<>();
    final List<Thread> virtualThreads = new ArrayList<>();
    final var typeWalkers = schemaWalkers(target).stream()
        .flatMap(it -> it.typeWalkers(target, queue).stream())
        .collect(toSet());
    for (final var walker : typeWalkers) {
      virtualThreads.add(walker.walk(queue));
    }
    forEach(virtualThreads, Thread::join);

    return new ArrayList<>(queue).stream();
  }

  private List<SchemaWalker> schemaWalkers(final IndexingTarget target) {
    final Predicate<Path> p = it -> {
      final var f = it.toFile();
      return f.isDirectory() && (target.schemas().isEmpty() || target.schemas()
          .contains(f.getName()));
    };

    try (final var children = Files.list(pathToStorage)) {
      return children
          .filter(p)
          .map(it -> new SchemaWalker(pathToStorage, it.getFileName()))
          .toList();
    } catch (IOException e) {
      log.error(e.getMessage(), e);
      return Collections.emptyList();
    }
  }


  private record SchemaWalker(Path root, Path schemaFolder) {

    private List<TypeWalker> typeWalkers(final IndexingTarget target,
                                         LinkedBlockingQueue<URI> queue) {
      final Predicate<Path> p = it -> it.toFile().isDirectory()
                                      && (target.types().isEmpty()) || target.types().stream()
                                          .anyMatch(it::endsWith);
      try (final var childrenStream = Files.list(root.resolve(schemaFolder))) {
        final List<Path> children = childrenStream.toList();
        final var typesWalkers = children.stream()
            .filter(p)
            .map(it -> new TypeWalker(root, schemaFolder.resolve(it.getFileName())))
            .toList();
        if (schemaFolder.getFileName().toString().equals(SPECIAL_SCHEMA_APIS)) {
          for (final var child : children) {
            if (Files.isRegularFile(child) && child.getFileName().toString().endsWith(".o")) {
              final URI uri = IO.pathToUri(root.relativize(child));
              if (uri != null) {
                queue.offer(uri);
              }
            }
          }
        }
        return typesWalkers;
      } catch (IOException e) {
        log.error(e.getMessage(), e);
        return Collections.emptyList();
      }
    }
  }


  private record TypeWalker(Path root, Path typeFolder) {

    private Thread walk(LinkedBlockingQueue<URI> queue) {
      final var absolute = root.resolve(typeFolder);
      return processDir(root, absolute, queue);
    }
  }

  private static Thread processDir(final Path root,
                                   final Path dir,
                                   final LinkedBlockingQueue<URI> queue) {
    return Thread.ofVirtual().start(() -> {
      try (final var es = Files.list(dir)) {
        final var children = es.collect(toSet());
        final Set<Path> oFiles = new HashSet<>();
        final Set<Path> subDirs = new HashSet<>();
        for (final Path child : children) {
          if (Files.isRegularFile(child) && child.getFileName().toString().endsWith(".o")) {
            oFiles.add(child);
          }

          if (Files.isDirectory(child)) {
            subDirs.add(child);
          }
        }

        if (!oFiles.isEmpty()) {
          oFiles.stream()
              .map(root::relativize)
              .map(IO::pathToUri)
              .filter(Objects::nonNull)
              .forEach(queue::offer);
          final var dirStr = dir.toString();
          if (SPECIAL_DIRS.stream().noneMatch(dirStr::contains)) {
            return;
          }
        }

        if (!subDirs.isEmpty()) {
          final var futures = subDirs.stream()
              .map(it -> processDir(root, it, queue))
              .toList();
          forEach(futures, Thread::join);
        }

      } catch (IOException e) {
        log.error(e.getMessage(), e);
      }
    });
  }

  @FunctionalInterface
  private interface CheckedConsumer<T> {
    void accept(T t) throws Exception;
  }

  private static <E> void forEach(Collection<E> es, CheckedConsumer<? super E> f) {
    for (final E e : es) {
      try {
        f.accept(e);
      } catch (final Exception ex) {
        log.error(ex.getMessage(), ex);
      }
    }
  }

}

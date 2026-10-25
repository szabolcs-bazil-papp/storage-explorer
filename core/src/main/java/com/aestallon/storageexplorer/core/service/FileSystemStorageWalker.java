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
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Predicate;
import static java.util.stream.Collectors.toSet;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.aestallon.storageexplorer.common.util.IO;
import com.aestallon.storageexplorer.core.model.loading.IndexingTarget;

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
 * The stream returned by {@link #walk(IndexingTarget)} is <em>lazily populated</em>: URIs become
 * available to the consumer as walker threads discover them, without waiting for the full traversal
 * to complete. Closing the stream cancels the traversal; an unclosed, undrained stream leaks walker
 * threads, so callers must consume or close it.
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

  /**
   * Bounds how far discovery may run ahead of the consumer. Not a tuning knob for consumers: the
   * query pipeline applies its own demand discipline downstream.
   */
  private static final int QUEUE_CAPACITY = 4_096;
  private static final long OFFER_POLL_TIMEOUT_MS = 50L;

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
    final var discovery = new Discovery();
    // the coordinator performs the directory listing I/O off the caller's thread, starts the
    // type walkers, and signals completion only once every walker has terminated - elements
    // enqueued before the done flag flips are always observed by the consumer's re-poll:
    final Thread coordinator = Thread.ofVirtual().start(() -> {
      try {
        final var typeWalkers = schemaWalkers(target).stream()
            .flatMap(it -> it.typeWalkers(target, discovery).stream())
            .collect(toSet());
        final List<Thread> virtualThreads = typeWalkers.stream()
            .map(walker -> walker.walk(discovery))
            .toList();
        forEach(virtualThreads, Thread::join);
      } finally {
        discovery.done.set(true);
      }
    });

    return StreamSupport
        .stream(new UriSpliterator(discovery), false)
        .onClose(() -> {
          discovery.cancelled.set(true);
          // unblock producers parked on a saturated queue; anything they enqueue afterwards is
          // garbage-collected along with the queue:
          discovery.queue.clear();
          try {
            coordinator.join(TimeUnit.SECONDS.toMillis(5L));
          } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
          }
        });
  }

  /** Shared state between the consumer-facing stream and the producing walker threads. */
  private static final class Discovery {
    private final LinkedBlockingQueue<URI> queue = new LinkedBlockingQueue<>(QUEUE_CAPACITY);
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private final AtomicBoolean done = new AtomicBoolean();

    private boolean proceed() {
      return !cancelled.get();
    }

    /**
     * Enqueues a discovered URI, waiting for downstream demand if the queue is saturated - but
     * never parking indefinitely: cancellation is re-checked between bounded offer attempts, so a
     * departed consumer cannot strand producer threads.
     */
    private void emit(final URI uri) {
      try {
        while (proceed()) {
          if (queue.offer(uri, OFFER_POLL_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            return;
          }
        }
      } catch (final InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
  }


  private static final class UriSpliterator extends Spliterators.AbstractSpliterator<URI> {

    private final Discovery discovery;

    private UriSpliterator(final Discovery discovery) {
      super(Long.MAX_VALUE, Spliterator.ORDERED | Spliterator.NONNULL);
      this.discovery = discovery;
    }

    @Override
    public boolean tryAdvance(final Consumer<? super URI> action) {
      try {
        while (true) {
          final URI uri = discovery.queue.poll(OFFER_POLL_TIMEOUT_MS, TimeUnit.MILLISECONDS);
          if (uri != null) {
            action.accept(uri);
            return true;
          }
          // a null poll is only terminal if the producers have all finished AND the queue has
          // been re-checked afterwards - the done flag is set strictly after the final offer:
          if (discovery.done.get() && discovery.queue.isEmpty()) {
            return false;
          }
        }
      } catch (final InterruptedException e) {
        Thread.currentThread().interrupt();
        return false;
      }
    }
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
      log.error("Error during walking File System Storage at [ root: {} ] for target: [ {} ]: {}",
          pathToStorage, target, e.getMessage());
      log.debug(e.getMessage(), e);
      return Collections.emptyList();
    }
  }


  private record SchemaWalker(Path root, Path schemaFolder) {

    private List<TypeWalker> typeWalkers(final IndexingTarget target, final Discovery discovery) {
      if (!discovery.proceed()) {
        return Collections.emptyList();
      }

      final Predicate<Path> p = it -> it.toFile().isDirectory()
          && (target.types().isEmpty()) || target.types().stream()
          .anyMatch(t -> it.toString().endsWith(t));
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
                discovery.emit(uri);
              }
            }
          }
        }
        return typesWalkers;
      } catch (IOException e) {
        log.error("Error during walking schema [ {} ] of File System Storage: {}",
            schemaFolder, e.getMessage());
        log.debug(e.getMessage(), e);
        return Collections.emptyList();
      }
    }
  }


  private record TypeWalker(Path root, Path typeFolder) {

    private Thread walk(final Discovery discovery) {
      final var absolute = root.resolve(typeFolder);
      return processDir(root, absolute, discovery);
    }
  }

  private static Thread processDir(final Path root,
                                   final Path dir,
                                   final Discovery discovery) {
    return Thread.ofVirtual().start(() -> {
      if (!discovery.proceed()) {
        return;
      }

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
              .forEach(discovery::emit);
          final var dirStr = dir.toString();
          if (SPECIAL_DIRS.stream().noneMatch(dirStr::contains)) {
            return;
          }
        }

        if (!subDirs.isEmpty() && discovery.proceed()) {
          final var futures = subDirs.stream()
              .map(it -> processDir(root, it, discovery))
              .toList();
          forEach(futures, Thread::join);
        }

      } catch (IOException e) {
        log.error("Error processing directory [ {} ] of File System Storage: {}",
            dir, e.getMessage());
        log.debug(e.getMessage(), e);
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
        log.error("Unexpected error: {}", ex.getMessage());
        log.debug(ex.getMessage(), ex);
      }
    }
  }

}

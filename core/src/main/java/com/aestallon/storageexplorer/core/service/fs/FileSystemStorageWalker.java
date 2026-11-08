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

package com.aestallon.storageexplorer.core.service.fs;

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
 * persisted entries. A folder is read in full regardless of what it already yielded: finding
 * {@code .o}/{@code .td}/{@code .os} files in it is not a reason to stop descending into its
 * subfolders — matching {@code ../sb4a-fs}'s own {@code list.rs}, the correctness reference for
 * this storage format (an object's own version subfolder never holds a further {@code .o}, so
 * descending into it costs a wasted listing but never a wrong result; a schema/type folder may
 * legitimately nest further type structure below a level that already has entries, which a
 * stop-on-first-find rule would silently miss). An earlier version of this walker special-cased
 * three hardcoded schema/type names (`apis`, `objectDefinition`, `storedSeq`) to work around
 * exactly that miss — removed in favor of this general rule, per the implementation plan's T1.8.
 *
 * <p>
 * The stream returned by {@link #walk(IndexingTarget)}/{@link #walkAll(IndexingTarget)} is
 * <em>lazily populated</em>: items become available to the consumer as walker threads discover
 * them, without waiting for the full traversal to complete. Closing the stream cancels the
 * traversal; an unclosed, undrained stream leaks walker threads, so callers must consume or close
 * it.
 *
 * <p>
 * This implementation swallows every potential exception (the end user should detect the indexing
 * performed was only partial). A later implementation should be able to bubble up an error value.
 *
 * @author Szabolcs Bazil Papp
 */
public final class FileSystemStorageWalker {

  private static final Logger log = LoggerFactory.getLogger(FileSystemStorageWalker.class);

  /** What kind of entry a discovered file represents. */
  public enum Kind {
    /** An object with a descriptor ({@code .o}). */
    OBJECT,
    /** Serialized table data ({@code .td}). */
    TABLE_DATA,
    /** An object stream ({@code .os}). */
    OBJECT_STREAM
  }

  /** One discovered file, kind-tagged, with its absolute path on disk. */
  public record Found(Path file, Kind kind) {}

  /**
   * Bounds how far discovery may run ahead of the consumer. Not a tuning knob for consumers: the
   * query pipeline applies its own demand discipline downstream.
   */
  private static final int QUEUE_CAPACITY = 4_096;
  private static final long OFFER_POLL_TIMEOUT_MS = 50L;

  public static FileSystemStorageWalker of(final Path pathToStorage) {
    if (pathToStorage == null || !pathToStorage.isAbsolute()) {
      throw new IllegalArgumentException("Path to storage must be absolute!");
    }
    return new FileSystemStorageWalker(pathToStorage);
  }

  private final Path pathToStorage;

  private FileSystemStorageWalker(Path pathToStorage) {
    this.pathToStorage = pathToStorage;
  }

  /**
   * The legacy-shaped stream of discovered object ({@code .o}) URIs — unchanged consumer contract
   * for {@code FileSystemStorageIndex}, now backed by the fixed-up, kind-aware traversal.
   */
  public Stream<URI> walk(final IndexingTarget target) {
    return walkAll(target)
        .filter(f -> f.kind() == Kind.OBJECT)
        .map(f -> IO.pathToUri(pathToStorage.relativize(f.file())))
        .filter(Objects::nonNull);
  }

  /** Every discovered entry — objects, table data, and object streams — kind-tagged. */
  public Stream<Found> walkAll(final IndexingTarget target) {
    final var discovery = new Discovery();
    // the coordinator performs the directory listing I/O off the caller's thread, starts the
    // type walkers, and signals completion only once every walker has terminated - elements
    // enqueued before the done flag flips are always observed by the consumer's re-poll:
    final Thread coordinator = Thread.ofVirtual().start(() -> {
      try {
        final List<Thread> threads = schemaWalkers(target).stream()
            .flatMap(it -> it.walk(target, discovery).stream())
            .toList();
        forEach(threads, Thread::join);
      } finally {
        discovery.done.set(true);
      }
    });

    return StreamSupport
        .stream(new FoundSpliterator(discovery), false)
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
    private final LinkedBlockingQueue<Found> queue = new LinkedBlockingQueue<>(QUEUE_CAPACITY);
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private final AtomicBoolean done = new AtomicBoolean();

    private boolean proceed() {
      return !cancelled.get();
    }

    /**
     * Enqueues a discovered item, waiting for downstream demand if the queue is saturated - but
     * never parking indefinitely: cancellation is re-checked between bounded offer attempts, so a
     * departed consumer cannot strand producer threads.
     */
    private void emit(final Found found) {
      try {
        while (proceed()) {
          if (queue.offer(found, OFFER_POLL_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            return;
          }
        }
      } catch (final InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
  }


  private static final class FoundSpliterator extends Spliterators.AbstractSpliterator<Found> {

    private final Discovery discovery;

    private FoundSpliterator(final Discovery discovery) {
      super(Long.MAX_VALUE, Spliterator.ORDERED | Spliterator.NONNULL);
      this.discovery = discovery;
    }

    @Override
    public boolean tryAdvance(final Consumer<? super Found> action) {
      try {
        while (true) {
          final Found found = discovery.queue.poll(OFFER_POLL_TIMEOUT_MS, TimeUnit.MILLISECONDS);
          if (found != null) {
            action.accept(found);
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

    /**
     * No type filter: the whole schema folder is one recursive walk, starting at the schema
     * folder itself (its own direct files count too, same as every other folder). A type filter
     * narrows which first-level subfolders of the schema folder are walked, each still fully
     * recursively — matching {@code list.rs}'s own {@code starts} selection.
     */
    private List<Thread> walk(final IndexingTarget target, final Discovery discovery) {
      if (!discovery.proceed()) {
        return Collections.emptyList();
      }

      final Path schemeDir = root.resolve(schemaFolder);
      if (target.types().isEmpty()) {
        return List.of(processDir(root, schemeDir, discovery));
      }

      try (final var childrenStream = Files.list(schemeDir)) {
        return childrenStream
            .filter(it -> Files.isDirectory(it) && target.types().stream()
                .anyMatch(t -> it.toString().endsWith(t)))
            .map(it -> processDir(root, it, discovery))
            .toList();
      } catch (IOException e) {
        log.error("Error during walking schema [ {} ] of File System Storage: {}",
            schemaFolder, e.getMessage());
        log.debug(e.getMessage(), e);
        return Collections.emptyList();
      }
    }
  }

  private static Kind kindOf(final Path file) {
    final String name = file.getFileName().toString();
    if (name.endsWith(".o")) {
      return Kind.OBJECT;
    }
    if (name.endsWith(".td")) {
      return Kind.TABLE_DATA;
    }
    if (name.endsWith(".os")) {
      return Kind.OBJECT_STREAM;
    }
    return null;
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
        final Set<Path> subDirs = new HashSet<>();
        for (final Path child : children) {
          if (Files.isDirectory(child)) {
            subDirs.add(child);
            continue;
          }

          final Kind kind = kindOf(child);
          if (kind != null) {
            discovery.emit(new Found(child, kind));
          }
        }

        // every subfolder is read, regardless of what this folder already yielded - see the
        // class javadoc for why that's the correct general rule, not an optimization to undo.
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

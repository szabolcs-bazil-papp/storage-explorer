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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.toList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.smartbit4all.core.object.ObjectNode;
import com.aestallon.storageexplorer.common.util.IO;
import com.aestallon.storageexplorer.core.event.LoadingQueueSize;
import com.aestallon.storageexplorer.core.model.entry.ObjectEntry;
import com.aestallon.storageexplorer.core.model.loading.ObjectEntryLoadRequest;
import com.aestallon.storageexplorer.core.model.loading.ObjectEntryLoadResult;
import com.aestallon.storageexplorer.core.util.Uris;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;

public abstract sealed class ObjectEntryLoadingService<T extends StorageIndex<T>> {

  private static final Logger log = LoggerFactory.getLogger(ObjectEntryLoadingService.class);

  public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  protected final T storageIndex;

  protected ObjectEntryLoadingService(final T storageIndex) {
    this.storageIndex = storageIndex;
  }

  public abstract ObjectEntryLoadRequest load(final ObjectEntry objectEntry);

  public abstract ObjectEntryLoadResult.SingleVersion.Eager loadExact(final URI uri,
                                                                      final long version);

  protected final ObjectEntryLoadResult loadInner(final ObjectEntry objectEntry,
                                                  final ObjectNode node) {
    try {
      final ObjectEntryLoadResult ret;
      if (!objectEntry.valid() && node != null) {
        objectEntry.refresh(node);
      }

      if (Uris.isSingleVersion(objectEntry.uri())) {
        ret = (node != null)
            ? ObjectEntryLoadResult.singleVersion(node, OBJECT_MAPPER)
            : ObjectEntryLoadResult.err("Failed to retrieve single version object entry!");
      } else {
        ret = (node != null)
            ? ObjectEntryLoadResult.multiVersion(
            node,
            this::loadExact,
            OBJECT_MAPPER,
            Long.MAX_VALUE)
            : ObjectEntryLoadResult.err("Failed to retrieve multi version object entry!");
      }

      return ret;
    } catch (Throwable t) {
      final String msg = String.format("Could not load Object Entry [ %s ] : %s",
          objectEntry.uri(),
          t.getMessage());
      log.error(msg);
      log.error(t.getMessage(), t);

      return ObjectEntryLoadResult.err(msg);
    }
  }

  protected final ObjectEntryLoadResult loadInner(final ObjectEntry objectEntry,
                                                  final ObjectEntryLoadResult headLoadResult) {
    if (headLoadResult.isErr()) {
      return headLoadResult;
    }

    final ObjectEntryLoadResult.SingleVersion head = switch (headLoadResult) {
      case ObjectEntryLoadResult.SingleVersion sv -> sv;
      case ObjectEntryLoadResult.MultiVersion mv -> mv.head();
      default -> throw new AssertionError("Unexpected head load result " + headLoadResult);
    };
    if (!objectEntry.valid()) {
      objectEntry.refresh(
          head.objectAsMap(),
          headLoadResult instanceof ObjectEntryLoadResult.MultiVersion(var versions)
              ? versions.size()
              : -1L);
    }

    if (headLoadResult instanceof ObjectEntryLoadResult.SingleVersion sv) {
      return sv;
    }

    return ObjectEntryLoadResult.multiVersion(head, this::loadExact, Long.MAX_VALUE);
  }



  static final class FileSystem extends ObjectEntryLoadingService<FileSystemStorageIndex> {

    FileSystem(FileSystemStorageIndex storageIndex) {
      super(storageIndex);
    }

    @Override
    public ObjectEntryLoadRequest load(ObjectEntry objectEntry) {
      return new ObjectEntryLoadRequest.FileSystemObjectEntryLoadRequest(loadInner(objectEntry));
    }

    @Override
    public ObjectEntryLoadResult.SingleVersion.Eager loadExact(URI uri, long version) {
      return (ObjectEntryLoadResult.SingleVersion.Eager) ObjectEntryLoadResult.singleVersion(
          Uris.isSingleVersion(uri)
              ? storageIndex.objectApi.loadLatest(uri, null)
              : storageIndex.objectApi.load(Uris.atVersion(uri, version)),
          OBJECT_MAPPER);
    }

    private ObjectEntryLoadResult loadInner(final ObjectEntry objectEntry) {
      final var node = loadObjectNode(objectEntry);
      return loadInner(objectEntry, node);
    }

    private ObjectNode loadObjectNode(ObjectEntry entry) {
      if (Uris.isSingleVersion(entry.uri())) {
        // We have to do this...
        return tryDeserialise(entry)
            .map(it -> storageIndex.objectApi.create(null, it))
            .orElse(null);
      }
      try {
        return storageIndex.objectApi.loadLatest(entry.uri());
      } catch (final Exception e) {
        log.error(e.getMessage(), e);
        return null;
      }
    }

    private Optional<Map<?, ?>> tryDeserialise(final ObjectEntry entry) {
      final var entryPath = entry.path();
      // ObjectEntry::path is never null here, because FileSystemStorageIndex guarantees it!
      assert entryPath != null;
      final String rawContent = IO.read(entryPath);
      if (Strings.isNullOrEmpty(rawContent)) {
        log.error("Empty content found during deserialisation attempt of [ {} ]", entry.uri());
        return Optional.empty();
      }

      try {
        final Map<?, ?> res = storageIndex.objectApi
            .getDefaultSerializer()
            .fromString(rawContent, LinkedHashMap.class);
        return Optional.of(res);
      } catch (IOException e) {
        log.error("Error during deserialisation attempt of [ {} ]", entry.uri());
        log.error(e.getMessage(), e);
        return Optional.empty();
      }
    }

  }


  public record RelationalDatabaseLoadingServiceParameters(
      int batchSize,
      int timeoutMillisMin,
      int timeoutMillisMax) {

    public static final RelationalDatabaseLoadingServiceParameters DEFAULT =
        new RelationalDatabaseLoadingServiceParameters(150, 20, 2_000);

  }


  static final class RelationalDatabase
      extends ObjectEntryLoadingService<RelationalDatabaseStorageIndex> {

    private record LoadingTask(ObjectEntry objectEntry) {}


    private final Map<LoadingTask, CompletableFuture<ObjectEntryLoadResult>> pendingRequests;
    private final BlockingQueue<LoadingTask> queue;
    // TODO: we keep a reference because we need to shut this down if the storage is deleted!
    private final Thread worker;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final AtomicReference<RelationalDatabaseLoadingServiceParameters> params;
    private final AtomicInteger timeoutMillis;

    RelationalDatabase(RelationalDatabaseStorageIndex storageIndex) {
      super(storageIndex);
      this.pendingRequests = new ConcurrentHashMap<>();
      this.queue = new LinkedBlockingQueue<>();
      params = new AtomicReference<>(RelationalDatabaseLoadingServiceParameters.DEFAULT);
      timeoutMillis =
          new AtomicInteger(RelationalDatabaseLoadingServiceParameters.DEFAULT.timeoutMillisMax());
      worker = startWorker();
    }

    private Thread startWorker() {
      return Thread.ofPlatform().name("Batch Loader").start(() -> {
        while (!Thread.currentThread().isInterrupted()) {
          try {
            processBatch();
          } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            break;
          }
        }
      });
    }

    @Override
    public ObjectEntryLoadResult.SingleVersion.Eager loadExact(URI uri, long version) {
      return storageIndex.loadSingle(uri, version);
    }

    @Override
    public ObjectEntryLoadRequest load(final ObjectEntry objectEntry) {
      final var f = pendingRequests.computeIfAbsent(
          new LoadingTask(objectEntry),
          k -> {
            queue.add(k);
            return new CompletableFuture<>();
          });
      return new ObjectEntryLoadRequest.RelationalDatabaseObjectEntryLoadRequest(f);
    }

    private void processBatch() throws InterruptedException {
      final var params = this.params.get();
      final var batchSize = params.batchSize;
      final var timeoutMillisMax = params.timeoutMillisMax;
      final var timeoutMillisMin = params.timeoutMillisMin;

      storageIndex.publishEvent(new LoadingQueueSize(storageIndex.id(), queue.size()));

      final List<LoadingTask> batch = new ArrayList<>(batchSize);
      final var t = queue.poll(timeoutMillis.get(), TimeUnit.MILLISECONDS);
      if (t != null) {
        timeoutMillis.set(timeoutMillisMin);
        batch.add(t);
        queue.drainTo(batch, batchSize - 1);
      } else {
        timeoutMillis.getAndUpdate(i -> (i + timeoutMillisMax) / 2);
      }

      if (!batch.isEmpty()) {
        final List<ObjectEntryLoadResult> results = batch.stream()
            .map(LoadingTask::objectEntry)
            .map(ObjectEntry::uri)
            .collect(collectingAndThen(toList(), storageIndex::loadBatch));
        for (int i = 0; i < results.size(); i++) {
          final LoadingTask task = batch.get(i);
          final ObjectEntry e = task.objectEntry();
          final ObjectEntryLoadResult loadResult = results.get(i);
          executor.submit(() -> {
            final ObjectEntryLoadResult r = loadInner(e, loadResult);
            final CompletableFuture<ObjectEntryLoadResult> f = pendingRequests.remove(task);
            if (f != null) {
              f.complete(r);
            }
          });
        }
      }
    }

  }

}

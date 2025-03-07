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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.smartbit4all.core.object.ObjectNode;
import com.aestallon.storageexplorer.common.util.IO;
import com.aestallon.storageexplorer.common.util.Uris;
import com.aestallon.storageexplorer.core.model.entry.ObjectEntry;
import com.aestallon.storageexplorer.core.model.loading.ObjectEntryLoadRequest;
import com.aestallon.storageexplorer.core.model.loading.ObjectEntryLoadResult;
import com.aestallon.storageexplorer.core.model.loading.ObjectEntryLoadResults;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.toList;

public sealed abstract class ObjectEntryLoadingService {

  private static final Logger log = LoggerFactory.getLogger(ObjectEntryLoadingService.class);

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  protected final StorageIndex storageIndex;

  protected ObjectEntryLoadingService(final StorageIndex storageIndex) {
    this.storageIndex = storageIndex;
  }

  public ObjectEntryLoadRequest load(final ObjectEntry objectEntry) {
    return load(objectEntry, false);
  }

  public abstract ObjectEntryLoadRequest load(final ObjectEntry objectEntry,
                                              final boolean headVersionOnly);

  protected final ObjectEntryLoadResult loadInner(final ObjectEntry objectEntry,
                                                  final ObjectNode node,
                                                  final boolean headVersionOnly) {
    try {
      final ObjectEntryLoadResult ret;
      if (!objectEntry.valid() && node != null) {
        objectEntry.refresh(node);
      }

      if (Uris.isSingleVersion(objectEntry.uri())) {
        ret = (node != null)
            ? ObjectEntryLoadResults.singleVersion(node, OBJECT_MAPPER)
            : ObjectEntryLoadResults.err("Failed to retrieve single version object entry!");
      } else {
        final long v = headVersionOnly ? 1L : Long.MAX_VALUE;
        ret = (node != null)
            ? ObjectEntryLoadResults.multiVersion(node, storageIndex.objectApi, OBJECT_MAPPER, v)
            : ObjectEntryLoadResults.err("Failed to retrieve multi version object entry!");
      }

      return ret;
    } catch (Throwable t) {
      final String msg = String.format("Could not load Object Entry [ %s ] : %s",
          objectEntry.uri(),
          t.getMessage());
      log.error(msg);
      log.error(t.getMessage(), t);

      return ObjectEntryLoadResults.err(msg);
    }
  }



  static final class FileSystem extends ObjectEntryLoadingService {

    FileSystem(FileSystemStorageIndex storageIndex) {
      super(storageIndex);
    }

    @Override
    public ObjectEntryLoadRequest load(ObjectEntry objectEntry,
                                       boolean headVersionOnly) {
      return new ObjectEntryLoadRequest.FileSystemObjectEntryLoadRequest(loadInner(
          objectEntry,
          headVersionOnly));
    }

    private ObjectEntryLoadResult loadInner(final ObjectEntry objectEntry,
                                            final boolean headVersionOnly) {
      final var node = loadObjectNode(objectEntry);
      return loadInner(objectEntry, node, headVersionOnly);
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
        new RelationalDatabaseLoadingServiceParameters(500, 20, 2_000);

  }


  static final class RelationalDatabase extends ObjectEntryLoadingService {

    private record LoadingTask(ObjectEntry objectEntry, boolean headVersionOnly) {}


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
        while (true) {
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
    public ObjectEntryLoadRequest load(final ObjectEntry objectEntry,
                                       final boolean headVersionOnly) {
      final var f = pendingRequests.computeIfAbsent(
          new LoadingTask(objectEntry, headVersionOnly),
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
        final List<ObjectNode> nodes = batch.stream()
            .map(LoadingTask::objectEntry)
            .map(ObjectEntry::uri)
            .collect(collectingAndThen(toList(), storageIndex.objectApi::loadBatch));
        // instead of logging here, we should emit an event, and display this on the UI...
        log.info("Batch loaded [ {} ] | Remaining queue size [ {} ]", nodes.size(), queue.size());
        for (int i = 0; i < nodes.size(); i++) {
          final LoadingTask task = batch.get(i);
          final ObjectEntry e = task.objectEntry();
          final boolean headVersionOnly = task.headVersionOnly();
          final ObjectNode n = nodes.get(i);
          executor.submit(() -> {
            final ObjectEntryLoadResult r = loadInner(e, n, headVersionOnly);
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

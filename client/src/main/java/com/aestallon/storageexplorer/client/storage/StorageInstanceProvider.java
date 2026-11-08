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

package com.aestallon.storageexplorer.client.storage;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import com.aestallon.storageexplorer.client.userconfig.model.Problem;
import com.aestallon.storageexplorer.client.userconfig.service.ProblemService;
import com.aestallon.storageexplorer.client.userconfig.service.TypeInfoRepository;
import com.aestallon.storageexplorer.client.userconfig.service.UserConfigService;
import com.aestallon.storageexplorer.client.util.OpResult;
import com.aestallon.storageexplorer.common.event.bgwork.BackgroundWorkCompletedEvent;
import com.aestallon.storageexplorer.common.event.bgwork.BackgroundWorkStartedEvent;
import com.aestallon.storageexplorer.common.event.msg.Msg;
import com.aestallon.storageexplorer.core.event.StorageImportEvent;
import com.aestallon.storageexplorer.core.event.StorageIndexDiscardedEvent;
import com.aestallon.storageexplorer.core.event.StorageReimportedEvent;
import com.aestallon.storageexplorer.core.event.StorageReindexed;
import com.aestallon.storageexplorer.core.event.TypeInfoUpdated;
import com.aestallon.storageexplorer.core.model.entry.StorageEntry;
import com.aestallon.storageexplorer.core.model.instance.StorageInstance;
import com.aestallon.storageexplorer.core.model.instance.dto.Availability;
import com.aestallon.storageexplorer.core.model.instance.dto.FsStorageLocation;
import com.aestallon.storageexplorer.core.model.instance.dto.IndexingStrategyType;
import com.aestallon.storageexplorer.core.model.instance.dto.SqlStorageLocation;
import com.aestallon.storageexplorer.core.model.instance.dto.StorageId;
import com.aestallon.storageexplorer.core.model.instance.dto.StorageInstanceDto;
import com.aestallon.storageexplorer.core.model.loading.IndexingTarget;
import com.aestallon.storageexplorer.core.model.type.EntityType;
import com.aestallon.storageexplorer.core.service.engine.EngineResult;
import com.aestallon.storageexplorer.core.service.engine.StorageEngine;
import com.aestallon.storageexplorer.core.service.fs.FsStorageEngine;
import com.aestallon.storageexplorer.core.service.index.impl.TreeBookmarkIndex;
import com.aestallon.storageexplorer.core.service.rdb.RdbStorageEngine;

@Service
public class StorageInstanceProvider {

  private static final Logger log = LoggerFactory.getLogger(StorageInstanceProvider.class);


  private static final class HighPriorityThreadFactory implements ThreadFactory {
    private static final AtomicInteger poolNumber = new AtomicInteger(1);
    private final ThreadGroup group;
    private final AtomicInteger threadNumber = new AtomicInteger(1);
    private final String namePrefix;

    private HighPriorityThreadFactory() {
      group = Thread.currentThread().getThreadGroup();
      namePrefix = "pool-" + poolNumber.getAndIncrement() + "-thread-";
    }

    @Override
    public Thread newThread(Runnable r) {
      Thread t = new Thread(group, r, namePrefix + threadNumber.getAndIncrement(), 0);
      if (t.isDaemon()) {
        t.setDaemon(false);
      }

      if (t.getPriority() != Thread.MAX_PRIORITY) {
        t.setPriority(Thread.MAX_PRIORITY);
      }
      return t;
    }
  }


  private final ApplicationEventPublisher eventPublisher;
  private final UserConfigService userConfigService;
  private final ProblemService problemService;
  private final Map<StorageId, StorageInstance> storageInstancesById;
  private final ExecutorService executorService;

  public StorageInstanceProvider(ApplicationEventPublisher eventPublisher,
                                 UserConfigService userConfigService,
                                 ProblemService problemService) {
    this.eventPublisher = eventPublisher;
    this.userConfigService = userConfigService;
    this.problemService = problemService;
    storageInstancesById = new HashMap<>();
    executorService = Executors.newSingleThreadExecutor(new HighPriorityThreadFactory());
  }

  public Stream<StorageInstance> provide() {
    return storageInstancesById.values().stream();
  }

  public StorageInstance get(final StorageId id) {
    return storageInstancesById.get(id);
  }

  public StorageInstance storageInstanceOf(final StorageEntry storageEntry) {
    return storageInstancesById.get(storageEntry.storageId());
  }

  public void importAndIndex(final StorageInstance storageInstance) {
    if (storageInstancesById.containsValue(storageInstance)) {
      return;
    }

    final String name = storageInstance.name();
    final UUID workId = UUID.randomUUID();
    eventPublisher.publishEvent(new BackgroundWorkStartedEvent(
        workId,
        "Importing storage: " + name + "..."));
    initialise(storageInstance);
    rebuildSurfaceBestEffort(storageInstance);

    userConfigService.addStorageLocation(storageInstance.toDto());
    eventPublisher.publishEvent(new StorageImportEvent(storageInstance));
    eventPublisher.publishEvent(BackgroundWorkCompletedEvent.ok(workId));
  }

  public boolean isKnownStorageInstance(StorageId storageId) {
    return userConfigService
        .storageLocationSettings()
        .getImportedStorageLocations()
        .stream()
        .map(StorageInstanceDto::getId)
        .anyMatch(storageId.uuid()::equals);
  }

  public void fetchAllKnown() {
    final var storageLocations = userConfigService
        .storageLocationSettings()
        .getImportedStorageLocations();
    if (storageLocations.isEmpty()) {
      return;
    }

    final UUID workId = UUID.randomUUID();
    eventPublisher.publishEvent(new BackgroundWorkStartedEvent(workId, "Importing storages"));
    for (final var dto : storageLocations) {
      final StorageInstance storageInstance = StorageInstance.fromDto(dto);
      initialise(storageInstance);
      eventPublisher.publishEvent(new StorageImportEvent(storageInstance));
    }
    eventPublisher.publishEvent(BackgroundWorkCompletedEvent.ok(workId));
  }

  /**
   * (Re)connects {@code storageInstance} to its configured {@link StorageEngine} and registers
   * it, regardless of outcome — a failed connection still leaves the instance present (with
   * {@code availability} set to {@code UNAVAILABLE}/{@code MISCONFIGURED}), fixing the old
   * "zombie instance" bug where a failed import silently vanished instead of showing up as a
   * broken storage (§10).
   */
  private void initialise(final StorageInstance storageInstance) {
    switch (storageInstance.location()) {
      case FsStorageLocation fs -> connectFs(storageInstance, fs);
      case SqlStorageLocation sql -> connectDb(storageInstance, sql);
    }
    storageInstancesById.put(storageInstance.id(), storageInstance);
  }

  private void connectFs(final StorageInstance storageInstance, final FsStorageLocation location) {
    switch (FsStorageEngine.open(location)) {
      case EngineResult.Ok<FsStorageEngine> ok -> {
        storageInstance.setEngine(ok.value().asStorageEngine());
        storageInstance.setAvailability(Availability.AVAILABLE);
        storageInstance.addTypeInfoListener(
            id -> eventPublisher.publishEvent(new TypeInfoUpdated(id)));
      }
      case EngineResult.Err<FsStorageEngine> err -> fail(storageInstance, err.error().toString());
    }
  }

  private void connectDb(final StorageInstance storageInstance, final SqlStorageLocation location) {
    switch (RdbStorageEngine.open(location)) {
      case EngineResult.Ok<RdbStorageEngine> ok -> {
        storageInstance.setEngine(ok.value());
        storageInstance.setAvailability(Availability.AVAILABLE);
        storageInstance.addTypeInfoListener(
            id -> eventPublisher.publishEvent(new TypeInfoUpdated(id)));
      }
      case EngineResult.Err<RdbStorageEngine> err -> fail(storageInstance, err.error().toString());
    }
  }

  private void fail(final StorageInstance storageInstance, final String reason) {
    storageInstance.setAvailability(storageInstance.misconfigured()
        ? Availability.MISCONFIGURED
        : Availability.UNAVAILABLE);
    eventPublisher.publishEvent(Msg.err(
        "Failed to initialize " + storageInstance.name(),
        "Storage instance is unavailable: " + reason));
    problemService.add(Problem.ofStorage(storageInstance.id(),
        "Failed to init Storage " + storageInstance.name() + ": " + reason));
    log.error("Failed to initialise Storage instance [ {} ]: {}", storageInstance.name(), reason);
  }

  /** The SURFACE-mode rebuild from §10 — best-effort: a failure here leaves the instance
   *  connected but with a stale/empty tree, never fails the surrounding operation. Skipped
   *  entirely for an unavailable instance or an {@code ON_DEMAND} indexing strategy. */
  private void rebuildSurfaceBestEffort(final StorageInstance storageInstance) {
    if (storageInstance.availability() != Availability.AVAILABLE) {
      return;
    }
    if (storageInstance.indexingStrategy() == IndexingStrategyType.ON_DEMAND) {
      return;
    }

    try {
      storageInstance.treeIndex().rebuildSurface(IndexingTarget.any());
    } catch (final Exception e) {
      log.warn("Best-effort surface rebuild failed for storage [ {} ]: {}",
          storageInstance.name(), e.getMessage());
      log.debug(e.getMessage(), e);
    }
  }

  public void reindex(final StorageInstance storageInstance) {
    executorService.submit(() -> {
      if (storageInstance.availability() != Availability.AVAILABLE) {
        eventPublisher.publishEvent(Msg.err(
            "Cannot reindex " + storageInstance.name() + "!",
            "The storage is not connected. Check the connection settings for this storage!"));
        log.warn("Cannot reindex storage [ {} ] as it is not connected!", storageInstance.name());
        return;
      }

      final UUID workId = UUID.randomUUID();
      eventPublisher.publishEvent(new BackgroundWorkStartedEvent(
          workId,
          "Reindexing storage " + storageInstance.name() + "..."));
      rebuildSurfaceBestEffort(storageInstance);
      eventPublisher.publishEvent(new StorageReindexed(storageInstance));
      eventPublisher.publishEvent(BackgroundWorkCompletedEvent.ok(workId));
      eventPublisher.publishEvent(Msg.info(storageInstance.name() + " reindexed!", null));
    });
  }

  @EventListener
  public void onStorageIndexDiscarded(StorageIndexDiscardedEvent e) {
    CompletableFuture.runAsync(() -> discard(e.storageInstance()));
  }

  public void discard(final StorageInstance storageInstance) {
    storageInstancesById.remove(storageInstance.id());
    // closes both the treeIndex and the engine itself (a connection pool, for RDB) - setEngine's
    // own reconfigure path handles the "swap, don't leak" case; this is the "gone for good" case:
    storageInstance.close();

    userConfigService.removeStorageLocation(storageInstance.toDto());
  }

  public void reimport(final StorageInstance storageInstance) {
    executorService.submit(() -> {
      final TreeBookmarkIndex treeIndex = storageInstance.treeIndex();
      if (treeIndex != null) {
        treeIndex.close();
      }

      final UUID workId = UUID.randomUUID();
      eventPublisher.publishEvent(new BackgroundWorkStartedEvent(
          workId,
          "Importing storage: " + storageInstance.name() + "..."));
      initialise(storageInstance);
      rebuildSurfaceBestEffort(storageInstance);
      eventPublisher.publishEvent(new StorageReindexed(storageInstance));
      eventPublisher.publishEvent(new StorageReimportedEvent(storageInstance));
      eventPublisher.publishEvent(BackgroundWorkCompletedEvent.ok(workId));
    });
  }

  public void loadTypeInformation() {
    final TypeInfoRepository typeInfoRepository = userConfigService.typeInfoRepository();
    for (final var storageInstance : storageInstancesById.values()) {
      final var id = storageInstance.id();
      final List<EntityType> types = typeInfoRepository.loadStructuredTypeInfo(id);
      storageInstance.addTypeInfo(types);
    }
  }

  public void saveTypeInformation(StorageId storageId) {
    final StorageInstance storageInstance = get(storageId);
    final Set<EntityType> types = storageInstance.getStructuredTypeInfo();
    final OpResult result = userConfigService
        .typeInfoRepository()
        .saveStructuredTypeInfo(storageId, types);
    final var message = switch (result) {
      case OpResult.Ok(var title, var msg) -> Msg.info(title, msg);
      case OpResult.Err err -> Msg.warn(err.title(), err.msg());
    };
    eventPublisher.publishEvent(message);
  }

}

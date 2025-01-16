/*
 * Copyright (C) 2024 it4all Hungary Kft.
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

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import com.aestallon.storageexplorer.common.event.bgwork.BackgroundWorkCompletedEvent;
import com.aestallon.storageexplorer.common.event.bgwork.BackgroundWorkStartedEvent;
import com.aestallon.storageexplorer.core.event.StorageImportEvent;
import com.aestallon.storageexplorer.core.event.StorageIndexDiscardedEvent;
import com.aestallon.storageexplorer.core.event.StorageReimportedEvent;
import com.aestallon.storageexplorer.core.event.StorageReindexed;
import com.aestallon.storageexplorer.core.model.entry.StorageEntry;
import com.aestallon.storageexplorer.core.model.instance.StorageInstance;
import com.aestallon.storageexplorer.core.model.instance.dto.StorageId;
import com.aestallon.storageexplorer.core.userconfig.service.UserConfigService;
import com.aestallon.storageexplorer.common.event.msg.Msg;
import jakarta.validation.constraints.NotNull;

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
    public Thread newThread(@NotNull Runnable r) {
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
  private final Map<StorageId, StorageInstance> storageInstancesById;
  private final Map<StorageInstance, ConfigurableApplicationContext> contextsByInstance;
  private final ExecutorService executorService;

  public StorageInstanceProvider(ApplicationEventPublisher eventPublisher,
                                 UserConfigService userConfigService) {
    this.eventPublisher = eventPublisher;
    this.userConfigService = userConfigService;
    storageInstancesById = new HashMap<>();
    contextsByInstance = new HashMap<>();
    executorService = Executors.newSingleThreadExecutor(new HighPriorityThreadFactory());
  }

  public Stream<StorageIndex> provide() {
    return storageInstancesById.values().stream().map(StorageInstance::index);
  }

  public StorageInstance get(final StorageId id) {
    return storageInstancesById.get(id);
  }

  public Stream<StorageEntry> searchForUri(final String queryString) {
    return storageInstancesById.values().stream()
        .map(StorageInstance::index)
        .flatMap(it -> it.searchForUri(queryString));
  }

  public StorageInstance storageInstanceOf(final StorageEntry storageEntry) {
    return storageInstancesById.get(storageEntry.storageId());
  }

  public void importAndIndex(final StorageInstance storageInstance) {
    if (storageInstancesById.containsValue(storageInstance)) {
      return;
    }

    final String name = storageInstance.name();
    eventPublisher.publishEvent(
        new BackgroundWorkStartedEvent("Importing storage: " + name + "..."));
    initialise(storageInstance);
    storageInstance.refreshIndex();

    userConfigService.addStorageLocation(storageInstance.toDto());
    eventPublisher.publishEvent(new StorageImportEvent(storageInstance));
    eventPublisher.publishEvent(BackgroundWorkCompletedEvent.ok());
  }

  public void fetchAllKnown() {
    final var storageLocations = userConfigService
        .storageLocationSettings()
        .getImportedStorageLocations();
    if (storageLocations.isEmpty()) {
      return;
    }

    eventPublisher.publishEvent(
        new BackgroundWorkStartedEvent("Importing storages"));
    for (final var dto : storageLocations) {
      final StorageInstance storageInstance = StorageInstance.fromDto(dto);
      initialise(storageInstance);
      eventPublisher.publishEvent(new StorageImportEvent(storageInstance));
    }
    eventPublisher.publishEvent(BackgroundWorkCompletedEvent.ok());
  }

  private void initialise(final StorageInstance storageInstance) {
    storageInstance.setEventPublisher(eventPublisher);

    final var factory = StorageIndexFactory.of(storageInstance.id());
    switch (factory.create(storageInstance.location())) {
      case StorageIndexFactory.StorageIndexCreationResult.Ok ok -> {
        final var index = ok.storageIndex();
        final var ctx = ok.springContext();

        storageInstance.setIndex(index);
        storageInstancesById.put(storageInstance.id(), storageInstance);
        contextsByInstance.put(storageInstance, ctx);
      }
      case StorageIndexFactory.StorageIndexCreationResult.Err err -> {
        eventPublisher.publishEvent(Msg.err(
            "Failed to initialize " + storageInstance.name(),
            "Storage instance is unavailable: " + err.errorMessage()));
        log.error("Failed to initialise Storage instance [ {} ]: {}",
            storageInstance.name(),
            err.errorMessage());
      }
    }
  }

  public void reindex(final StorageInstance storageInstance) {
    executorService.submit(() -> {
      final StorageIndex storageIndex = storageInstance.index();
      if (storageIndex == null) {
        eventPublisher.publishEvent(Msg.err(
            "Cannot reindex " + storageInstance.name() + "!",
            "The index is not available. Check the connection settings for this storage!"));
        log.warn("Cannot reindex storage at [ {} ] as it is unknown!", storageInstance);
        return;
      }

      eventPublisher.publishEvent(new BackgroundWorkStartedEvent(
          "Reindexing storage" + storageInstance.name() + "..."));
      storageInstance.refreshIndex();
      eventPublisher.publishEvent(new StorageReindexed(storageInstance));
      eventPublisher.publishEvent(BackgroundWorkCompletedEvent.ok());
      eventPublisher.publishEvent(Msg.info(storageInstance.name() + " reindexed!", null));
    });
  }

  @EventListener
  public void onStorageIndexDiscarded(StorageIndexDiscardedEvent e) {
    CompletableFuture.runAsync(() -> discardIndex(e.storageInstance()));
  }

  public void discardIndex(final StorageInstance storageInstance) {
    storageInstancesById.remove(storageInstance.id());
    final var idx = storageInstance.index();
    if (idx != null) {
      idx.clear();
    }

    final ConfigurableApplicationContext ctx = contextsByInstance.remove(storageInstance);
    if (ctx == null) {
      return;
    }
    tryCloseCtx(ctx, storageInstance);

    userConfigService.removeStorageLocation(storageInstance.toDto());
  }

  public void reimport(final StorageInstance storageInstance) {
    executorService.submit(() -> {
      storageInstance.index().clear();

      final ConfigurableApplicationContext ctx = contextsByInstance.remove(storageInstance);
      tryCloseCtx(ctx, storageInstance);

      eventPublisher.publishEvent(new BackgroundWorkStartedEvent(
          "Importing storage: " + storageInstance.name() + "..."));
      initialise(storageInstance);
      storageInstance.refreshIndex();
      eventPublisher.publishEvent(new StorageReindexed(storageInstance));
      eventPublisher.publishEvent(new StorageReimportedEvent(storageInstance));
      eventPublisher.publishEvent(BackgroundWorkCompletedEvent.ok());
    });
  }

  private void tryCloseCtx(final ConfigurableApplicationContext ctx,
                           final StorageInstance storageInstance) {
    try {
      ctx.close();
    } catch (Throwable t) {
      log.error("Cannot close application context [ {} ] belonging to storage at [ {} ]!!!",
          ctx, storageInstance);
      log.error(t.getMessage(), t);
    }
  }

}

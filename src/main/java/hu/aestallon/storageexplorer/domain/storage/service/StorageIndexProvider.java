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

package hu.aestallon.storageexplorer.domain.storage.service;

import java.net.URI;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.smartbit4all.api.collection.CollectionApi;
import org.smartbit4all.api.config.PlatformApiConfig;
import org.smartbit4all.core.object.ObjectApi;
import org.smartbit4all.core.object.ObjectDefinitionApi;
import org.smartbit4all.domain.data.storage.ObjectStorage;
import org.smartbit4all.storage.fs.StorageFS;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import hu.aestallon.storageexplorer.domain.storage.model.entry.StorageEntry;
import hu.aestallon.storageexplorer.domain.storage.model.instance.StorageInstance;
import hu.aestallon.storageexplorer.domain.storage.model.instance.dto.FsStorageLocation;
import hu.aestallon.storageexplorer.domain.storage.model.instance.dto.StorageId;
import hu.aestallon.storageexplorer.domain.userconfig.service.UserConfigService;
import hu.aestallon.storageexplorer.ui.controller.ViewController;
import hu.aestallon.storageexplorer.util.NotImplementedException;

@Service
public class StorageIndexProvider {

  private static final Logger log = LoggerFactory.getLogger(StorageIndexProvider.class);


  private static final class HighPriorityThreadFactory implements ThreadFactory {
    private static final AtomicInteger poolNumber = new AtomicInteger(1);
    private final ThreadGroup group;
    private final AtomicInteger threadNumber = new AtomicInteger(1);
    private final String namePrefix;

    private HighPriorityThreadFactory() {
      SecurityManager s = System.getSecurityManager();
      group = (s != null) ? s.getThreadGroup() : Thread.currentThread().getThreadGroup();
      namePrefix = "pool-" +
          poolNumber.getAndIncrement() +
          "-thread-";
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
  private final Map<StorageId, StorageInstance> storageInstancesById;
  private final Map<StorageInstance, ConfigurableApplicationContext> contextsByInstance;
  private final ExecutorService executorService;

  public StorageIndexProvider(ApplicationEventPublisher eventPublisher,
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

  public Stream<StorageEntry> searchForUri(final String queryString) {
    return storageInstancesById.values().stream()
        .map(StorageInstance::index)
        .flatMap(it -> it.searchForUri(queryString));
  }

  public StorageIndex indexOf(final URI uri) {
    // TODO: don't do this, store backreference!
    return storageInstancesById.values().stream()
        .map(StorageInstance::index)
        .filter(it -> it.get(uri).isPresent())
        .findFirst()
        .orElseThrow();
  }

  public StorageIndex indexOf(final StorageEntry entry) {
    // TODO: Return Optional!
    return indexOf(entry.uri());
  }

  public void importAndIndex(final StorageInstance storageInstance) {
    if (storageInstancesById.containsValue(storageInstance)) {
      return;
    }

    final String name = storageInstance.name();
    eventPublisher.publishEvent(
        new ViewController.BackgroundWorkStartedEvent("Importing storage: " + name + "..."));
    final StorageIndex storageIndex = initialise(storageInstance);
    storageIndex.refresh();

    userConfigService.addStorageLocation(storageInstance.toDto());
    eventPublisher.publishEvent(new ViewController.StorageImportEvent(storageInstance));
    eventPublisher.publishEvent(ViewController.BackgroundWorkCompletedEvent.ok());
  }

  public void fetchAllKnown() {
    final var storageLocations = userConfigService
        .storageLocationSettings()
        .getImportedStorageLocations();
    if (storageLocations.isEmpty()) {
      return;
    }

    eventPublisher.publishEvent(
        new ViewController.BackgroundWorkStartedEvent("Importing storages"));
    for (final var dto : storageLocations) {
      final StorageInstance storageInstance = StorageInstance.fromDto(dto);
      initialise(storageInstance);
      eventPublisher.publishEvent(new ViewController.StorageImportEvent(storageInstance));
    }
    eventPublisher.publishEvent(ViewController.BackgroundWorkCompletedEvent.ok());
  }

  private StorageIndex initialise(final StorageInstance storageInstance) {
    final var factory = StorageIndexFactory.of(storageInstance.id());
    final var result = factory.create(storageInstance.location());
    if (result instanceof StorageIndexFactory.StorageIndexCreationResult.Ok) {
      final var ok = (StorageIndexFactory.StorageIndexCreationResult.Ok) result;
      final var index = ok.storageIndex();
      final var ctx = ok.springContext();

      storageInstance.setIndex(index);
      storageInstancesById.put(storageInstance.id(), storageInstance);
      contextsByInstance.put(storageInstance, ctx);
      
      return index;

    } else {
      final var err = (StorageIndexFactory.StorageIndexCreationResult.Err) result;
      throw new IllegalStateException(err.errorMessage());
    }
  }

  public void reindex(final StorageInstance storageInstance) {
    executorService.submit(() -> {
      final StorageIndex storageIndex = storageInstance.index();
      if (storageIndex == null) {
        log.warn("Cannot reindex storage at [ {} ] as it is unknown!", storageInstance);
        return;
      }

      eventPublisher.publishEvent(
          new ViewController.BackgroundWorkStartedEvent(
              "Reindexing storage" + storageInstance.name() + "..."));
      storageIndex.refresh();
      eventPublisher.publishEvent(new ViewController.StorageReindexed(storageInstance));
      eventPublisher.publishEvent(ViewController.BackgroundWorkCompletedEvent.ok());
    });
  }

  @EventListener
  public void onStorageIndexDiscarded(ViewController.StorageIndexDiscardedEvent e) {
    CompletableFuture.runAsync(() -> discardIndex(e.storageInstance));
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

    try {
      ctx.close();
    } catch (Throwable t) {
      log.error("Cannot close application context [ {} ] belonging to storage at [ {} ]!!!",
          ctx, storageInstance);
      log.error(t.getMessage(), t);
    }
    userConfigService.removeStorageLocation(storageInstance.toDto());
  }

}

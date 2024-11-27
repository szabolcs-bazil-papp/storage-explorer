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
import hu.aestallon.storageexplorer.domain.storage.model.StorageEntry;
import hu.aestallon.storageexplorer.domain.userconfig.service.UserConfigService;
import hu.aestallon.storageexplorer.ui.controller.ViewController;

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
  private final Map<Path, StorageIndex> indicesByPath;
  private final Map<Path, ConfigurableApplicationContext> contextsByPath;
  private final ExecutorService executorService;

  public StorageIndexProvider(ApplicationEventPublisher eventPublisher,
      UserConfigService userConfigService) {
    this.eventPublisher = eventPublisher;
    this.userConfigService = userConfigService;
    indicesByPath = new HashMap<>();
    contextsByPath = new HashMap<>();
    executorService = Executors.newSingleThreadExecutor(new HighPriorityThreadFactory());
  }

  public Stream<StorageIndex> provide() {
    return indicesByPath.values().stream();
  }

  public Stream<StorageEntry> searchForUri(final String queryString) {
    return indicesByPath.values().stream().flatMap(it -> it.searchForUri(queryString));
  }

  public StorageIndex indexOf(final URI uri) {
    // TODO: don't do this, store backreference!
    return indicesByPath.values().stream()
        .filter(it -> it.get(uri).isPresent())
        .findFirst()
        .orElseThrow();
  }

  public StorageIndex indexOf(final StorageEntry entry) {
    // TODO: Return Optional!
    return indexOf(entry.uri());
  }

  public void importAndIndex(final Path path) {
    final Path absolute = path.toAbsolutePath();
    if (indicesByPath.containsKey(absolute)) {
      return;
    }

    final String name = storageIndexName(absolute);
    eventPublisher.publishEvent(
        new ViewController.BackgroundWorkStartedEvent("Importing storage: " + name + "..."));
    final StorageIndex storageIndex = initialise(name, absolute);
    storageIndex.refresh();

    userConfigService.addStorageLocation(absolute);
    eventPublisher.publishEvent(new ViewController.StorageImportEvent(storageIndex));
    eventPublisher.publishEvent(ViewController.BackgroundWorkCompletedEvent.ok());
  }

  private String storageIndexName(final Path path) {
    final String fsDirName = path.getFileName().toString();
    final String fsDirParentName = path.getParent().getFileName().toString();
    return String.format("%s (%s)", fsDirName, fsDirParentName);
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
    for (final var path : storageLocations) {
      final Path absolute = path.toAbsolutePath();
      final String name = storageIndexName(absolute);
      final StorageIndex storageIndex = initialise(name, absolute);
      eventPublisher.publishEvent(new ViewController.StorageImportEvent(storageIndex));
    }
    eventPublisher.publishEvent(ViewController.BackgroundWorkCompletedEvent.ok());
  }

  private StorageIndex initialise(final String name, final Path path) {
    final var ctx = new AnnotationConfigApplicationContext();
    ctx.register(PlatformApiConfig.class);
    ctx.registerBean(name, ObjectStorage.class, () -> new StorageFS(
        path.toFile(),
        ctx.getBean(ObjectDefinitionApi.class)));
    ctx.refresh();

    final ObjectApi objectApi = ctx.getBean(ObjectApi.class);
    final CollectionApi collectionApi = ctx.getBean(CollectionApi.class);

    final StorageIndex storageIndex = new StorageIndex(name, path, objectApi, collectionApi);
    indicesByPath.put(path, storageIndex);
    contextsByPath.put(path, ctx);
    return storageIndex;
  }

  public void reindex(final Path path) {
    executorService.submit(() -> {
      final Path absolute = path.toAbsolutePath();
      final StorageIndex storageIndex = indicesByPath.get(absolute);
      if (storageIndex == null) {
        log.warn("Cannot reindex storage at [ {} ] as it is unknown!", absolute);
        return;
      }

      eventPublisher.publishEvent(
          new ViewController.BackgroundWorkStartedEvent(
              "Reindexing storage" + storageIndex.name() + "..."));
      storageIndex.refresh();
      eventPublisher.publishEvent(new ViewController.StorageReindexed(storageIndex));
      eventPublisher.publishEvent(ViewController.BackgroundWorkCompletedEvent.ok());
    });
  }

  @EventListener
  public void onStorageIndexDiscarded(ViewController.StorageIndexDiscardedEvent e) {
    CompletableFuture.runAsync(() -> discardIndex(e.pathToStorage));
  }

  public void discardIndex(final Path path) {
    final StorageIndex idx = indicesByPath.remove(path);
    if (idx != null) {
      idx.stopFileSystemWatcher();
    }

    final ConfigurableApplicationContext ctx = contextsByPath.remove(path);
    if (ctx == null) {
      return;
    }

    try {
      ctx.close();
    } catch (Throwable t) {
      log.error("Cannot close application context [ {} ] belonging to storage at [ {} ]!!!",
          ctx, path);
      log.error(t.getMessage(), t);
    }
    userConfigService.removeStorageLocation(path);
  }

}

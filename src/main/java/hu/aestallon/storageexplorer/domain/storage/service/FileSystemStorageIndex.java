package hu.aestallon.storageexplorer.domain.storage.service;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.smartbit4all.api.collection.CollectionApi;
import org.smartbit4all.core.object.ObjectApi;
import hu.aestallon.storageexplorer.domain.storage.model.entry.ObjectEntry;
import hu.aestallon.storageexplorer.domain.storage.model.entry.ScopedEntry;
import hu.aestallon.storageexplorer.domain.storage.model.entry.StorageEntry;
import hu.aestallon.storageexplorer.domain.storage.model.instance.dto.StorageId;
import hu.aestallon.storageexplorer.util.IO;
import static java.util.stream.Collectors.groupingBy;

public final class FileSystemStorageIndex extends StorageIndex {

  private static final Logger log = LoggerFactory.getLogger(FileSystemStorageIndex.class);

  private final Path pathToStorage;

  FileSystemStorageIndex(
      StorageId storageId,
      ObjectApi objectApi,
      CollectionApi collectionApi,
      Path pathToStorage) {
    super(storageId, objectApi, collectionApi);
    this.pathToStorage = pathToStorage;
  }

  @Override
  void refresh() {
    clear();
    try {
      log.info("Starting to index {}", pathToStorage);
      final Map<URI, StorageEntry> map = new ConcurrentHashMap<>();
      Files.walkFileTree(pathToStorage, EnumSet.noneOf(FileVisitOption.class), 8,
          new SimpleFileVisitor<>() {
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
                StorageEntry
                    .create(storageId, file, uri, objectApi, collectionApi)
                    .ifPresent(it -> map.put(uri, it));
              }
              return FileVisitResult.SKIP_SUBTREE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
              return FileVisitResult.SKIP_SUBTREE;
            }
          });
      log.info("Finished reading files of {}", pathToStorage);

      Map<String, List<ScopedEntry>> scopedEntries = map.values().stream()
          .filter(ScopedEntry.class::isInstance)
          .map(ScopedEntry.class::cast)
          .collect(groupingBy(it -> it.scope().getPath()));

      map.values().stream()
          .filter(ObjectEntry.class::isInstance)
          .map(ObjectEntry.class::cast)
          .forEach(it -> {
            final var scopedChildren = scopedEntries.get(it.uri().getPath());
            if (scopedChildren == null) {
              return;
            }

            scopedChildren.forEach(it::addScopedEntry);
          });
      cache.putAll(map);
      // startFileSystemWatcher();
    } catch (IOException e) {
      log.error(e.getMessage(), e);
    }
  }


  // -----------------------------------------------------------------------------------------------
  // File system watching stuff. Copied here for later removal. All StorageIndices shall start and
  // stop watching for changes in the future by index.watcher().start(); and index.watcher().stop();

  private StorageWatchService watchService;

  void startFileSystemWatcher() {
    if (watchService != null) {
      return;
    }

    StorageWatchService.builder(pathToStorage)
        .onModified(it -> {
          final URI uri = IO.pathToUri(pathToStorage.relativize(it));
          final StorageEntry storageEntry = cache.get(uri);
          if (storageEntry != null) {
            storageEntry.refresh();
          }
        })
        .onCreated(it -> {
          final URI uri = IO.pathToUri(pathToStorage.relativize(it));
          StorageEntry
              .create(storageId, it, uri, objectApi, collectionApi)
              .ifPresent(e -> cache.put(uri, e));
        })
        .build()
        .ifPresent(it -> {
          watchService = it;
          watchService.start();
        });
  }

  void stopFileSystemWatcher() {
    if (watchService == null) {
      return;
    }

    watchService.stop();
    watchService = null;
  }

}

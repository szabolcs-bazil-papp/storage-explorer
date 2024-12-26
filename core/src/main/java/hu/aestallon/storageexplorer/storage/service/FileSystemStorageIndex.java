package hu.aestallon.storageexplorer.storage.service;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.EnumSet;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.smartbit4all.api.collection.CollectionApi;
import org.smartbit4all.core.object.ObjectApi;
import hu.aestallon.storageexplorer.storage.model.entry.StorageEntry;
import hu.aestallon.storageexplorer.storage.model.entry.StorageEntryFactory;
import hu.aestallon.storageexplorer.storage.model.instance.dto.StorageId;
import hu.aestallon.storageexplorer.common.util.IO;

public final class FileSystemStorageIndex extends StorageIndex {

  private static final Logger log = LoggerFactory.getLogger(FileSystemStorageIndex.class);


  private static final class StorageFileVisitor extends SimpleFileVisitor<Path> {

    private final Path pathToStorage;
    private final BlockingQueue<URI> queue;

    private StorageFileVisitor(Path pathToStorage, BlockingQueue<URI> queue) {
      this.pathToStorage = pathToStorage;
      this.queue = queue;
    }

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
        queue.offer(uri);
      } else {
        log.warn("Encountered NULL when converting Path to URI: {}", relativePath);
      }

      return FileVisitResult.SKIP_SUBTREE;
    }

    @Override
    public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
      return FileVisitResult.SKIP_SUBTREE;
    }
  }


  private final Path pathToStorage;

  public FileSystemStorageIndex(
      StorageId storageId,
      ObjectApi objectApi,
      CollectionApi collectionApi,
      Path pathToStorage) {
    super(storageId, objectApi, collectionApi);
    this.pathToStorage = pathToStorage;
  }

  @Override
  protected Stream<URI> fetchEntries() {
    final BlockingQueue<URI> queue = new LinkedBlockingQueue<>();
    final StorageFileVisitor visitor = new StorageFileVisitor(pathToStorage, queue);
    try {
      Files.walkFileTree(pathToStorage, EnumSet.noneOf(FileVisitOption.class), 20, visitor);
    } catch (IOException e) {
      log.error(e.getMessage(), e);
    }

    return queue.stream();
  }

  @Override
  protected StorageEntryFactory storageEntryFactory() {
    return StorageEntryFactory.builder(storageId, objectApi, collectionApi)
        .pathToStorage(pathToStorage)
        .build();
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
          storageEntryFactory.create(uri).ifPresent(e -> cache.put(uri, e));
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

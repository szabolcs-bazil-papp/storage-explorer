package hu.aestallon.storageexplorer.domain.storage.service;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;

public final class StorageWatchService {

  private static final Logger log = LoggerFactory.getLogger(StorageWatchService.class);


  @FunctionalInterface
  public interface PathAction extends Consumer<Path> {
  }


  static final class StorageWatchServiceBuilder {

    private final Path pathToStorage;

    private PathAction onCreated = it -> {
    };
    private PathAction onModified = it -> {
    };
    private PathAction onDeleted = it -> {
    };

    private StorageWatchServiceBuilder(final Path pathToStorage) {
      this.pathToStorage = pathToStorage;
    }

    StorageWatchServiceBuilder onCreated(final PathAction pathAction) {
      this.onCreated = pathAction;
      return this;
    }

    StorageWatchServiceBuilder onModified(final PathAction pathAction) {
      this.onModified = pathAction;
      return this;
    }

    StorageWatchServiceBuilder onDeleted(final PathAction pathAction) {
      this.onDeleted = pathAction;
      return this;
    }

    Optional<StorageWatchService> build() {
      try {
        return Optional.of(new StorageWatchService(this));
      } catch (IOException e) {
        log.error(e.getMessage(), e);
        return Optional.empty();
      }
    }
  }

  static StorageWatchServiceBuilder builder(final Path pathToStorage) {
    return new StorageWatchServiceBuilder(pathToStorage);
  }

  private final Path pathToStorage;
  private final WatchService watchService;
  private final ExecutorService executor;
  private final PathAction onCreated;
  private final PathAction onModified;
  private final PathAction onDeleted;
  private final Map<WatchKey, Path> watchKeys = new HashMap<>();

  private boolean running;

  private StorageWatchService(StorageWatchServiceBuilder builder) throws IOException {
    pathToStorage = builder.pathToStorage;
    onCreated = builder.onCreated;
    onModified = builder.onModified;
    onDeleted = builder.onDeleted;

    watchService = FileSystems.getDefault().newWatchService();
    executor = Executors.newSingleThreadExecutor();
  }

  private void registerAll(final Path root) {
    try {
      Files.walkFileTree(root, new SimpleFileVisitor<>() {
        @Override
        public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
          watchKeys.put(
              dir.register(watchService, ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE),
              dir);
          return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
          return FileVisitResult.SKIP_SUBTREE;
        }
      });

    } catch (IOException e) {
      log.error(e.getMessage(), e);
    }
    log.info("Watched folders: {}", watchKeys.size());
  }

  void start() {
    if (running) {
      return;
    }

    registerAll(pathToStorage);

    running = true;
    executor.submit(() -> {
      try {
        while (running) {
          final WatchKey key = watchService.take();
          final Path dir = watchKeys.get(key);
          if (dir == null) {
            log.warn("Unknown folder: {}", key);
            continue;
          }

          handleEvents(key, dir);

          final boolean valid = key.reset();
          if (!valid) {
            watchKeys.remove(key);
            if (watchKeys.isEmpty()) {
              log.warn("All directories unavailable under: {}", pathToStorage);
              break;
            }
          }

        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        running = false;
      }
    });
  }

  private void handleEvents(WatchKey key, Path dir) {
    for (final WatchEvent<?> event : key.pollEvents()) {
      handleEvent(dir, event);
    }
  }

  private void handleEvent(Path dir, WatchEvent<?> event) {
    final WatchEvent.Kind<?> kind = event.kind();
    if (kind == OVERFLOW) {
      log.warn("Overflow: {}", event);
      return;
    }

    final Path name = (Path) event.context();
    final Path p = dir.resolve(name);
    if (kind == ENTRY_CREATE && Files.isDirectory(p)) {
      registerAll(p);
    }

    if (!Files.isRegularFile(p) || !p.getFileName().toString().endsWith(".o")) {
      return;
    }

    if (log.isDebugEnabled()) {
      final String pLowerCase = p.toString().toLowerCase();
      if (!pLowerCase.contains("applicationruntimedata") && !pLowerCase.contains("viewcontext")) {
        log.debug("{} -> {}", kind, p);
      }
    }
    if (kind == ENTRY_CREATE) {
      onCreated.accept(p);
    } else if (kind == ENTRY_MODIFY) {
      onModified.accept(p);
    } else if (kind == ENTRY_DELETE) {
      onDeleted.accept(p);
    }
  }

  void stop() {
    log.debug("Stopping watch service: {}", pathToStorage);
    running = false;
    executor.shutdownNow();
    try {
      watchService.close();
    } catch (Exception e) {
      log.error(e.getMessage(), e);
    }
  }
}

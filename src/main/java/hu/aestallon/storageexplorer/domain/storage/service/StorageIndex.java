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

import hu.aestallon.storageexplorer.domain.storage.model.instance.dto.StorageId;
import static java.util.stream.Collectors.groupingBy;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.smartbit4all.api.collection.CollectionApi;
import org.smartbit4all.core.object.ObjectApi;
import com.google.common.base.Strings;
import hu.aestallon.storageexplorer.domain.storage.model.entry.ObjectEntry;
import hu.aestallon.storageexplorer.domain.storage.model.entry.ScopedEntry;
import hu.aestallon.storageexplorer.domain.storage.model.entry.StorageEntry;
import hu.aestallon.storageexplorer.util.IO;

public class StorageIndex {

  private static final Logger log = LoggerFactory.getLogger(StorageIndex.class);

  private final StorageId storageId;
  private final Path pathToStorage;
  private final ObjectApi objectApi;
  private final CollectionApi collectionApi;
  private final Map<URI, StorageEntry> cache;

  private StorageWatchService watchService;

  StorageIndex(StorageId storageId, 
               Path pathToStorage, 
               ObjectApi objectApi,
               CollectionApi collectionApi) {
    this.storageId = storageId;
    this.pathToStorage = pathToStorage;
    this.objectApi = objectApi;
    this.collectionApi = collectionApi;
    this.cache = new HashMap<>();
  }

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

  void clear() {
    cache.clear();
  }

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

  public Stream<StorageEntry> entities() {
    return cache.values().stream();
  }

  public Path pathToStorage() {
    return pathToStorage;
  }

  public Optional<StorageEntry> get(final URI uri) {
    return Optional.ofNullable(cache.get(uri));
  }

  public Stream<StorageEntry> searchForUri(final String queryString) {
    if (Strings.isNullOrEmpty(queryString)) {
      return Stream.empty();
    }
    final var p = constructPattern(queryString);
    return cache.values().stream().filter(it -> p.matcher(it.uri().toString()).find());
  }

  private static Pattern constructPattern(final String queryString) {
    final String q = queryString.replaceAll("\\.\\+\\*\\-\\(\\)\\[\\]", "");
    return Arrays.stream(splitAtForwardSlash(q))
        .map(StorageIndex::examineSubsection)
        .collect(Collectors.collectingAndThen(Collectors.joining(), Pattern::compile));
  }

  private static String[] splitAtForwardSlash(final String q) {
    final String[] arr = q.split("/");
    final String[] temp = new String[arr.length];
    int ptr = 0;
    for (final String s : arr) {
      if (Strings.isNullOrEmpty(s)) {
        continue;
      }
      temp[ptr] = ptr == 0 ? s : "\\/" + s;
      ptr++;
    }
    final String[] ret = new String[ptr];
    System.arraycopy(temp, 0, ret, 0, ret.length);
    return ret;
  }

  private static String examineSubsection(final String s) {
    final StringBuilder sb = new StringBuilder();
    for (char c : s.toCharArray()) {
      if (sb.length() != 0 && Character.isUpperCase(c)) {
        sb.append(".*");
      }
      sb.append(c);
    }
    sb.append(".*");
    return sb.toString();
  }

}

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

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.smartbit4all.api.collection.CollectionApi;
import org.smartbit4all.core.object.ObjectApi;
import com.google.common.base.Strings;
import hu.aestallon.storageexplorer.domain.storage.model.ObjectEntry;
import hu.aestallon.storageexplorer.domain.storage.model.ScopedEntry;
import hu.aestallon.storageexplorer.domain.storage.model.StorageEntry;
import hu.aestallon.storageexplorer.util.IO;
import hu.aestallon.storageexplorer.util.Pair;

public class StorageIndex {

  private static final Logger log = LoggerFactory.getLogger(StorageIndex.class);

  private final String name;
  private final Path pathToStorage;
  private final ObjectApi objectApi;
  private final CollectionApi collectionApi;
  private final Map<URI, StorageEntry> cache;

  public StorageIndex(String name, Path pathToStorage, ObjectApi objectApi,
                      CollectionApi collectionApi) {
    this.name = name;
    this.pathToStorage = pathToStorage;
    this.objectApi = objectApi;
    this.collectionApi = collectionApi;
    this.cache = new HashMap<>();
  }

  void refresh() {
    clear();
    try (final var files = Files.walk(pathToStorage)) {
      final var map = files
          .filter(p -> p.toFile().isFile())
          .filter(p -> p.getFileName().toString().endsWith(".o"))
          .map(IO::read)
          .flatMap(IO.findObjectUri().andThen(Optional::stream))
          .map(uri -> StorageEntry.create(uri, objectApi, collectionApi))
          .flatMap(Optional::stream)
          .map(it -> Pair.of(it.uri(), it))
          .collect(Pair.toMap());
      map.values().stream()
          .filter(ScopedEntry.class::isInstance)
          .map(ScopedEntry.class::cast)
          .forEach(it -> Optional.ofNullable(map.get(it.scope())).ifPresent(e -> {
            if (e instanceof ObjectEntry) {
              ((ObjectEntry) e).addScopedEntry(it);
            } else {
              log.warn("Scoped entry [ {} ] belongs to non-object [ {} ] ???", it, e);
            }
          }));
      cache.putAll(map);
    } catch (IOException e) {
      log.error(e.getMessage(), e);
    }
  }

  void clear() {
    cache.clear();
  }

  public Stream<StorageEntry> entities() {
    return cache.values().stream();
  }

  public String name() {
    return name;
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

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;
    StorageIndex that = (StorageIndex) o;
    return Objects.equals(name, that.name);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name);
  }

}

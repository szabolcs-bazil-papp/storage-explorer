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

package hu.aestallon.storageexplorer.service.internal;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.smartbit4all.api.collection.CollectionApi;
import org.smartbit4all.core.object.ObjectApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import hu.aestallon.storageexplorer.util.IO;
import hu.aestallon.storageexplorer.util.Pair;
import hu.aestallon.storageexplorer.util.Uris;

@Service
public class StorageIndex {

  private static final Logger log = LoggerFactory.getLogger(StorageIndex.class);

  private final String fsBaseDirectory;
  private final ObjectApi objectApi;
  private final CollectionApi collectionApi;
  private final Map<StorageEntry, Set<UriProperty>> cache;

  public StorageIndex(@Value("${fs.base.directory:./fs}") String fsBaseDirectory,
                      ObjectApi objectApi, CollectionApi collectionApi) {
    this.fsBaseDirectory = fsBaseDirectory;
    this.objectApi = objectApi;
    this.collectionApi = collectionApi;
    this.cache = new HashMap<>();

    init();
  }

  private void init() {
    try (final var files = Files.walk(Path.of(fsBaseDirectory))) {
      files
          .filter(p -> p.toFile().isFile())
          .filter(p -> p.getFileName().toString().endsWith(".o"))
          .map(IO::read)
          .flatMap(IO.findObjectUri().andThen(Optional::stream))
          .map(uri -> StorageEntry.create(uri, objectApi, collectionApi))
          .flatMap(Optional::stream)
          .map(it -> Pair.of(it, load(it)))
          .flatMap(Pair.streamOnB())
          .forEach(Pair.putIntoMap(cache));
    } catch (IOException e) {
      log.error(e.getMessage(), e);
    }
  }

  private Optional<Set<UriProperty>> load(StorageEntry se) {
    try {
      return Optional.of(se.uriProperties());
    } catch (Throwable t) {
      log.error(t.getMessage(), t);
      return Optional.empty();
    }
  }

  public Stream<StorageEntry> entities() {
    return cache.keySet().stream();
  }

  public Stream<Pair<StorageEntry, Set<UriProperty>>> refs() {
    return cache.entrySet().stream().map(Pair::of);
  }

  public Path fsBaseDirectory() {
    return Path.of(fsBaseDirectory);
  }

  public Optional<StorageEntry> get(URI uri) {
    return cache.keySet().stream()
        .filter(it -> Uris.equalIgnoringVersion(it.uri(), uri))
        .findAny();
  }

}

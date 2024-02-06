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
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import hu.aestallon.storageexplorer.domain.storage.model.StorageEntry;

@Service
public class StorageIndexProvider {

  private final Map<String, StorageIndex> indicesByName;

  public StorageIndexProvider() {
    indicesByName = new HashMap<>();
  }

  public StorageIndex provide(final String name) {
    return indicesByName.get(name);
  }

  public Stream<StorageIndex> provide() {
    return indicesByName.values().stream();
  }

  public Stream<StorageEntry> searchForUri(final String queryString) {
    // TODO: Move here and implement!
    return Stream.empty();
  }

  public StorageIndex indexOf(final URI uri) {
    // TODO: don't do this, store backreference!
    return indicesByName.values().stream()
        .filter(it -> it.get(uri).isPresent())
        .findFirst()
        .orElseThrow();
  }

  public StorageIndex indexOf(final StorageEntry entry) {
    // TODO: Return Optional!
    return indexOf(entry.uri());
  }

  public StorageIndex init(Path path) {
    // TODO: Implement!
    return null;
  }



}

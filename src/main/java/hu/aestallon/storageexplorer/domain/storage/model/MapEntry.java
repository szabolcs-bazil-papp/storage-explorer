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

package hu.aestallon.storageexplorer.domain.storage.model;

import java.net.URI;
import java.nio.file.Path;
import java.util.Set;
import org.smartbit4all.api.collection.CollectionApi;
import hu.aestallon.storageexplorer.util.Uris;
import static java.util.stream.Collectors.toSet;

public class MapEntry implements StorageEntry {

  private final Path path;
  private final URI uri;
  private final CollectionApi collectionApi;
  private final String schema;
  private final String name;

  private boolean valid = false;
  private Set<UriProperty> uriProperties;

  MapEntry(Path path, URI uri, CollectionApi collectionApi) {
    this.path = path;
    this.uri = uri;
    this.collectionApi = collectionApi;

    final String fullScheme = uri.getScheme();
    this.schema = fullScheme.substring(0, fullScheme.lastIndexOf('-'));

    final String pathElements[] = uri.getPath().split("/");
    final String terminalElement = pathElements[pathElements.length - 1];
    this.name = terminalElement.substring(0, terminalElement.lastIndexOf('-'));
  }

  @Override
  public Path path() {
    return path;
  }

  @Override
  public URI uri() {
    return uri;
  }

  public String schema() {
    return schema;
  }

  public String name() {
    return name;
  }

  @Override
  public Set<UriProperty> uriProperties() {
    if (!valid) {
      refresh();
      valid = true;
    }
    
    return uriProperties;
  }

  @Override
  public void refresh() {
    this.uriProperties = collectionApi.map(schema, name).uris().entrySet().stream()
        .map(e -> UriProperty.standalone(e.getKey(), e.getValue()))
        .collect(toSet());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;
    MapEntry mapEntry = (MapEntry) o;
    return Uris.equalIgnoringVersion(uri, mapEntry.uri);
  }

  @Override
  public int hashCode() {
    return uri.hashCode();
  }

  @Override
  public String toString() {
    return "MAP (" + schema + "/" + name + ")";
  }
}

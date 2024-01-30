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

import java.net.URI;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import org.smartbit4all.api.collection.CollectionApi;
import org.smartbit4all.api.collection.StoredList;
import hu.aestallon.storageexplorer.util.Uris;

public class ListEntry implements StorageEntry {

  private final URI uri;
  private final CollectionApi collectionApi;
  private final String schema;
  private final String name;

  public ListEntry(URI uri, CollectionApi collectionApi) {
    this.uri = uri;
    this.collectionApi = collectionApi;

    final String fullScheme = uri.getScheme();
    this.schema = fullScheme.substring(0, fullScheme.lastIndexOf('-'));

    final String pathElements[] = uri.getPath().split("/");
    final String terminalElement = pathElements[pathElements.length - 1];
    this.name = terminalElement.substring(0, terminalElement.lastIndexOf('-'));
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
    final var list = collectionApi.list(schema, name).uris();
    final var ret = new HashSet<UriProperty>();
    for (int i = 0; i < list.size(); i++) {
      ret.add(UriProperty.listElement(String.valueOf(i), list.get(i), i));
    }
    return ret;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;
    ListEntry listEntry = (ListEntry) o;
    return Uris.equalIgnoringVersion(uri, listEntry.uri);
  }

  @Override
  public int hashCode() {
    return uri.hashCode();
  }
}

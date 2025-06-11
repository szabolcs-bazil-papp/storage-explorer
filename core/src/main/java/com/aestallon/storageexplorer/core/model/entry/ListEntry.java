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

package com.aestallon.storageexplorer.core.model.entry;

import java.net.URI;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.smartbit4all.api.collection.CollectionApi;
import com.aestallon.storageexplorer.common.util.Uris;
import com.aestallon.storageexplorer.core.model.instance.dto.StorageId;

public sealed class ListEntry implements StorageEntry permits ScopedListEntry {

  private final StorageId id;
  private final Path path;
  private final URI uri;
  private final CollectionApi collectionApi;
  private final String schema;
  private final String name;

  private final Lock refreshLock = new ReentrantLock(true);
  private boolean valid = false;
  private Set<UriProperty> uriProperties;

  ListEntry(StorageId id, Path path, URI uri, CollectionApi collectionApi) {
    this.id = id;
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
  public StorageId storageId() {
    return id;
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
    }

    return uriProperties;
  }

  @Override
  public void refresh() {
    if (valid) {
      return;
    }

    refreshLock.lock();
    try {
      if (valid) {
        return;
      }

      final var list = collectionApi.list(schema, name).uris();
      final var uriProperties = new HashSet<UriProperty>();
      for (int i = 0; i < list.size(); i++) {
        uriProperties.add(
            UriProperty.of(new UriProperty.Segment[] { UriProperty.Segment.idx(i) },
                list.get(i)));
      }

      this.uriProperties = uriProperties;
      valid = true;
    } finally {
      refreshLock.unlock();
    }


  }

  @Override
  public boolean valid() {
    return valid;
  }

  @Override
  public void setUriProperties(Set<UriProperty> uriProperties) {
    this.uriProperties = uriProperties;
    this.valid = true;
  }
  
  @Override
  public void accept(StorageEntry storageEntry) {
    refreshLock.lock();
    try {

      if (Objects.requireNonNull(storageEntry) instanceof ListEntry that && that.valid) {
        this.uriProperties = new HashSet<>(that.uriProperties());
        valid = true;
      }

    } finally {
      refreshLock.unlock();
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ListEntry listEntry = (ListEntry) o;
    return Uris.equalIgnoringVersion(uri, listEntry.uri);
  }

  public String displayName() {
    return schema + " / " + name;
  }

  @Override
  public int hashCode() {
    return uri.hashCode();
  }

  @Override
  public String toString() {
    return "LIST (" + schema + "/" + name + ")";
  }

}

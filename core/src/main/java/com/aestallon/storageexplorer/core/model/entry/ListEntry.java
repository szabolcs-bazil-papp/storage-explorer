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

import java.lang.ref.WeakReference;
import java.net.URI;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.smartbit4all.api.collection.CollectionApi;
import org.smartbit4all.api.collection.StoredListStorageImpl;
import org.smartbit4all.core.object.ObjectApi;
import com.aestallon.storageexplorer.core.model.instance.dto.StorageId;
import com.aestallon.storageexplorer.core.model.loading.ObjectEntryLoadResult;
import com.aestallon.storageexplorer.core.service.StorageIndex;
import com.aestallon.storageexplorer.core.util.Uris;

public sealed class ListEntry implements StorageEntry permits ScopedListEntry {

  private static final Logger log = LoggerFactory.getLogger(ListEntry.class);
  private final WeakReference<StorageIndex<?>> storageIndex;
  private final StorageId id;
  private final Path path;
  private final URI uri;
  private final ObjectApi objectApi;
  protected final CollectionApi collectionApi;
  private final String schema;
  private final String name;

  private final Lock refreshLock = new ReentrantLock(true);
  private boolean valid = false;
  private Set<UriProperty> uriProperties;

  ListEntry(final StorageIndex<?> storageIndex, StorageId id, Path path, URI uri,
            ObjectApi objectApi,
            CollectionApi collectionApi) {
    this.storageIndex = new WeakReference<>(storageIndex);
    this.id = id;
    this.path = path;
    this.uri = uri;
    this.objectApi = objectApi;
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

  @Override
  public Path path() {
    return path;
  }

  public String schema() {
    return schema;
  }

  public String name() {
    return name;
  }

  public ObjectEntryLoadResult.SingleVersion asSingleVersion() {
    final var list = impl();
    try {
      return storageIndex.get().loader().loadExact(list.getUri(), 0);
    } catch (Exception e) {
      log.error(e.getMessage(), e);
      return null;
    }
  }
  
  protected StoredListStorageImpl impl() {
    return (StoredListStorageImpl) collectionApi.list(schema, name);
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

      final var list = asUriList(asSingleVersion());
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

  public List<URI> asUriList(ObjectEntryLoadResult.SingleVersion singleVersion) {
    final Object urisObj = singleVersion.objectAsMap().get("uris");
    if (urisObj instanceof List<?> uriObjList) {
      return uriObjList.stream()
          .map(Uris::parse)
          .flatMap(Optional::stream)
          .toList();
    }

    return Collections.emptyList();
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

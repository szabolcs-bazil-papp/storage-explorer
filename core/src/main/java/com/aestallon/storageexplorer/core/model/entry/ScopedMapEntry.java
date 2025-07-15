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
import org.smartbit4all.api.collection.CollectionApi;
import org.smartbit4all.api.collection.StoredMapStorageImpl;
import org.smartbit4all.core.object.ObjectApi;
import com.aestallon.storageexplorer.common.util.Uris;
import com.aestallon.storageexplorer.core.model.instance.dto.StorageId;
import com.aestallon.storageexplorer.core.service.StorageIndex;

public final class ScopedMapEntry extends MapEntry implements ScopedEntry, StorageEntry {
  private final URI scopeUri;

  ScopedMapEntry(final StorageIndex<?> storageIndex, StorageId id, Path path, URI uri, ObjectApi objectApi, CollectionApi collectionApi,
                 URI scopeUri) {
    super(storageIndex, id, path, uri, objectApi, collectionApi);
    this.scopeUri = scopeUri;
  }

  @Override
  protected StoredMapStorageImpl impl() {
    return (StoredMapStorageImpl) collectionApi.map(scopeUri, schema(), name());
  }

  @Override
  public URI scope() {
    return scopeUri;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) { return true; }
    if (o == null || getClass() != o.getClass()) { return false; }
    if (!super.equals(o)) { return false; }
    ScopedMapEntry that = (ScopedMapEntry) o;
    return Uris.equalIgnoringVersion(uri(), that.uri());
  }

  @Override
  public int hashCode() {
    return uri().hashCode();
  }

}

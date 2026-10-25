/*
 * Copyright (C) 2025 Szabolcs Bazil Papp
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
import java.util.Objects;
import com.aestallon.storageexplorer.core.model.instance.dto.StorageId;
import com.aestallon.storageexplorer.core.service.StorageIndex;

abstract sealed class AbstractStorageEntry
    implements StorageEntry
    permits ListEntry, MapEntry, ObjectEntry, SequenceEntry {

  protected final WeakReference<StorageIndex<?>> storageIndex;
  protected final StorageId id;
  protected final Path path;
  protected final URI uri;

  protected AbstractStorageEntry(final StorageIndex<?> storageIndex,
                                 final Path path,
                                 final URI uri) {
    this.storageIndex = new WeakReference<>(storageIndex);
    this.id = storageIndex.id();
    this.path = path;
    this.uri = uri;
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass())
      return false;
    AbstractStorageEntry that = (AbstractStorageEntry) o;
    return Objects.equals(id, that.id) && Objects.equals(uri, that.uri);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, uri);
  }

}

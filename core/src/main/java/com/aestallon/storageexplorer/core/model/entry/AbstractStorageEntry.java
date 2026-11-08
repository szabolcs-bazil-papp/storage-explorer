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

import java.util.Objects;
import com.aestallon.storageexplorer.core.model.EntryUri;
import com.aestallon.storageexplorer.core.model.instance.dto.StorageId;

/**
 * The shared identity base for every {@link StorageEntry}: a {@link StorageId} and an
 * {@link EntryUri}, nothing else — no held index reference, no filesystem path (per T4.1; see
 * {@link StorageEntry}'s own javadoc). Package-private: nothing outside {@code
 * core.model.entry} constructs or extends these directly — {@link StorageEntryFactory} is the
 * sole construction choke point (confirmed in the pre-T4.1 audit).
 */
abstract sealed class AbstractStorageEntry
    implements StorageEntry
    permits ListEntry, MapEntry, ObjectEntry, ObjectStreamEntry, SequenceEntry, TableDataEntry {

  protected final StorageId id;
  protected final EntryUri uri;

  protected AbstractStorageEntry(final StorageId id, final EntryUri uri) {
    this.id = Objects.requireNonNull(id, "id cannot be null!");
    this.uri = Objects.requireNonNull(uri, "uri cannot be null!");
  }

  @Override
  public StorageId storageId() {
    return id;
  }

  @Override
  public EntryUri uri() {
    return uri;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    // strict same-class check, not instanceof: two different leaf types (e.g. a plain
    // ObjectEntry and a ScopedObjectEntry) must never compare equal even if they somehow shared
    // an address - preserved from the pre-T4.1 equals, unchanged in spirit.
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final AbstractStorageEntry that = (AbstractStorageEntry) o;
    return Objects.equals(id, that.id) && Objects.equals(uri, that.uri);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, uri);
  }
}

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

import org.smartbit4all.core.utility.StringConstant;
import com.aestallon.storageexplorer.core.model.EntryUri;
import com.aestallon.storageexplorer.core.model.instance.dto.StorageId;

/**
 * A pure immutable value type: an entry's identity (its {@link StorageId} + {@link EntryUri}) and
 * what kind of thing it is — nothing else. Per the implementation plan's T4.1 (and the decisions
 * log's §3 decision 4): no mutable fields, no {@code refresh()}/{@code accept()}/{@code
 * notifyRefresh()} lifecycle, no cached {@code uriProperties}/{@code scopedEntries}, no
 * {@code path()} (that had no meaning for a SQL-backed entry anyway — see {@code
 * StorageInstance.pathOf(StorageEntry)} instead). Content, metadata, and outgoing-reference
 * knowledge are the responsibility of whichever {@code StorageIndex} implementation is asked —
 * never the entry itself. Equality is {@code (StorageId, EntryUri)}-based only, deliberately not
 * content-based: a "duplicate" entry for the same address is, by design, interchangeable with the
 * original, which is exactly why {@code AbstractStorageEntry} is free to implement {@code equals}/
 * {@code hashCode} once for the whole hierarchy instead of each leaf reimplementing it.
 */
public sealed interface StorageEntry
    permits AbstractStorageEntry, GodObjectEntry, ListEntry, MapEntry, ObjectEntry,
    ObjectReferenceByIdEntry, ObjectStreamEntry, ScopedListEntry,
    ScopedMapEntry,
    ScopedObjectEntry, SequenceEntry, TableDataEntry {

  static String typeNameOf(StorageEntry storageEntry) {
    return switch (storageEntry) {
      case null -> StringConstant.NULL.toUpperCase();
      case ListEntry l -> "List";
      case MapEntry m -> "Map";
      case SequenceEntry s -> "Sequence";
      case ObjectStreamEntry s -> "Object Stream";
      case TableDataEntry t -> "Table Data";
      case GodObjectEntry g -> "God Object (" + g.typeName() + ")";
      case ObjectReferenceByIdEntry r -> "Reference";
      case ObjectEntry o -> o.typeName();
    };
  }

  StorageId storageId();

  EntryUri uri();

}

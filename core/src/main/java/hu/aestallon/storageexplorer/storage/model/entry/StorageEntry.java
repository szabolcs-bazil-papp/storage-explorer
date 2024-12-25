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

package hu.aestallon.storageexplorer.storage.model.entry;

import java.net.URI;
import java.util.Set;
import org.smartbit4all.core.utility.StringConstant;
import hu.aestallon.storageexplorer.storage.model.instance.dto.StorageId;
import hu.aestallon.storageexplorer.common.util.Uris;

public sealed interface StorageEntry permits
    ListEntry, MapEntry, ObjectEntry,
    SequenceEntry, ScopedListEntry, ScopedMapEntry,
    ScopedObjectEntry {

  static String typeNameOf(StorageEntry storageEntry) {
    if (storageEntry == null) {
      return StringConstant.NULL.toUpperCase();
    }

    if (storageEntry instanceof ListEntry) {
      return "List";
    }

    if (storageEntry instanceof MapEntry) {
      return "Map";
    }

    if (storageEntry instanceof ObjectEntry) {
      return ((ObjectEntry) storageEntry).typeName();
    }

    return "Unknown type";
  }

  StorageId storageId();

  URI uri();

  Set<UriProperty> uriProperties();

  void refresh();

  boolean valid();

  default boolean references(StorageEntry that) {
    return uriProperties().stream()
        .map(it -> it.uri)
        .anyMatch(Uris.equalsIgnoringVersion(that.uri()));
  }

}

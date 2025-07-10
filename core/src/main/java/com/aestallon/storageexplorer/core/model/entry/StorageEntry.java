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
import java.util.Collections;
import java.util.Set;
import org.smartbit4all.core.utility.StringConstant;
import com.aestallon.storageexplorer.common.util.Uris;
import com.aestallon.storageexplorer.core.model.instance.dto.StorageId;

public sealed interface StorageEntry permits
    ListEntry, MapEntry, ObjectEntry,
    SequenceEntry, ScopedListEntry, ScopedMapEntry,
    ScopedObjectEntry {

  static String typeNameOf(StorageEntry storageEntry) {
    return switch (storageEntry) {
      case null -> StringConstant.NULL.toUpperCase();
      case ListEntry l -> "List";
      case MapEntry m -> "Map";
      case SequenceEntry s -> "Sequence";
      case ObjectEntry o -> o.typeName();
    };
  }

  StorageId storageId();

  URI uri();
  
  Path path();

  Set<UriProperty> uriProperties();

  void refresh();

  boolean valid();
  
  void accept(StorageEntry storageEntry);

  default boolean references(StorageEntry that) {
    return uriProperties().stream()
        .map(it -> it.uri)
        .anyMatch(Uris.equalsIgnoringVersion(that.uri()));
  }
  
  void setUriProperties(Set<UriProperty> uriProperties);
  
  default Set<UriProperty> uriPropertiesStrict() {
    return valid() ? uriProperties() : Collections.emptySet();
  }

}

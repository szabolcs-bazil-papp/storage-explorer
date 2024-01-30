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
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.smartbit4all.api.collection.CollectionApi;
import org.smartbit4all.core.object.ObjectApi;

public interface StorageEntry {

  Logger log = LoggerFactory.getLogger(StorageEntry.class);

  static Optional<StorageEntry> create(URI uri, ObjectApi objectApi, CollectionApi collectionApi) {
    final String uriString = uri.toString();
    if (uriString.contains("-collections:/storedlist")) {
      return Optional.of(new ListEntry(uri, collectionApi));
    } else if (uriString.contains("-collections:/storedmap")) {
      return Optional.of(new MapEntry(uri, collectionApi));
    } else if (!uriString.contains("storedRef") && !uriString.contains("storedSeq")) {
      return Optional.of(new ObjectEntry(uri, objectApi));
    }

    log.warn("Cannot yet deal with URI TYPE of [ {} ]", uri);
    return Optional.empty();
  }

  URI uri();

  Set<UriProperty> uriProperties();

}

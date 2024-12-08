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

package hu.aestallon.storageexplorer.domain.storage.model.entry;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.smartbit4all.api.collection.CollectionApi;
import org.smartbit4all.core.object.ObjectApi;
import org.smartbit4all.core.utility.StringConstant;
import com.google.common.base.Strings;
import hu.aestallon.storageexplorer.domain.storage.model.instance.dto.StorageId;
import hu.aestallon.storageexplorer.util.Uris;

public interface StorageEntry {

  Logger log = LoggerFactory.getLogger(StorageEntry.class);
  String STORED_LIST_MARKER = "/storedlist";
  String STORED_MAP_MARKER = "/storedmap";
  String STORED_REF_MARKER = "/storedRef";
  String STORED_SEQ_MARKER = "/storedSeq";

  static Optional<? extends StorageEntry> create(StorageId id, URI uri, 
                                                 ObjectApi objectApi,
                                                 CollectionApi collectionApi) {
    final URI latestUri = objectApi.getLatestUri(uri);
    final Path path = Paths.get(latestUri.getScheme(), latestUri.getPath());
    final String uriString = latestUri.toString();
    try {
      if (uriString.contains(STORED_LIST_MARKER)) {
        return scopeUri(uriString, STORED_LIST_MARKER)
            .map(scope -> (ListEntry) new ScopedListEntry(id, path, latestUri, collectionApi, scope))
            .or(() -> Optional.of(new ListEntry(id, path, latestUri, collectionApi)));

      } else if (uriString.contains(STORED_MAP_MARKER)) {
        return scopeUri(uriString, STORED_MAP_MARKER)
            .map(scope -> (MapEntry) new ScopedMapEntry(id, path, latestUri, collectionApi, scope))
            .or(() -> Optional.of(new MapEntry(id, path, latestUri, collectionApi)));

      } else if (uriString.contains(STORED_REF_MARKER)) {
        return scopeUri(uriString, STORED_REF_MARKER)
            .map(scope -> new ScopedObjectEntry(id, path, latestUri, objectApi, scope));

      } else if(uriString.contains(STORED_SEQ_MARKER)) {
        return Optional.of(new SequenceEntry(id, path, latestUri, collectionApi));
        
      } else {
        return Optional.of(new ObjectEntry(id, path, latestUri, objectApi));
        
      }

    } catch (Exception e) {
      log.error("Cannot initialise StorageEntry [ {} ]: [ {} ]", latestUri, e.getMessage());
      log.debug(e.getMessage(), e);
    }

    log.warn("Cannot yet deal with URI TYPE of [ {} ]", latestUri);
    return Optional.empty();
  }

  private static Optional<URI> scopeUri(final String uriString, final String probe) {
    if (Strings.isNullOrEmpty(uriString)) {
      return Optional.empty();
    }
    return scopeEndsAt(uriString, probe).stream()
        .mapToObj(i -> uriString.substring(0, i))
        .findFirst()
        .flatMap(Uris::parse);
  }

  private static OptionalInt scopeEndsAt(final String uriString, final String probe) {
    final int idx = uriString.indexOf(probe);
    return (idx > 0) ? OptionalInt.of(idx) : OptionalInt.empty();
  }

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

  default boolean references(StorageEntry that) {
    return uriProperties().stream()
        .map(it -> it.uri)
        .anyMatch(Uris.equalsIgnoringVersion(that.uri()));
  }

}

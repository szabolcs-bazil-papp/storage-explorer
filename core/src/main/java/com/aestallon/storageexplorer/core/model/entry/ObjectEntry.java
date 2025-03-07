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
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.smartbit4all.core.object.ObjectNode;
import com.aestallon.storageexplorer.core.util.ObjectMaps;
import com.aestallon.storageexplorer.common.util.Pair;
import com.aestallon.storageexplorer.common.util.Uris;
import com.aestallon.storageexplorer.core.model.instance.dto.StorageId;
import com.aestallon.storageexplorer.core.model.loading.ObjectEntryLoadRequest;
import com.aestallon.storageexplorer.core.model.loading.ObjectEntryLoadResult;
import com.aestallon.storageexplorer.core.service.StorageIndex;
import static java.util.stream.Collectors.toSet;

public sealed class ObjectEntry implements StorageEntry permits ScopedObjectEntry {

  public sealed interface Versioning {

    record Single() implements Versioning {}


    record Multi(long head) implements Versioning {}

  }


  private static final Logger log = LoggerFactory.getLogger(ObjectEntry.class);

  private final WeakReference<StorageIndex> storageIndex;
  private final StorageId id;
  private final Path path;
  private final URI uri;
  private final String typeName;
  private final String uuid;
  private final Set<ScopedEntry> scopedEntries = new HashSet<>();

  private /*volatile*/ boolean valid = false;
  private Versioning versioning;
  private Set<UriProperty> uriProperties;

  ObjectEntry(final StorageIndex storageIndex,
              final Path path,
              final URI uri) {
    this.storageIndex = new WeakReference<>(storageIndex);
    this.id = storageIndex.id();
    this.path = path;
    this.uri = uri;
    this.typeName = Uris.getTypeName(uri);
    this.uuid = Uris.getUuid(uri);
  }

  public Set<ScopedEntry> scopedEntries() {
    return scopedEntries;
  }

  public void addScopedEntry(final ScopedEntry scopedEntry) {
    scopedEntries.add(scopedEntry);
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
  public Set<UriProperty> uriProperties() {
    if (!valid) {
      refresh();
    }

    final var uriProperties = new HashSet<>(this.uriProperties);
    scopedEntries.stream()
        .map(e -> UriProperty.of(new UriProperty.Segment[] { UriProperty.Segment.key(
                (e instanceof ObjectEntry o) ? o.uuid : e.uri().toString()) },
            e.uri()))
        .forEach(uriProperties::add);
    return uriProperties;
  }

  @Override
  public boolean references(StorageEntry that) {
    return StorageEntry.super.references(that)
           || ((that instanceof ScopedEntry) && scopedEntries.contains(that));
  }

  @Override
  public void refresh() {
    log.warn("!!!!!!!!!! INDIVIDUAL REFRESH ON NODE !!!!!!!!!!: {}", uri);
    Objects.requireNonNull(storageIndex.get()).loader().load(this, true);
  }

  public void refresh(final ObjectNode objectNode) {
    if (objectNode == null) {
      uriProperties = new HashSet<>();
      return;
    }

    // synchronized (this) {
      uriProperties = initUriProperties(objectNode);
      valid = true;
    // }
  }

  private Set<UriProperty> initUriProperties(final ObjectNode objectNode) {
    return ObjectMaps.flatten(objectNode.getObjectAsMap())
        .filter(it -> !UriProperty.Segment.isOwnUri(it.a()))
        .map(Pair.onB(Uris::parse))
        .flatMap(Pair.streamOnB())
        .map(it -> UriProperty.of(it.a(), it.b()))
        .collect(toSet());
  }

  public String getDisplayName(final ObjectEntryLoadResult.SingleVersion version) {
    final var oam = version.objectAsMap();
    final var heuristicName = getHeuristicName(oam);
    return heuristicName.isEmpty() ? typeName : typeName + " (" + heuristicName + ")";
  }

  private static String getHeuristicName(Map<String, Object> oam) {
    final var name = oam.get("name");
    if (name != null) {
      return String.valueOf(name);
    }

    return switch (oam.get("data")) {
      case Map<?, ?> m -> switch (m.get("name")) {
        case String s -> s;
        case null, default -> "";
      };
      case null, default -> "";
    };
  }

  @Override
  public void accept(StorageEntry storageEntry) {
    if (Objects.requireNonNull(storageEntry) instanceof ObjectEntry that && that.valid) {
      uriProperties = that.uriProperties;
      valid = true;
    }
  }

  public String uuid() {
    return uuid;
  }

  public String typeName() {
    return typeName;
  }

  public @Nullable Path path() {
    return path;
  }


  public ObjectEntryLoadRequest tryLoad() {
    return Objects.requireNonNull(storageIndex.get()).loader().load(this);
  }
  
  public ObjectEntryLoadRequest tryLoadHead() {
    return Objects.requireNonNull(storageIndex.get()).loader().load(this, true);
  }

  @Override
  public boolean valid() {
    return valid;
  }


  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;
    ObjectEntry that = (ObjectEntry) o;
    return Uris.equalIgnoringVersion(uri, that.uri);
  }

  @Override
  public int hashCode() {
    return uri.hashCode();
  }

  @Override
  public String toString() {
    return typeName;
  }

}

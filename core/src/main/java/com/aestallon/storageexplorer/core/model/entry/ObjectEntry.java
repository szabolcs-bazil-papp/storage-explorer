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
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import static java.util.stream.Collectors.toSet;
import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.smartbit4all.core.object.ObjectNode;
import com.aestallon.storageexplorer.common.util.Pair;
import com.aestallon.storageexplorer.core.model.instance.dto.StorageId;
import com.aestallon.storageexplorer.core.model.loading.ObjectEntryLoadRequest;
import com.aestallon.storageexplorer.core.model.loading.ObjectEntryLoadResult;
import com.aestallon.storageexplorer.core.service.StorageIndex;
import com.aestallon.storageexplorer.core.util.ObjectMaps;
import com.aestallon.storageexplorer.core.util.Uris;

public sealed class ObjectEntry implements StorageEntry permits ScopedObjectEntry {

  public sealed interface Versioning {

    record Single() implements Versioning {}


    record Multi(long head) implements Versioning {}

  }


  private static final Logger log = LoggerFactory.getLogger(ObjectEntry.class);

  private final WeakReference<StorageIndex<?>> storageIndex;
  private final StorageId id;
  private final Path path;
  private final URI uri;
  private final String typeName;
  private final String uuid;
  private final Set<ScopedEntry> scopedEntries = new HashSet<>();

  private final Lock refreshLock = new ReentrantLock(true);
  private boolean valid = false;
  private Versioning versioning;
  private Set<UriProperty> uriProperties;

  ObjectEntry(final StorageIndex<?> storageIndex,
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

    if (uriProperties == null) {
      log.warn("!!!!!!!!!! NULL URI PROPERTIES FOR {} !!!!!!!!!!", uri);
      return Collections.emptySet();
    }
    final var ret = new HashSet<>(this.uriProperties);
    ret.addAll(scopedEntriesAsUriProperties());
    return ret;
  }

  public Set<UriProperty> scopedEntriesAsUriProperties() {
    return scopedEntries.stream()
        .map(e -> UriProperty.of(new UriProperty.Segment[] { UriProperty.Segment.key(
                (e instanceof ObjectEntry o) ? o.uuid : e.uri().toString()) },
            e.uri()))
        .collect(toSet());
  }

  @Override
  public boolean references(StorageEntry that) {
    return StorageEntry.super.references(that)
           || ((that instanceof ScopedEntry se) && scopedEntries.contains(se));
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

      // TODO: maybe explicitly get() here? The whole entrance to the critical section is awful...
      Objects.requireNonNull(storageIndex.get()).loader().load(this);
    } finally {
      refreshLock.unlock();
    }
  }

  public void refresh(final ObjectNode objectNode) {
    if (objectNode == null) {
      refresh(null, 0L);
    } else {
      final Long versionNr = objectNode.getVersionNr();
      final long version = versionNr == null ? -1L : versionNr;
      refresh(objectNode.getObjectAsMap(), version);
    }
  }

  public void refresh(final Map<String, Object> objectAsMap, final long version) {
    if (valid) {
      return;
    }

    if (objectAsMap == null) {
      uriProperties = new HashSet<>();
      return;
    }

    uriProperties = initUriProperties(objectAsMap);
    valid = true;
    versioning = version < 0 ? new Versioning.Single() : new Versioning.Multi(version);
    storageIndex.get().notifyRefresh(this);
  }

  private Set<UriProperty> initUriProperties(final Map<String, Object> objectAsMap) {
    return ObjectMaps.flatten(objectAsMap)
        .filter(it -> !UriProperty.Segment.isOwnUri(it.a()))
        .map(Pair.onB(Uris::parse))
        .flatMap(Pair.streamOnB())
        .map(it -> UriProperty.of(it.a(), it.b()))
        .collect(toSet());
  }

  public String getDisplayName(final ObjectEntryLoadResult.SingleVersion version) {
    final var oam = version.objectAsMap();
    final var heuristicName = getHeuristicName(oam);
    final var sb = new StringBuilder(typeName);
    if (!heuristicName.isEmpty()) {
      sb.append(" (").append(heuristicName).append(")");
    }
    
    final var entryId = version.meta().entryId();
    if (entryId != null && !entryId.isEmpty()) {
      sb.append(" (ID: ").append(entryId).append(")");
    }
    
    return sb.toString();
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
    refreshLock.lock();
    try {

      if (Objects.requireNonNull(storageEntry) instanceof ObjectEntry that && that.valid) {
        uriProperties = that.uriProperties;
        valid = true;
      }

    } finally {
      refreshLock.unlock();
    }
  }

  public String uuid() {
    return uuid;
  }

  public String typeName() {
    return typeName;
  }

  public Versioning versioning() {
    if (versioning == null) {
      return uri.toString().endsWith("-s")
          ? new Versioning.Single()
          : new Versioning.Multi(0L);
    }

    return versioning;
  }

  @Override
  public @Nullable Path path() {
    return path;
  }


  public ObjectEntryLoadRequest tryLoad() {
    return Objects.requireNonNull(storageIndex.get()).loader().load(this);
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
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
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

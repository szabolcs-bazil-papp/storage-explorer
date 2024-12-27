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

package hu.aestallon.storageexplorer.core.model.entry;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.smartbit4all.core.object.ObjectApi;
import org.smartbit4all.core.object.ObjectNode;
import org.smartbit4all.core.utility.StringConstant;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import hu.aestallon.storageexplorer.common.util.IO;
import hu.aestallon.storageexplorer.common.util.ObjectMaps;
import hu.aestallon.storageexplorer.common.util.Pair;
import hu.aestallon.storageexplorer.common.util.Uris;
import hu.aestallon.storageexplorer.core.model.instance.dto.StorageId;
import hu.aestallon.storageexplorer.core.model.loading.ObjectEntryLoadResult;
import hu.aestallon.storageexplorer.core.model.loading.ObjectEntryLoadResults;
import static java.util.stream.Collectors.toSet;

public sealed class ObjectEntry implements StorageEntry permits ScopedObjectEntry {

  private static final Logger log = LoggerFactory.getLogger(ObjectEntry.class);
  
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private final StorageId id;
  private final Path path;
  private final URI uri;
  private final ObjectApi objectApi;
  private final String typeName;
  private final String uuid;
  private final Set<ScopedEntry> scopedEntries = new HashSet<>();

  private boolean valid = false;
  private Set<UriProperty> uriProperties;
  private String displayName;

  ObjectEntry(StorageId id, final Path path, final URI uri, final ObjectApi objectApi) {
    this.id = id;
    this.path = path;
    this.uri = uri;
    this.objectApi = objectApi;
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
        .map(e -> UriProperty.standalone(
            (e instanceof ObjectEntry) ? ((ObjectEntry) e).uuid : e.uri().toString(),
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
    log.info("Individual refresh on node: {}", uri);
    final var objectNode = load();
    refresh(objectNode);
  }

  public void refresh(final ObjectNode objectNode) {
    if (objectNode == null) {
      uriProperties = new HashSet<>();
      displayName = "ERROR LOADING";
      return;
    }

    uriProperties = initUriProperties(objectNode);
    displayName = initDisplayName(objectNode);
    valid = true;
  }

  private Set<UriProperty> initUriProperties(final ObjectNode objectNode) {
    return ObjectMaps.flatten(objectNode.getObjectAsMap())
        .entrySet().stream()
        .filter(it -> !UriProperty.OWN.equals(it.getKey()))
        .map(Pair::of)
        .map(Pair.onB(Uris::parse))
        .flatMap(Pair.streamOnB())
        .map(it -> UriProperty.parse(it.a(), it.b()))
        .collect(toSet());
  }

  private String initDisplayName(final ObjectNode objectNode) {
    final Object name = objectNode.getValue("name");
    if (name != null) {
      return String.valueOf(name);
    }

    Object dataName = null;
    try {
      dataName = objectNode.getValue("data", "name");
    } catch (final Exception e) {
      log.debug(e.getMessage(), e);
    }

    if (dataName != null) {
      return String.valueOf(dataName);
    }

    return StringConstant.EMPTY;
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

  public ObjectNode load() {
    if (Uris.isSingleVersion(uri) && path != null) {
      // We have to do this...
      return tryDeserialise().map(it -> objectApi.create(null, it)).orElse(null);
    }
    try {
      return objectApi.loadLatest(uri);
    } catch (final Exception e) {
      log.error(e.getMessage(), e);
      return null;
    }
  }

  private Optional<Map<?, ?>> tryDeserialise() {
    final String rawContent = IO.read(path);
    if (Strings.isNullOrEmpty(rawContent)) {
      log.error("Empty content found during deserialisation attempt of [ {} ]", uri);
      return Optional.empty();
    }

    try {
      final Map<?, ?> res = objectApi
          .getDefaultSerializer()
          .fromString(rawContent, LinkedHashMap.class);
      return Optional.of(res);
    } catch (IOException e) {
      log.error("Error during deserialisation attempt of [ {} ]", uri);
      log.error(e.getMessage(), e);
      return Optional.empty();
    }
  }

  public ObjectEntryLoadResult tryLoad() {
    try {
      if (Uris.isSingleVersion(uri)) {
        final var node = load();
        return (node != null)
            ? ObjectEntryLoadResults.singleVersion(node, OBJECT_MAPPER)
            : ObjectEntryLoadResults.err("Failed to retrieve single version object entry!");
      } else {
        final var historyIterator = objectApi.objectHistory(uri);
        return ObjectEntryLoadResults.multiVersion(historyIterator, OBJECT_MAPPER);
      }
    } catch (Throwable t) {
      final String msg = String.format("Could not load Object Entry [ %s ] : %s",
          uri,
          t.getMessage());
      log.error(msg);
      log.error(t.getMessage(), t);

      return ObjectEntryLoadResults.err(msg);
    }
  }

  public ObjectNode load(final long version) {
    if (Uris.isSingleVersion(uri)) {
      return load();
    }

    return objectApi.load(Uris.atVersion(uri, version));
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
    if (!valid) {
      refresh();
    }

    return (displayName.isEmpty())
        ? typeName
        : typeName + " (" + displayName + ")";
  }

}

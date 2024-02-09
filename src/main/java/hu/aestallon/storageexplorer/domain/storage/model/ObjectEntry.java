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

package hu.aestallon.storageexplorer.domain.storage.model;

import java.net.URI;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.smartbit4all.core.object.ObjectApi;
import org.smartbit4all.core.object.ObjectNode;
import org.smartbit4all.core.utility.StringConstant;
import com.google.common.base.Strings;
import hu.aestallon.storageexplorer.util.ObjectMaps;
import hu.aestallon.storageexplorer.util.Pair;
import hu.aestallon.storageexplorer.util.Uris;
import static java.util.stream.Collectors.toSet;

public class ObjectEntry implements StorageEntry {

  private static final Logger log = LoggerFactory.getLogger(ObjectEntry.class);

  private final URI uri;
  private final ObjectApi objectApi;
  private final String typeName;
  private final String uuid;
  private Set<UriProperty> uriProperties;
  private String displayName;

  private Set<ScopedEntry> scopedEntries = new HashSet<>();

  ObjectEntry(URI uri, ObjectApi objectApi) {
    this.uri = uri;
    this.objectApi = objectApi;
    this.typeName = Uris.getTypeName(uri);
    this.uuid = Uris.getUuid(uri);

    refresh();
  }

  public Set<ScopedEntry> scopedEntries() {
    return scopedEntries;
  }

  public void scopedEntries(final Set<ScopedEntry> scopedEntries) {
    this.scopedEntries = (scopedEntries == null) ? new HashSet<>() : new HashSet<>(scopedEntries);
  }

  public void addScopedEntry(final ScopedEntry scopedEntry) {
    scopedEntries.add(scopedEntry);
  }

  @Override
  public URI uri() {
    return uri;
  }

  @Override
  public Set<UriProperty> uriProperties() {
    final var uriProperties = new HashSet<UriProperty>();
    uriProperties.addAll(this.uriProperties);
    scopedEntries.stream()
        .map(e -> UriProperty.standalone(e.toString(), e.uri()))
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
    final var objectNode = objectApi.load(uri);
    uriProperties = initUriProperties(objectNode);
    displayName = initDisplayName(objectNode);
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

    final Object dataName = objectNode.getValue("data", "name");
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

  public ObjectNode load() {
    // TODO: Add VersionInfo to Entry -> what to load, what can be loaded!
    return objectApi.loadLatest(uri);
  }

  public ObjectEntryLoadResult tryLoad() {
    try {
      final ObjectNode objectNode = load();
      if (objectNode == null) {
        return ObjectEntryLoadResult.err(null);
      }
      return ObjectEntryLoadResult.ok(objectNode);
    } catch (Throwable t) {
      final String msg = String.format("Could not load Object Entry [ %s ] : %s",
          uri,
          t.getMessage());
      log.error(msg);
      log.error(t.getMessage(), t);

      return ObjectEntryLoadResult.err(msg);
    }
  }

  public ObjectNode load(final long version) {
    return objectApi.load(Uris.atVersion(uri, version));
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
    return (displayName.isEmpty())
        ? typeName
        : typeName + " (" + displayName + ")";
  }

  public static final class ObjectEntryLoadResult {

    static ObjectEntryLoadResult ok(final ObjectNode objectNode) {
      return new ObjectEntryLoadResult(objectNode, StringConstant.EMPTY);
    }

    static ObjectEntryLoadResult err(final String errorMessage) {
      return Strings.isNullOrEmpty(errorMessage)
          ? new ObjectEntryLoadResult(null, "Loading node failed for unknown reason.")
          : new ObjectEntryLoadResult(null, errorMessage);
    }

    private final ObjectNode objectNode;
    private final String errorMessage;

    private ObjectEntryLoadResult(ObjectNode objectNode, String errorMessage) {
      this.objectNode = objectNode;
      this.errorMessage = errorMessage;
    }

    public ObjectNode objectNode() {
      return objectNode;
    }

    public String errorMessage() {
      return errorMessage;
    }

    public boolean isOk() {
      return objectNode != null;
    }

    public boolean isErr() {
      return !isOk();
    }

    @Override
    public boolean equals(Object o) {
      if (this == o)
        return true;
      if (o == null || getClass() != o.getClass())
        return false;
      ObjectEntryLoadResult that = (ObjectEntryLoadResult) o;
      return Objects.equals(objectNode, that.objectNode) && Objects.equals(
          errorMessage, that.errorMessage);
    }

    @Override
    public int hashCode() {
      return Objects.hash(objectNode, errorMessage);
    }

    @Override
    public String toString() {
      return "ObjectEntryLoadResult{" +
          "objectNode=" + objectNode +
          ", errorMessage='" + errorMessage + '\'' +
          '}';
    }

  }

}

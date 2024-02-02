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
import java.util.Set;
import org.smartbit4all.core.object.ObjectApi;
import org.smartbit4all.core.object.ObjectNode;
import org.smartbit4all.core.utility.StringConstant;
import hu.aestallon.storageexplorer.util.ObjectMaps;
import hu.aestallon.storageexplorer.util.Pair;
import hu.aestallon.storageexplorer.util.Uris;
import static java.util.stream.Collectors.toSet;

public class ObjectEntry implements StorageEntry {

  private final URI uri;
  private final ObjectApi objectApi;
  private final String typeName;
  private final String uuid;
  private Set<UriProperty> uriProperties;
  private String displayName;

  ObjectEntry(URI uri, ObjectApi objectApi) {
    this.uri = uri;
    this.objectApi = objectApi;
    this.typeName = Uris.getTypeName(uri);
    this.uuid = Uris.getUuid(uri);

    refresh();
  }

  @Override
  public URI uri() {
    return uri;
  }

  @Override
  public Set<UriProperty> uriProperties() {
    return uriProperties;
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

}

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
import java.util.Objects;
import java.util.Set;
import org.smartbit4all.core.object.ObjectApi;
import hu.aestallon.storageexplorer.util.ObjectMaps;
import hu.aestallon.storageexplorer.util.Pair;
import hu.aestallon.storageexplorer.util.Uris;
import static java.util.stream.Collectors.toSet;

public class ObjectEntry implements StorageEntry {

  private final URI uri;
  private final ObjectApi objectApi;

  public ObjectEntry(URI uri, ObjectApi objectApi) {
    this.uri = uri;
    this.objectApi = objectApi;
  }

  @Override
  public URI uri() {
    return uri;
  }

  @Override
  public Set<UriProperty> uriProperties() {
    return ObjectMaps.flatten(objectApi.load(uri).getObjectAsMap())
        .entrySet().stream()
        .filter(it -> !UriProperty.OWN.equals(it.getKey()))
        .map(Pair::of)
        .map(Pair.onB(Uris::parse))
        .flatMap(Pair.streamOnB())
        .map(it -> UriProperty.parse(it.a(), it.b()))
        .collect(toSet());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;
    ObjectEntry that = (ObjectEntry) o;
    return Objects.equals(Uris.latest(uri), Uris.latest(that.uri));
  }

  @Override
  public int hashCode() {
    return uri.hashCode();
  }

}

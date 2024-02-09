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
import org.smartbit4all.api.collection.CollectionApi;
import hu.aestallon.storageexplorer.util.Uris;

public class ScopedMapEntry extends MapEntry implements ScopedEntry {
  private final URI scopeUri;

  ScopedMapEntry(URI uri, CollectionApi collectionApi, URI scopeUri) {
    super(uri, collectionApi);
    this.scopeUri = scopeUri;
  }

  @Override
  public URI scope() {
    return scopeUri;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;
    if (!super.equals(o))
      return false;
    ScopedMapEntry that = (ScopedMapEntry) o;
    return Uris.equalIgnoringVersion(uri(), that.uri());
  }

  @Override
  public int hashCode() {
    return uri().hashCode();
  }

}

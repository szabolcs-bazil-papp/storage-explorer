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
import com.aestallon.storageexplorer.core.service.StorageIndex;
import com.aestallon.storageexplorer.core.util.Uris;

public final class ScopedObjectEntry extends ObjectEntry implements StorageEntry, ScopedEntry {

  private final URI scopeUri;

  ScopedObjectEntry(final StorageIndex storageIndex, 
                    final Path path, 
                    final URI uri,
                    final URI scopeUri) {
    super(storageIndex, path, uri);
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
    ScopedObjectEntry that = (ScopedObjectEntry) o;
    return Uris.equalIgnoringVersion(uri(), that.uri());
  }

  @Override
  public int hashCode() {
    return uri().hashCode();
  }

  @Override
  public String toString() {
    return uuid();
  }
}

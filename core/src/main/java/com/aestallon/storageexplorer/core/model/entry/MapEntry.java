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

import java.util.List;
import com.aestallon.storageexplorer.core.model.EntryUri;
import com.aestallon.storageexplorer.core.model.instance.dto.StorageId;

/**
 * A {@code storedmap}-rooted map entry — identity only. See {@link ListEntry}'s class javadoc:
 * the same "asSingleVersion() needs a Milestone 5 replacement" note applies here.
 */
public sealed class MapEntry
    extends AbstractStorageEntry
    implements StorageEntry
    permits ScopedMapEntry {

  private final String schema;
  private final String name;

  MapEntry(final StorageId id, final EntryUri uri) {
    super(id, uri);

    final String fullScheme = uri.scheme();
    this.schema = fullScheme.substring(0, fullScheme.lastIndexOf('-'));

    final List<String> segments = uri.segments();
    final String terminalElement = segments.isEmpty() ? "" : segments.get(segments.size() - 1);
    final int dash = terminalElement.lastIndexOf('-');
    this.name = (dash >= 0) ? terminalElement.substring(0, dash) : terminalElement;
  }

  public String schema() {
    return schema;
  }

  public String name() {
    return name;
  }

  public String displayName() {
    return schema + " / " + name;
  }

  @Override
  public String toString() {
    return "MAP (" + schema + "/" + name + ")";
  }
}

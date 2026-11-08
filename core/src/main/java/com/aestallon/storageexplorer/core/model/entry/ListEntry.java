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
 * A {@code storedlist}-rooted list entry — identity only (see {@link StorageEntry}'s class
 * javadoc). {@code asSingleVersion()}/{@code uriProperties()}/{@code refresh()} are gone along
 * with the held index this class used to load through; a stand-in for {@code asSingleVersion()}
 * (needed by {@code QuerySourceResolver.exists(...)} in {@code arc-script}, per the pre-T4.1
 * audit) has to be built on {@code ContentLoadable}/{@code StorageEngine} when that module is
 * migrated (Milestone 5) — not carried forward here in a form that can't actually load anything
 * without a held index to begin with.
 */
public sealed class ListEntry
    extends AbstractStorageEntry
    implements StorageEntry
    permits ScopedListEntry {

  private final String schema;
  private final String name;

  ListEntry(final StorageId id, final EntryUri uri) {
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
    return "LIST (" + schema + "/" + name + ")";
  }

}

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

import org.smartbit4all.core.utility.StringConstant;
import com.aestallon.storageexplorer.core.model.EntryUri;
import com.aestallon.storageexplorer.core.model.instance.dto.StorageId;

/**
 * A {@code storedSeq}-rooted sequence entry — identity only. Its "current value" read needs no
 * dedicated capability at all (per T4.1/the implementation plan): {@link StorageEntryFactory}
 * recognizes it as a plain {@code .o}-rooted entry via the {@code /storedSeq} marker, so it reads
 * through the exact same {@code StorageEngine.read(...)}/{@code ContentHandle} path as any other
 * entry — a caller interested in "current" just reads this entry's content like any other and
 * looks at its own {@code current} field, the same way it would for any other JSON payload.
 */
public final class SequenceEntry extends AbstractStorageEntry implements StorageEntry {

  private static final String MARKER = "storedSeq/";

  private final String schema;
  private final String name;

  SequenceEntry(final StorageId id, final EntryUri uri) {
    super(id, uri);

    final String fullScheme = uri.scheme();
    this.schema = fullScheme.substring(0, fullScheme.lastIndexOf('-'));

    final String uriPath = uri.path();
    final int markerIdx = uriPath.indexOf(MARKER);
    this.name = (markerIdx != -1)
        ? uriPath.substring(markerIdx + MARKER.length(), uriPath.length() - 2)
        : StringConstant.EMPTY;
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
    return "SEQ (" + schema + "/" + name + ")";
  }
}

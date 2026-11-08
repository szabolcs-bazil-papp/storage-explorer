/*
 * Copyright (C) 2025 Szabolcs Bazil Papp
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

import com.aestallon.storageexplorer.core.model.EntryUri;
import com.aestallon.storageexplorer.core.model.instance.dto.StorageId;
import com.aestallon.storageexplorer.core.model.loading.ObjectEntryLoadResult;

public final class GodObjectEntry extends ObjectEntry implements StorageEntry {

  private static String determineConciseName(final EntryUri uri) {
    final String prefix;
    final String schema = uri.scheme();
    if (schema.endsWith("-collections")) {
      prefix = schema.substring(0, schema.length() - "-collections".length());
    } else {
      prefix = schema;
    }

    final var segments = uri.segments();
    final String name = segments.isEmpty() ? "" : segments.get(segments.size() - 1);

    final String suffix;
    if (name.endsWith("-s")) {
      suffix = name.substring(0, name.length() - 2);
    } else {
      suffix = name;
    }
    return prefix + " / " + suffix;
  }

  private final String conciseName;

  GodObjectEntry(final StorageId id, final EntryUri uri) {
    super(id, uri);
    conciseName = determineConciseName(uri);
  }

  @Override
  public String toString() {
    return conciseName;
  }

  public String displayName() {
    return conciseName;
  }

  @Override
  public String getDisplayName(final ObjectEntryLoadResult.SingleVersion version) {
    return conciseName;
  }

}

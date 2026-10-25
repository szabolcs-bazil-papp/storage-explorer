/*
 * Copyright (C) 2026 Szabolcs Bazil Papp
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
import com.aestallon.storageexplorer.core.model.loading.ObjectEntryLoadResult;
import com.aestallon.storageexplorer.core.service.StorageIndex;

/**
 * An {@code org_smartbit4all_api_object_bean_ObjectReferenceById} entry: a pure pointer object
 * holding a logical {@code id} and the {@code refObjectUri} of the entity it represents.
 *
 * <p>
 * Its own URI encodes its logical id chopped into 2-character path segments, e.g.
 * {@code smartLink:/org_smartbit4all_api_object_bean_ObjectReferenceById/b1/f1/70/4a/-f/6a/5-/4c/76/-a/60/2-/3b/78/fd/02/1c/b0}
 * - {@link #uuid()} reconstructs the dashed UUID from those segments, since the generic
 * last-path-segment heuristic of {@link ObjectEntry#uuid()} would otherwise yield a meaningless
 * 2-character fragment.
 */
public final class ObjectReferenceByIdEntry extends ObjectEntry implements StorageEntry {

  private static String reconstructUuid(final URI uri) {
    final String[] pathElements = uri.getPath().split("/");
    final var sb = new StringBuilder();
    for (int i = 2; i < pathElements.length; i++) {
      sb.append(pathElements[i]);
    }
    return sb.toString();
  }

  private final String uuid;

  ObjectReferenceByIdEntry(final StorageIndex<?> storageIndex,
                           final Path path,
                           final URI uri) {
    super(storageIndex, path, uri);
    this.uuid = reconstructUuid(uri);
  }

  @Override
  public String uuid() {
    return uuid;
  }

  @Override
  public String toString() {
    return uuid;
  }

  @Override
  public String getDisplayName(final ObjectEntryLoadResult.SingleVersion version) {
    final var refObjectUri = version.objectAsMap().get("refObjectUri");
    return refObjectUri == null
        ? "Reference (" + uuid + ")"
        : "Reference (" + uuid + ") -> " + refObjectUri;
  }

}

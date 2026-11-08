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

import java.util.Map;
import com.aestallon.storageexplorer.core.model.EntryUri;
import com.aestallon.storageexplorer.core.model.instance.dto.StorageId;
import com.aestallon.storageexplorer.core.model.loading.ObjectEntryLoadResult;

/**
 * A plain, {@code .o}-rooted object entry — identity only (see {@link StorageEntry}'s class
 * javadoc). Content, versioning, outgoing-reference, and scoped-child-folding knowledge all
 * moved out to whichever {@code StorageIndex} implementation is asked (T3.2's
 * {@code StreamingAdjacencyIndex} for references/scoped folding, T3.3's {@code
 * HeaderMetadataIndex} for versioning) — this class no longer tracks any of it itself.
 */
public sealed class ObjectEntry
    extends AbstractStorageEntry
    implements StorageEntry
    permits ScopedObjectEntry, GodObjectEntry, ObjectReferenceByIdEntry {

  /** The platform's large-blob holder type ({@code org.smartbit4all.api.binarydata
   *  .BinaryDataObject}) — its payload is base64-inlined JSON that can be arbitrarily large (it's
   *  the platform's generic "attach a file" mechanism), so it is never loaded for ArcScript
   *  property discovery or graph adjacency scanning (see {@link #isBinaryData()}'s callers). */
  private static final String BINARY_DATA_OBJECT_TYPE_NAME = "BinaryDataObject";

  private final String typeName;
  private final String uuid;

  ObjectEntry(final StorageId id, final EntryUri uri) {
    super(id, uri);
    this.typeName = typeNameOf(uri);
    this.uuid = uuidOf(uri);
  }

  private static String typeNameOf(final EntryUri uri) {
    final var segments = uri.segments();
    if (segments.isEmpty()) {
      return "";
    }
    final String[] typeElements = segments.get(0).split("_");
    return typeElements[typeElements.length - 1];
  }

  private static String uuidOf(final EntryUri uri) {
    final var segments = uri.segments();
    return segments.isEmpty() ? "" : segments.get(segments.size() - 1);
  }

  public String uuid() {
    return uuid;
  }

  public String typeName() {
    return typeName;
  }

  /** Zero-I/O: derived purely from the URI's type segment, same as {@link #typeName()} itself —
   *  callers that must decide whether to even attempt a content read (not just render an icon)
   *  can check this before ever touching the engine. */
  public boolean isBinaryData() {
    return BINARY_DATA_OBJECT_TYPE_NAME.equals(typeName);
  }

  public String getDisplayName(final ObjectEntryLoadResult.SingleVersion version) {
    final var oam = version.objectAsMap();
    final var heuristicName = getHeuristicName(oam);
    final var sb = new StringBuilder(typeName);
    if (!heuristicName.isEmpty()) {
      sb.append(" (").append(heuristicName).append(")");
    }

    final var entryId = version.meta().entryId();
    if (entryId != null && !entryId.isEmpty()) {
      sb.append(" (ID: ").append(entryId).append(")");
    }

    return sb.toString();
  }

  private static String getHeuristicName(final Map<String, Object> oam) {
    final var name = oam.get("name");
    if (name != null) {
      return String.valueOf(name);
    }

    return switch (oam.get("data")) {
      case Map<?, ?> m -> switch (m.get("name")) {
        case String s -> s;
        case null, default -> "";
      };
      case null, default -> "";
    };
  }

  @Override
  public String toString() {
    return typeName;
  }

}

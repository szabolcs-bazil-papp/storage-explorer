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

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.aestallon.storageexplorer.core.model.EntryKind;
import com.aestallon.storageexplorer.core.model.EntryUri;
import com.aestallon.storageexplorer.core.model.instance.dto.StorageId;
import com.google.common.base.Strings;

/**
 * Classifies an {@link EntryUri} into the right {@link StorageEntry} subtype — the sole
 * construction choke point for the entire hierarchy (every entry constructor is package-private;
 * confirmed by the pre-T4.1 audit that nothing else builds one). Reworked for T4.1 in two ways
 * from its pre-immutabilization shape: (1) it no longer depends on {@code core.service
 * .StorageIndex} at all — it takes a plain {@link StorageId}, since the entries it builds no
 * longer hold an index reference either; (2) it recognizes {@code .os}/{@code .td} artifacts too,
 * dispatching to {@link ObjectStreamEntry}/{@link TableDataEntry}.
 *
 * <p>Classification works from two starting points, converging on the same dispatch: a bare,
 * user-supplied/pasted {@link EntryUri} has no further context, so {@link #create(EntryUri)}
 * classifies purely from the address's own shape (the stored-collection markers, {@code
 * /storedSeq}, an {@code ObjectReferenceById} shape — exactly today's heuristic; there is no
 * shape signal that distinguishes a bare {@code .os}/{@code .td} address from a plain object one,
 * so without a hint this always falls through to {@link ObjectEntry}). A {@code
 * FileSystemStorageWalker}-discovered address already knows which kind of file it found, and
 * passes that along as an explicit {@link EntryKind} hint via {@link #create(EntryUri,
 * EntryKind)} instead of making this class re-derive it. A future SQL backend's own discovery
 * mechanism is expected to reuse this same class and the same two-entry-point shape.
 */
public final class StorageEntryFactory {

  private static final Logger log = LoggerFactory.getLogger(StorageEntryFactory.class);

  public static final String STORED_LIST_MARKER = "/storedlist";
  public static final String STORED_MAP_MARKER = "/storedmap";
  public static final String STORED_REF_MARKER = "/storedRef";
  public static final String STORED_SEQ_MARKER = "/storedSeq";

  private final StorageId storageId;

  public StorageEntryFactory(final StorageId storageId) {
    this.storageId = Objects.requireNonNull(storageId, "storageId cannot be null!");
  }

  private static Optional<EntryUri> scopeUri(final String uriString, final String probe) {
    if (Strings.isNullOrEmpty(uriString)) {
      return Optional.empty();
    }
    return scopeEndsAt(uriString, probe).stream()
        .mapToObj(i -> uriString.substring(0, i))
        .findFirst()
        .flatMap(EntryUri::parse);
  }

  private static OptionalInt scopeEndsAt(final String uriString, final String probe) {
    final int idx = uriString.indexOf(probe);
    return (idx > 0) ? OptionalInt.of(idx) : OptionalInt.empty();
  }

  /** Classifies a bare address with no further context — purely from its own shape. */
  public Optional<StorageEntry> create(final EntryUri uri) {
    return create(uri, EntryKind.OBJECT);
  }

  /**
   * Classifies an address a walker already found, with an explicit hint of which kind of file
   * it is — {@link EntryKind#OBJECT} means "no particular hint, use the usual shape heuristic";
   * {@link EntryKind#TABLE_DATA}/{@link EntryKind#OBJECT_STREAM} short-circuit straight to
   * {@link TableDataEntry}/{@link ObjectStreamEntry}, skipping the (pointless, for those kinds)
   * marker matching entirely.
   */
  public Optional<StorageEntry> create(final EntryUri uri, final EntryKind hint) {
    try {
      return switch (hint) {
        case TABLE_DATA -> Optional.of(new TableDataEntry(storageId, uri.latest()));
        case OBJECT_STREAM -> Optional.of(new ObjectStreamEntry(storageId, uri.latest()));
        case OBJECT -> classify(uri.latest());
      };
    } catch (final Exception e) {
      log.error("Cannot initialise StorageEntry [ {} ]: [ {} ]", uri, e.getMessage());
      log.debug(e.getMessage(), e);
      return Optional.empty();
    }
  }

  private Optional<StorageEntry> classify(final EntryUri uri) {
    final String uriString = uri.toString();

    if (uriString.contains(STORED_LIST_MARKER)) {
      return scopeUri(uriString, STORED_LIST_MARKER)
          .<StorageEntry>map(scope -> new ScopedListEntry(storageId, uri, scope))
          .or(() -> Optional.of(new ListEntry(storageId, uri)));

    } else if (uriString.contains(STORED_MAP_MARKER)) {
      return scopeUri(uriString, STORED_MAP_MARKER)
          .<StorageEntry>map(scope -> new ScopedMapEntry(storageId, uri, scope))
          .or(() -> Optional.of(new MapEntry(storageId, uri)));

    } else if (uriString.contains(STORED_REF_MARKER)) {
      return scopeUri(uriString, STORED_REF_MARKER)
          .<StorageEntry>map(scope -> new ScopedObjectEntry(storageId, uri, scope))
          .or(() -> Optional.of(new GodObjectEntry(storageId, uri)));

    } else if (uriString.contains(STORED_SEQ_MARKER)) {
      return Optional.of(new SequenceEntry(storageId, uri));

    } else if (isObjectReferenceById(uri)) {
      return Optional.of(new ObjectReferenceByIdEntry(storageId, uri));

    } else {
      return Optional.of(new ObjectEntry(storageId, uri));
    }
  }

  // the short, underscore-split-terminal type name (e.g. "org_..._ObjectReferenceById" -> just
  // "ObjectReferenceById") - matching ObjectEntry's own typeNameOf derivation, not the full
  // first path segment; getting this wrong (comparing against the full segment) would mean
  // this branch can never match, silently falling through to a plain ObjectEntry instead.
  private static final String OBJECT_REFERENCE_BY_ID_TYPE_NAME = "ObjectReferenceById";

  private static boolean isObjectReferenceById(final EntryUri uri) {
    final var segments = uri.segments();
    if (segments.isEmpty()) {
      return false;
    }
    final String[] parts = segments.get(0).split("_");
    return OBJECT_REFERENCE_BY_ID_TYPE_NAME.equals(parts[parts.length - 1]);
  }

}

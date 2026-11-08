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

package com.aestallon.storageexplorer.core.service.engine;

import java.time.OffsetDateTime;
import com.aestallon.storageexplorer.core.model.EntryUri;

/**
 * A deliberately scoped, backend-agnostic view of an entry's metadata — not a full mirror of
 * either backend's richer internal representation. Mirrors the shape of the existing (platform-
 * dependent, {@code java.net.URI}-based) {@code ObjectEntryMeta}
 * ({@code core/.../model/loading/ObjectEntryMeta.java}), minus its redundant {@code
 * storageSchema} field ({@link EntryUri#scheme()} already carries that), per the implementation
 * plan's T2.1.
 *
 * <p>Both a future FS-backed and RDB-backed {@code MetadataAware} implementation populate this
 * same record from their own richer internal model ({@code FsStorageEngine}'s {@code Head}/
 * {@code ObjectVersion}, or a RDB row) — neither backend's internal representation crosses into
 * {@code core.service.index} itself. Each field is nullable: a backend that has nothing to say
 * for a given field (e.g. no stable {@code entryId} distinct from the address itself) reports it
 * as {@code null} rather than inventing a value.
 *
 * @param uri         the entry's address
 * @param qualifiedName the entry's class/type name, if the backend knows one
 * @param versionNr   the version this metadata describes
 * @param createdAt   when this version was created
 * @param lastModified when the entry's storage representation was last touched, as epoch millis
 * @param entryId     a backend-specific stable identifier for the entry, distinct from its
 *                    address (e.g. a physical row id); not every backend has one
 */
public record EntryMetadata(
    EntryUri uri,
    String qualifiedName,
    Long versionNr,
    OffsetDateTime createdAt,
    Long lastModified,
    String entryId) {
}

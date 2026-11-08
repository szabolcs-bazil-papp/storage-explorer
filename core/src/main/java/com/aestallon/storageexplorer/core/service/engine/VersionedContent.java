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

/**
 * One version of a multi-version entry's history ({@link StorageEngine#history}) — grown onto
 * the shared engine contract in Milestone 7 (T7.1) once the Swing inspector views turned out to
 * be the "real consumer" {@link StorageEngine}'s own javadoc anticipated for this exact method.
 * {@code FsStorageEngine} already had the underlying capability ({@code history}/{@code
 * readVersion}); this is its translation across the same shared-type boundary {@code head}/
 * {@code list}/{@code newVersion}/{@code amend} already cross.
 *
 * @param serial   the version's data serial
 * @param metadata this version's own header fields (not the entry's current head's)
 * @param content  a handle to this version's content, independent of whatever the current head is
 */
public record VersionedContent(long serial, EntryMetadata metadata, ContentHandle content) {
}

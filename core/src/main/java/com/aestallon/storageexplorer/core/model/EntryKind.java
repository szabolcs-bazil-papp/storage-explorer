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

package com.aestallon.storageexplorer.core.model;

/**
 * What kind of persisted artifact an address names — the shared, backend-agnostic counterpart
 * of {@code FileSystemStorageWalker.Kind} (which stays FS-mechanics-internal, tied to file
 * suffixes; a future RDB engine has no files to tag by suffix at all, but still needs to report
 * this same three-way distinction). Lives in {@code core.model}, not {@code core.service.engine}
 * (its original home, T3.1) — moved here (T4.1) once {@code core.model.entry.StorageEntryFactory}
 * needed it too: classification by kind is fundamentally an addressing/identity concept, which
 * {@code core.model} already owns ({@link EntryUri}), not a {@code StorageEngine} behavior.
 * {@code StorageEngine#list}'s own {@code Listed} record still uses this same type.
 */
public enum EntryKind {
  OBJECT,
  TABLE_DATA,
  OBJECT_STREAM
}

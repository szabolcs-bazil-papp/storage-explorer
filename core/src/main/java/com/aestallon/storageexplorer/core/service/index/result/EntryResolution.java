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

package com.aestallon.storageexplorer.core.service.index.result;

import com.aestallon.storageexplorer.core.model.EntryUri;
import com.aestallon.storageexplorer.core.model.entry.StorageEntry;

/**
 * The outcome of resolving a {@link EntryUri} against a {@code StorageIndex} — whether the
 * address names a {@link StorageEntry} this index can produce, or not.
 *
 * <p>This is deliberately not a fuzzy/best-effort lookup (the implementation plan's motivating
 * decision to drop the old eager, whole-storage URI-guessing index): {@code Unknown} means the
 * index actually looked and found nothing (or the backend rejected it), with a reason, not that
 * the index gave up early.
 */
public sealed interface EntryResolution {

  /** The address resolves to this entry. */
  record Known(StorageEntry entry) implements EntryResolution {}

  /** The address does not resolve, with why not (not found, backend error, invalid shape, ...). */
  record Unknown(EntryUri uri, String reason) implements EntryResolution {}
}

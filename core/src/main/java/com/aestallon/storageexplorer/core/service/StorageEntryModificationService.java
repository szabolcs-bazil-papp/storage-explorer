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

package com.aestallon.storageexplorer.core.service;

import com.aestallon.storageexplorer.core.model.entry.StorageEntry;
import com.aestallon.storageexplorer.core.model.instance.StorageInstance;

/**
 * Saves an edited entry's content back to storage. Reworked for T4.2/T4.3: no longer generic
 * over the old (deleted) {@code StorageIndex<T>} — a single implementation now covers every
 * backend, since {@code StorageInstance.amend}/{@code newVersion} already are backend-agnostic
 * (built on {@code StorageEngine} underneath). Its result shape already matched the
 * {@code Ok(StorageEntry)}/{@code Err(StorageEntry, String)} convention before this rework, so
 * it's unchanged here.
 */
public interface StorageEntryModificationService {

  /** The one implementation, for {@code client}/{@code swing}/{@code app-cli} callers that have
   *  no other way to reach the package-private {@code StorageEntryModificationServiceImpl}. */
  static StorageEntryModificationService of(final StorageInstance storageInstance) {
    return new StorageEntryModificationServiceImpl(storageInstance);
  }

  sealed interface StorageEntryModificationResult {

    StorageEntry storageEntry();

    record Ok(StorageEntry storageEntry) implements StorageEntryModificationResult {}


    record Err(StorageEntry storageEntry, String msg) implements StorageEntryModificationResult {}

  }


  sealed interface ModificationMode {

    record Overwrite(long version) implements ModificationMode {}


    record SaveNewVersion(long version) implements ModificationMode {}


    record OverwriteSingleVersion() implements ModificationMode {}

  }

  StorageEntryModificationResult modify(final StorageEntry storageEntry,
                                        final String content,
                                        final ModificationMode mode);

}

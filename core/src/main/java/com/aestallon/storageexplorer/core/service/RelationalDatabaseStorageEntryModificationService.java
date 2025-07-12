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

import com.aestallon.storageexplorer.common.util.NotImplementedException;
import com.aestallon.storageexplorer.core.model.entry.StorageEntry;

class RelationalDatabaseStorageEntryModificationService
    implements StorageEntryModificationService<RelationalDatabaseStorageIndex> {

  @Override
  public StorageEntryModificationResult modify(final StorageEntry storageEntry,
                                               final String content, 
                                               final ModificationMode mode) {
    throw new NotImplementedException(
        "Not yet implemented: RelationalDatabaseStorageEntryModificationService.modify(StorageEntry, String, ModificationMode)");
  }

}

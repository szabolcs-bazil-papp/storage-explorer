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

package com.aestallon.storageexplorer.core.service.index;

import com.aestallon.storageexplorer.core.model.entry.StorageEntry;
import com.aestallon.storageexplorer.core.service.index.result.MetadataResult;

/** A {@link StorageIndex} capable of reporting an entry's metadata, without loading its full
 *  content. */
public interface MetadataAware extends StorageIndex {

  /** {@code entry}'s metadata. See {@link StorageIndex}'s class javadoc for the concurrency
   *  contract this must honor. */
  MetadataResult metadataOf(StorageEntry entry);
}

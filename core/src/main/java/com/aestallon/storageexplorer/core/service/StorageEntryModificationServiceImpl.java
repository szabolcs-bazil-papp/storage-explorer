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

import java.nio.charset.StandardCharsets;
import com.aestallon.storageexplorer.core.model.entry.StorageEntry;
import com.aestallon.storageexplorer.core.model.instance.StorageEntryAmendCommand;
import com.aestallon.storageexplorer.core.model.instance.StorageEntryPutCommand;
import com.aestallon.storageexplorer.core.model.instance.StorageInstance;
import com.aestallon.storageexplorer.core.model.instance.StorageWriteResult;

/**
 * The one implementation of {@link StorageEntryModificationService} — backend-agnostic since
 * T4.3, built on {@link StorageInstance#amend}/{@link StorageInstance#newVersion} rather than a
 * backend-specific index (replacing the pre-T4.2 {@code FileSystemStorageEntryModificationService}
 * stub, which was never actually implemented, and the deleted RDB equivalent).
 *
 * <p>Note: neither {@code Overwrite}/{@code SaveNewVersion}'s carried {@code version} is checked
 * against the storage's current head before writing (no optimistic-concurrency check) — {@code
 * StorageEngine.amend}/{@code newVersion} don't support one today; a caller editing stale content
 * can silently overwrite a newer version. Flagged, not fixed, here — out of this task's scope.
 */
final class StorageEntryModificationServiceImpl implements StorageEntryModificationService {

  private final StorageInstance storageInstance;

  StorageEntryModificationServiceImpl(final StorageInstance storageInstance) {
    this.storageInstance = storageInstance;
  }

  @Override
  public StorageEntryModificationResult modify(final StorageEntry storageEntry, final String content,
                                                 final ModificationMode mode) {
    final byte[] payload = content.getBytes(StandardCharsets.UTF_8);
    final StorageWriteResult result = switch (mode) {
      case ModificationMode.Overwrite(long version) ->
          storageInstance.amend(storageEntry, StorageEntryAmendCommand.of(version, payload));
      case ModificationMode.SaveNewVersion ignored ->
          storageInstance.newVersion(storageEntry, new StorageEntryPutCommand(payload));
      case ModificationMode.OverwriteSingleVersion ignored ->
          storageInstance.newVersion(storageEntry, new StorageEntryPutCommand(payload));
    };
    return switch (result) {
      case StorageWriteResult.Ok ok -> new StorageEntryModificationResult.Ok(storageEntry);
      case StorageWriteResult.Err err -> new StorageEntryModificationResult.Err(storageEntry, err.reason());
    };
  }

}

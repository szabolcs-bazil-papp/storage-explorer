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

package com.aestallon.storageexplorer.swing.ui.misc;

import java.net.URI;
import java.util.Optional;
import com.aestallon.storageexplorer.core.model.EntryUri;
import com.aestallon.storageexplorer.core.model.entry.StorageEntry;
import com.aestallon.storageexplorer.core.model.instance.StorageInstance;
import com.aestallon.storageexplorer.core.service.index.result.EntryResolution;

/** Resolves a pasted/legacy {@code java.net.URI} against a {@link StorageInstance} — the small
 *  shared boundary conversion every "jump to this address" action in {@code swing} needs now
 *  that {@code StorageInstance#acquire} is gone (T4.3) in favour of {@link
 *  StorageInstance#resolve(EntryUri)}. */
public final class StorageEntryResolver {

  private StorageEntryResolver() {}

  public static Optional<StorageEntry> resolve(final StorageInstance storageInstance, final URI uri) {
    return EntryUri.parse(uri.toString())
        .map(storageInstance::resolve)
        .flatMap(res -> switch (res) {
          case EntryResolution.Known(var entry) -> Optional.of(entry);
          case EntryResolution.Unknown u -> Optional.<StorageEntry>empty();
        });
  }

}

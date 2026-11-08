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

package com.aestallon.storageexplorer.core.service.index.impl;

import java.util.Objects;
import com.aestallon.storageexplorer.core.model.EntryUri;
import com.aestallon.storageexplorer.core.model.entry.StorageEntry;
import com.aestallon.storageexplorer.core.model.entry.StorageEntryFactory;
import com.aestallon.storageexplorer.core.model.instance.dto.StorageId;
import com.aestallon.storageexplorer.core.service.engine.EngineResult;
import com.aestallon.storageexplorer.core.service.engine.EntryMetadata;
import com.aestallon.storageexplorer.core.service.engine.StorageEngine;
import com.aestallon.storageexplorer.core.service.index.MetadataAware;
import com.aestallon.storageexplorer.core.service.index.result.EntryResolution;
import com.aestallon.storageexplorer.core.service.index.result.MetadataResult;

/**
 * A {@link MetadataAware} that never parses an entry's body: {@link #metadataOf} goes straight to
 * {@link StorageEngine#head}, which already separates header fields (version count-ish/
 * timestamps/class name) from payload on every backend — the FS side via Milestone 1's
 * {@code ObjectVersion}/{@code ObjectData} model, translated by {@code FsStorageEngineAdapter};
 * a future RDB engine would do the equivalent from its own row data. No cache, no persistence,
 * same reasoning as {@link PassthroughContentIndex}.
 */
public final class HeaderMetadataIndex implements MetadataAware {

  private final StorageId storageId;
  private final StorageEngine engine;
  private final StorageEntryFactory entryFactory;

  public HeaderMetadataIndex(final StorageId storageId, final StorageEngine engine,
                              final StorageEntryFactory entryFactory) {
    this.storageId = Objects.requireNonNull(storageId, "storageId cannot be null!");
    this.engine = Objects.requireNonNull(engine, "engine cannot be null!");
    this.entryFactory = Objects.requireNonNull(entryFactory, "entryFactory cannot be null!");
  }

  @Override
  public StorageId storageId() {
    return storageId;
  }

  @Override
  public EntryResolution resolve(final EntryUri uri) {
    if (!engine.exists(uri)) {
      return new EntryResolution.Unknown(uri, "no entry at this address");
    }
    return entryFactory.create(uri)
        .<EntryResolution>map(EntryResolution.Known::new)
        .orElseGet(() -> new EntryResolution.Unknown(uri, "exists, but could not be classified"));
  }

  @Override
  public MetadataResult metadataOf(final StorageEntry entry) {
    final EntryUri uri = entry.uri();
    return switch (engine.head(uri)) {
      case EngineResult.Ok<EntryMetadata> ok -> new MetadataResult.Ok(entry, ok.value());
      case EngineResult.Err<EntryMetadata> err -> new MetadataResult.Err(entry, err.error().toString());
    };
  }

  @Override
  public void close() {
    // nothing of its own to release - see PassthroughContentIndex's own close() note.
  }
}

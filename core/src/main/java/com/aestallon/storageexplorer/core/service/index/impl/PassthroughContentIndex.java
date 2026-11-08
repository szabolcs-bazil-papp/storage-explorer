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
import com.aestallon.storageexplorer.core.service.engine.ContentHandle;
import com.aestallon.storageexplorer.core.service.engine.EngineResult;
import com.aestallon.storageexplorer.core.service.engine.StorageEngine;
import com.aestallon.storageexplorer.core.service.index.ContentLoadable;
import com.aestallon.storageexplorer.core.service.index.result.ContentLoadResult;
import com.aestallon.storageexplorer.core.service.index.result.EntryResolution;

/**
 * The simplest possible {@link ContentLoadable} — no cache, no persistence (per the decisions
 * log: a handful of open inspectors don't need either). Reads go straight through the held
 * {@link StorageEngine}, whether it's backed by {@code FsStorageEngine} or a future
 * {@code RdbStorageEngine} underneath; this class doesn't care which. The caller (an inspector)
 * calls {@link ContentHandle#materialize()} itself when it actually wants to render something —
 * this index never does that eagerly.
 *
 * <p><strong>Entry construction note</strong>: {@link #resolve} needs to turn a bare address into
 * a {@link StorageEntry}, which today still means going through the existing (pre-Milestone-4)
 * {@link StorageEntryFactory} — the immutable, {@code StorageEngine}-backed entry hierarchy
 * {@code StorageEntryFactory} is itself reworked into (dropping its own {@code
 * core.service.StorageIndex} dependency) is T4.1's job, not this one's. This class only depends
 * on {@code StorageEntryFactory} itself, never on the old index class it happens to be
 * constructed with today — that dependency is the caller's problem (test fixtures now, real
 * wiring in Milestone 4), not this class's.
 */
public final class PassthroughContentIndex implements ContentLoadable {

  private final StorageId storageId;
  private final StorageEngine engine;
  private final StorageEntryFactory entryFactory;

  public PassthroughContentIndex(final StorageId storageId, final StorageEngine engine,
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
  public ContentLoadResult load(final StorageEntry entry) {
    final EntryUri uri = entry.uri();
    return switch (engine.read(uri)) {
      case EngineResult.Ok<ContentHandle> ok -> new ContentLoadResult.Ok(entry, ok.value());
      case EngineResult.Err<ContentHandle> err -> new ContentLoadResult.Err(entry, err.error().toString());
    };
  }

  @Override
  public void close() {
    // nothing of its own to release - the held StorageEngine's lifetime isn't this index's to
    // manage (it's handed to every operation-scoped index a StorageInstance constructs, per the
    // overview's decision; whoever owns the StorageInstance owns the engine).
  }
}

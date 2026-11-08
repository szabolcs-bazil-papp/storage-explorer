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
import com.aestallon.storageexplorer.core.service.engine.StorageEngine;
import com.aestallon.storageexplorer.core.service.index.QueryableIndex;
import com.aestallon.storageexplorer.core.service.index.result.AdjacencyResult;
import com.aestallon.storageexplorer.core.service.index.result.ContentLoadResult;
import com.aestallon.storageexplorer.core.service.index.result.EntryResolution;

/**
 * The simplest correct {@link QueryableIndex}: delegates content loads to a held {@link
 * PassthroughContentIndex} and adjacency discovery to a held {@link StreamingAdjacencyIndex} —
 * both already individually correct (T3.1/T3.3), nothing new to get right here beyond the
 * delegation itself. Built fresh per ArcScript query execution ({@code StorageInstance
 * .newQueryIndex()}, T5.2), the combined capability {@code StorageInstanceExaminer
 * .discoverProperty}'s recursion needs at every step (load a host's content, then walk its
 * outgoing references) — discarded when the query ends, never persisted.
 */
public final class CompositeQueryableIndex implements QueryableIndex {

  private final PassthroughContentIndex content;
  private final StreamingAdjacencyIndex adjacency;

  public CompositeQueryableIndex(final StorageId storageId, final StorageEngine engine,
                                  final StorageEntryFactory entryFactory) {
    Objects.requireNonNull(storageId, "storageId cannot be null!");
    Objects.requireNonNull(engine, "engine cannot be null!");
    Objects.requireNonNull(entryFactory, "entryFactory cannot be null!");
    this.content = new PassthroughContentIndex(storageId, engine, entryFactory);
    this.adjacency = new StreamingAdjacencyIndex(storageId, engine, entryFactory);
  }

  @Override
  public StorageId storageId() {
    return content.storageId();
  }

  @Override
  public EntryResolution resolve(final EntryUri uri) {
    return content.resolve(uri);
  }

  @Override
  public ContentLoadResult load(final StorageEntry entry) {
    return content.load(entry);
  }

  @Override
  public AdjacencyResult outgoingReferences(final StorageEntry entry) {
    return adjacency.outgoingReferences(entry);
  }

  @Override
  public void close() {
    content.close();
    adjacency.close();
  }
}

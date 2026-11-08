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

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import com.aestallon.storageexplorer.core.model.EntryUri;
import com.aestallon.storageexplorer.core.model.entry.StorageEntry;
import com.aestallon.storageexplorer.core.model.instance.dto.StorageId;
import com.aestallon.storageexplorer.core.service.index.Projection;
import com.aestallon.storageexplorer.core.service.index.Tabular;
import com.aestallon.storageexplorer.core.service.index.result.EntryResolution;

/**
 * The simplest correct {@link Tabular}: entirely in-memory, no spill (that's Milestone 9). Goal
 * at this milestone is proving the {@code Tabular}/{@link Projection} shape is usable at all, not
 * performance — see {@link InMemoryProjection} for where the actual row/column/cell logic lives.
 *
 * <p>Deliberately decoupled from {@code StorageEngine}/{@code ContentLoadable} for now: content
 * access is whatever the two injected functions ({@link ContentSource}/{@link EntryResolver}) do.
 * A real caller (Milestone 5+) would back them with a held {@code StorageEngine}'s {@code read}/
 * {@code materialize}; this milestone's own tests back them with a small synthetic entry graph,
 * exactly as the task asks for — wiring a real engine in is follow-up work for whoever actually
 * consumes this, not a gap in this class.
 */
public final class InMemoryTabular implements Tabular {

  /** An entry's own content, as a plain, already-loaded map — navigable by {@link
   *  com.aestallon.storageexplorer.core.service.index.PropertyPath}. */
  @FunctionalInterface
  public interface ContentSource {
    Map<String, Object> contentOf(StorageEntry entry);
  }

  /** Turns a reference encountered mid-path into the entry to continue from. */
  @FunctionalInterface
  public interface EntryResolver {
    Optional<StorageEntry> resolve(EntryUri uri);
  }

  private final StorageId storageId;
  private final ContentSource contentSource;
  private final EntryResolver entryResolver;

  public InMemoryTabular(final StorageId storageId, final ContentSource contentSource,
                          final EntryResolver entryResolver) {
    this.storageId = Objects.requireNonNull(storageId, "storageId cannot be null!");
    this.contentSource = Objects.requireNonNull(contentSource, "contentSource cannot be null!");
    this.entryResolver = Objects.requireNonNull(entryResolver, "entryResolver cannot be null!");
  }

  @Override
  public StorageId storageId() {
    return storageId;
  }

  @Override
  public EntryResolution resolve(final EntryUri uri) {
    return entryResolver.resolve(uri)
        .<EntryResolution>map(EntryResolution.Known::new)
        .orElseGet(() -> new EntryResolution.Unknown(uri, "not found"));
  }

  @Override
  public Projection newProjection() {
    return new InMemoryProjection(contentSource, entryResolver);
  }

  @Override
  public void close() {
    // nothing of its own to release - see PassthroughContentIndex's own close() note.
  }
}

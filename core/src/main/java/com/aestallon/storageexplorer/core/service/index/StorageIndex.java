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

import com.aestallon.storageexplorer.core.model.EntryUri;
import com.aestallon.storageexplorer.core.model.instance.dto.StorageId;
import com.aestallon.storageexplorer.core.service.index.result.EntryResolution;

/**
 * The transient, operation-scoped view over a storage this rework is building — distinct from
 * {@code core.service.StorageIndex} (the existing eager, whole-storage, in-memory cache this one
 * replaces; deleted in Milestone 4). An instance is created for the lifetime of one operation
 * (rendering a graph, running an ArcScript query, loading one inspector view) and closed via
 * {@link #close()} when that operation ends — it is not a long-lived, storage-wide cache the way
 * its predecessor was. {@code core}-internal code ({@link ContentLoadable}/{@link AdjacencyAware}/
 * {@link MetadataAware} implementations, built on a {@code StorageEngine}) calls that
 * {@code StorageEngine} directly; {@code StorageInstance}'s own operation-scoped factory methods
 * (e.g. {@code newGraphIndex()}) construct and hand out these implementations.
 *
 * <p><strong>Concurrency contract, stated here because it is a hard requirement, not a style
 * choice</strong>: every implementation of this interface — in-memory, disk-backed, the tree-
 * bookmark index — must be safe for concurrent access <em>within one operation</em>, without
 * external synchronization by the caller. This is driven directly by {@code
 * BatchLoaderExecutor}'s existing concurrent {@code tryLoad()} calls across a thread pool: a
 * graph render and an ArcScript execution may both be reading through the same instance at the
 * same moment. The type system cannot enforce this — it is restated here loudly because later
 * migration work (the plan's Milestone 6) depends on every implementation actually honoring it,
 * not just this interface declaring it.
 */
public interface StorageIndex extends AutoCloseable {

  /** Which storage this index is a view over. */
  StorageId storageId();

  /** Resolves an address against this index — not a fuzzy/best-effort lookup; see
   *  {@link EntryResolution}'s own javadoc. */
  EntryResolution resolve(EntryUri uri);

  /** Releases whatever this index's operation-scoped lifetime holds (caches, open handles).
   *  Narrowed from {@link AutoCloseable#close()}: implementations are not expected to need a
   *  checked exception for this. */
  @Override
  void close();
}

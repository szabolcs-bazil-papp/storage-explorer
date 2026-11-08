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

package com.aestallon.storageexplorer.core.service.engine;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.stream.Stream;
import com.aestallon.storageexplorer.core.model.EntryKind;
import com.aestallon.storageexplorer.core.model.EntryUri;
import com.aestallon.storageexplorer.core.model.loading.IndexingTarget;

/**
 * The shared, cross-backend contract for raw storage interaction — the Rust-{@code Storage}-
 * shaped low-level operations, carried exactly as ported in Milestone 1 (for the FS side) and
 * (eventually, by a future {@code RdbStorageEngine}) for the RDB side. {@code core}-internal only
 * (a convention enforced by package placement and code review, not by the build): {@code
 * StorageInstance} exposes a public, domain-shaped API on top of one of these, never this
 * interface's raw signatures, directly, to {@code client}/{@code swing}/{@code app-cli}/
 * {@code spring}.
 *
 * <p><strong>Must be safe for concurrent use by multiple operations at once</strong> — a single
 * instance is shared across every concurrent operation running against one {@code
 * StorageInstance} (a graph render and an ArcScript execution may both be reading through it at
 * the same moment), mirroring {@link com.aestallon.storageexplorer.core.service.index.StorageIndex}'s
 * own stated contract.
 *
 * <p><strong>Deliberately scoped, not the full method list implied by the implementation plan's
 * Milestone 1 overview</strong>: {@code relations}/{@code streams}/{@code readStream}/
 * {@code readTableData} are not declared here yet — nothing so far calls them, and declaring
 * them now would mean inventing their shared return shapes (mirroring {@code head}'s/
 * {@code list}'s own translation from {@code FsStorageEngine}'s richer FS-internal types) before
 * any caller exists to validate the shape against. {@code newVersion}/{@code amend} were added
 * in T4.3, once {@code StorageInstance} became their first real consumer; {@code history} was
 * added in T7.1, once the Swing object inspector's multi-version tab browsing turned out to need
 * exactly it; {@code close}/{@code readBatch} were added once a real {@code RdbStorageEngine}
 * (not just {@code FsStorageEngine}, which never held anything worth closing) existed: a
 * connection pool is a resource {@code StorageInstance} must be able to release when its
 * configuration changes, and a per-entry round trip that's merely "a cheap local file open" on FS
 * becomes a real, ask-the-database-every-time cost on a backend with actual network latency. Grow
 * this interface when a real consumer needs more of them — each addition should
 * come with the same kind of translation {@code head}/{@code list}/{@code newVersion}/{@code
 * amend}/{@code history} already demonstrate, not a leak of {@code core.service.fs}'s internal
 * model types across this boundary.
 */
public interface StorageEngine extends AutoCloseable {

  /** Releases whatever this backend holds open (a connection pool; nothing, for FS) — called by
   *  {@code StorageInstance} both when it's torn down and whenever it's reconfigured onto a new
   *  engine instance, so an engine is never simply dropped while still holding a resource. */
  @Override
  void close();

  /** Whether the entry exists. */
  boolean exists(EntryUri uri);

  /** The entry's path on disk, if this backend has one (always present for FS; always empty for
   *  a future RDB engine). Deterministic from the address alone — never conditioned on whether
   *  the entry actually exists. */
  Optional<Path> pathOf(EntryUri uri);

  /** A handle to the entry's content (the head version, if the address doesn't pin one). */
  EngineResult<ContentHandle> read(EntryUri uri);

  /** The entry's metadata — a deliberately scoped, backend-agnostic view; see
   *  {@link EntryMetadata}'s own javadoc. */
  EngineResult<EntryMetadata> head(EntryUri uri);

  /** One item of a {@link #list} listing: an addressed entry, kind-tagged. */
  record Listed(EntryUri uri, EntryKind kind) {}

  /** Lists every persisted entry under this storage (or a schema/type-filtered subset of it),
   *  lazily: items come out as the tree/table is read, in no particular order. The returned
   *  stream must be consumed or closed. */
  Stream<Listed> list(IndexingTarget target);

  /** What a write produced — deliberately scoped: just the new serial, not a full version
   *  record (nothing yet needs more than that; see {@link EntryMetadata} for header/metadata
   *  reads instead). Empty for a single-version object (it has no serial to report). */
  record Committed(OptionalLong serial) {}

  /** Adds a new version with {@code payload} and redirects the head to it (or, for a single-
   *  version object, simply replaces its payload). */
  EngineResult<Committed> newVersion(EntryUri uri, byte[] payload);

  /** Replaces the payload of an existing version ({@code serial}) in place — the version record
   *  itself is untouched, the head is not redirected. {@code touchHead} bumps the entry's own
   *  modification marker so caches keyed on it notice the change underneath an unchanged
   *  descriptor; see {@code FsStorageEngine.amend}'s own javadoc for why that matters on FS. */
  EngineResult<Void> amend(EntryUri uri, long serial, byte[] payload, boolean touchHead);

  /** Every data version of a multi-version object, oldest first — {@link
   *  EntryUri#isSingleVersion()} must be {@code false}; a single-version object has no history
   *  to read (it has exactly one content, via {@link #read}). */
  EngineResult<List<VersionedContent>> history(EntryUri uri);

  /**
   * Resolves many entries' content in as few round-trips as the backend can manage. A single
   * failing address never fails the whole call — each is reported independently, exactly as
   * {@link #read} would report it alone, so a caller that asked for a thousand entries and got
   * nine hundred back still has nine hundred usable handles.
   *
   * <p>The default implementation just loops {@link #read} — correct for any backend, optimal for
   * one where a "batch" has no meaning beyond that (FS: each read is already a cheap local file
   * open). A backend where round-trips actually cost something (RDB: one network call per query)
   * overrides this with a real batched query — the entire reason this method exists is to give
   * callers that would otherwise read one entry at a time across a few thousand entries (graph
   * adjacency scanning, ArcScript's columnar projection) a way to ask once instead.
   *
   * @param uris the addresses to resolve, order not significant
   * @return one result per input address, same cardinality as {@code uris}
   */
  default Map<EntryUri, EngineResult<ContentHandle>> readBatch(final List<EntryUri> uris) {
    final Map<EntryUri, EngineResult<ContentHandle>> out = new LinkedHashMap<>(uris.size());
    for (final EntryUri uri : uris) {
      out.put(uri, read(uri));
    }
    return out;
  }
}

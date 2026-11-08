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

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import com.aestallon.storageexplorer.core.model.EntryUri;
import com.aestallon.storageexplorer.core.model.entry.StorageEntry;
import com.aestallon.storageexplorer.core.model.entry.StorageEntryFactory;
import com.aestallon.storageexplorer.core.model.instance.dto.StorageId;
import com.aestallon.storageexplorer.core.model.loading.IndexingTarget;
import com.aestallon.storageexplorer.core.service.engine.StorageEngine;
import com.aestallon.storageexplorer.core.service.index.ContentLoadable;
import com.aestallon.storageexplorer.core.service.index.result.ContentLoadResult;
import com.aestallon.storageexplorer.core.service.index.result.EntryResolution;

/**
 * The one long-lived {@code StorageIndex} a {@code StorageInstance} keeps forever (decisions log
 * §3 decision 2 / §9) — everywhere else in Milestone 3, a bare {@link StorageEngine} is handed in
 * from outside for one disposable operation; this one instead lives inside the owning
 * {@code StorageInstance} itself (its {@code treeIndex} field, wired in Milestone 4) and backs
 * the StorageTree's bookmark/tracked-entry set.
 *
 * <p><strong>Deliberately not a cache of "everything seen so far"</strong>: {@link #resolve}
 * only ever reports {@code Known} for an address that was explicitly {@link #track}ed — it is
 * not a general-purpose resolver the way {@link PassthroughContentIndex#resolve} is (that would
 * defeat the entire point of this being a small, bounded, explicit bookmark set, not a second
 * whole-storage cache by another name).
 *
 * <p>{@link #rebuildSurface} is the one bulk-ish exception, and a deliberate one: the "SURFACE"
 * rebuild from §10's reindex/reimport rework — not something the normal track/forget flow ever
 * triggers on its own. "SURFACE" here means what the old {@code IndexingStrategyType.INITIAL}
 * strategy already meant (discovery/addressing only, no content load — the decisions log's §3
 * "SURFACE" is that same concept under its original name) — not a directory-depth limit.
 * Best-effort, per §10's explicit framing: for a sufficiently large storage, aborting cleanly
 * (not hanging, not OOMing) is an acceptable outcome, not a defect to design around.
 */
public final class TreeBookmarkIndex implements ContentLoadable {

  /** Aborts a {@link #rebuildSurface} that's grown past a sane bookmark-surface size, rather
   *  than silently trying to hold an entire large storage in memory. Not tuned for any
   *  particular storage size — just large enough that an ordinary tree surface never comes
   *  close, small enough that a runaway walk is caught quickly. */
  public static final int DEFAULT_SURFACE_REBUILD_LIMIT = 50_000;

  /** The outcome of a {@link #rebuildSurface} call. */
  public sealed interface SurfaceRebuildResult {

    record Completed(int count) implements SurfaceRebuildResult {}

    record Aborted(String reason) implements SurfaceRebuildResult {}
  }

  private final StorageId storageId;
  private final StorageEngine engine;
  private final StorageEntryFactory entryFactory;
  private final PassthroughContentIndex loader;
  private final int surfaceRebuildLimit;
  private final ConcurrentHashMap<EntryUri, StorageEntry> tracked = new ConcurrentHashMap<>();

  public TreeBookmarkIndex(final StorageId storageId, final StorageEngine engine,
                            final StorageEntryFactory entryFactory) {
    this(storageId, engine, entryFactory, DEFAULT_SURFACE_REBUILD_LIMIT);
  }

  public TreeBookmarkIndex(final StorageId storageId, final StorageEngine engine,
                            final StorageEntryFactory entryFactory, final int surfaceRebuildLimit) {
    this.storageId = Objects.requireNonNull(storageId, "storageId cannot be null!");
    this.engine = Objects.requireNonNull(engine, "engine cannot be null!");
    this.entryFactory = Objects.requireNonNull(entryFactory, "entryFactory cannot be null!");
    this.loader = new PassthroughContentIndex(storageId, engine, entryFactory);
    this.surfaceRebuildLimit = surfaceRebuildLimit;
  }

  @Override
  public StorageId storageId() {
    return storageId;
  }

  @Override
  public EntryResolution resolve(final EntryUri uri) {
    final StorageEntry entry = tracked.get(uri);
    return (entry != null)
        ? new EntryResolution.Known(entry)
        : new EntryResolution.Unknown(uri, "not tracked");
  }

  @Override
  public ContentLoadResult load(final StorageEntry entry) {
    return loader.load(entry);
  }

  /** Resolves {@code uri} and, if known, adds it to the tracked set. Returns the resolution
   *  either way, so the caller can tell whether tracking actually took. */
  public EntryResolution track(final EntryUri uri) {
    if (!engine.exists(uri)) {
      return new EntryResolution.Unknown(uri, "no entry at this address");
    }
    final EntryResolution resolution = entryFactory.create(uri)
        .<EntryResolution>map(EntryResolution.Known::new)
        .orElseGet(() -> new EntryResolution.Unknown(uri, "exists, but could not be classified"));
    if (resolution instanceof EntryResolution.Known known) {
      tracked.put(uri, known.entry());
    }
    return resolution;
  }

  /** Removes {@code uri} from the tracked set, if present. */
  public void forget(final EntryUri uri) {
    tracked.remove(uri);
  }

  /** Every currently-tracked entry, in no particular order. */
  public Collection<StorageEntry> entries() {
    return List.copyOf(tracked.values());
  }

  /**
   * The SURFACE-mode rebuild: discovers every entry matching {@code target} via {@link
   * StorageEngine#list} (discovery only, no content load) and replaces the tracked set with what
   * it found. Aborts — leaving the tracked set exactly as it was before the call, untouched — if
   * discovery grows past {@code surfaceRebuildLimit} rather than continuing indefinitely.
   */
  public SurfaceRebuildResult rebuildSurface(final IndexingTarget target) {
    final Map<EntryUri, StorageEntry> discovered = new LinkedHashMap<>();
    try (Stream<StorageEngine.Listed> listed = engine.list(target)) {
      final Iterator<StorageEngine.Listed> it = listed.iterator();
      while (it.hasNext()) {
        if (discovered.size() >= surfaceRebuildLimit) {
          return new SurfaceRebuildResult.Aborted(
              "storage surface exceeds " + surfaceRebuildLimit + " entries; aborting rather than "
                  + "hang or exhaust memory");
        }
        final StorageEngine.Listed item = it.next();
        entryFactory.create(item.uri())
            .ifPresent(entry -> discovered.put(item.uri(), entry));
      }
    }
    tracked.clear();
    tracked.putAll(discovered);
    return new SurfaceRebuildResult.Completed(discovered.size());
  }

  @Override
  public void close() {
    loader.close();
  }
}

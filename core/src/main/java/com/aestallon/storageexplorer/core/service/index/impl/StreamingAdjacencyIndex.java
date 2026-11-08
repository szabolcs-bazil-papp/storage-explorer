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

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import com.aestallon.storageexplorer.core.model.EntryUri;
import com.aestallon.storageexplorer.core.model.entry.GodObjectEntry;
import com.aestallon.storageexplorer.core.model.entry.ObjectEntry;
import com.aestallon.storageexplorer.core.model.entry.StorageEntry;
import com.aestallon.storageexplorer.core.model.entry.StorageEntryFactory;
import com.aestallon.storageexplorer.core.model.entry.UriProperty;
import com.aestallon.storageexplorer.core.model.instance.dto.StorageId;
import com.aestallon.storageexplorer.core.model.loading.IndexingTarget;
import com.aestallon.storageexplorer.core.service.engine.ContentHandle;
import com.aestallon.storageexplorer.core.service.engine.EngineResult;
import com.aestallon.storageexplorer.core.service.engine.StorageEngine;
import com.aestallon.storageexplorer.core.service.index.AdjacencyAware;
import com.aestallon.storageexplorer.core.service.index.UriLeafScanner;
import com.aestallon.storageexplorer.core.service.index.result.AdjacencyResult;
import com.aestallon.storageexplorer.core.service.index.result.EntryResolution;

/**
 * An in-memory {@link AdjacencyAware}: no persistence (the disk-backed variant is Milestone 9's
 * hand-rolled flat-file format, not this one). {@link #outgoingReferences} reads the entry's raw
 * bytes through the held {@link StorageEngine} and feeds them to {@link UriLeafScanner} — never
 * {@link ContentHandle#materialize()}, since this path only ever needs the bytes for scanning.
 *
 * <p><strong>Scoped-entry folding</strong>: today, a {@code ScopedEntry} child's references fold
 * into its {@code ObjectEntry} parent's own {@code uriProperties()} via {@code ObjectEntry
 * .scopedEntries}/{@code addScopedEntry}, populated by {@code IndexingStrategy
 * .associateScopedEntries} — a <em>batch-local</em> grouping over whatever entries a bulk walk
 * already turned up, matching each {@code ScopedEntry}'s {@code scope()} (its owner's URI) to an
 * {@code ObjectEntry} host's own URI. There is no such batch here (this index answers one
 * entry's adjacency at a time), so the matching candidate set is found instead with a narrowed
 * {@link StorageEngine#list} call — scoped to the owner's own scheme <em>and</em> its exact type
 * folder (the URI's first path segment, used verbatim as the type filter so it can only match
 * that one folder) — then filtered to the entries whose address actually sits under the owner's
 * own path behind one of the four stored-collection markers. Faithful to today's actual scope:
 * only {@code ObjectEntry} hosts fold children (excluding {@code GodObjectEntry}, exactly like
 * {@code associateScopedEntries} itself) — {@code ListEntry}/{@code MapEntry} have no scoped-
 * entry tracking of their own today either, despite looser wording elsewhere.
 */
public final class StreamingAdjacencyIndex implements AdjacencyAware {

  private static final List<String> SCOPE_MARKERS = List.of(
      StorageEntryFactory.STORED_LIST_MARKER,
      StorageEntryFactory.STORED_MAP_MARKER,
      StorageEntryFactory.STORED_REF_MARKER,
      StorageEntryFactory.STORED_SEQ_MARKER);

  private final StorageId storageId;
  private final StorageEngine engine;
  private final StorageEntryFactory entryFactory;

  public StreamingAdjacencyIndex(final StorageId storageId, final StorageEngine engine,
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
  public AdjacencyResult outgoingReferences(final StorageEntry entry) {
    final EntryUri uri = entry.uri();
    final Set<UriProperty> direct = new HashSet<>();
    String readError = null;
    // BinaryData's payload is base64-inlined JSON that can be arbitrarily large (the platform's
    // generic "attach a file" mechanism) - never worth scanning for URI leaves, and never worth
    // the read at all, so this is checked before engine.read(uri) is even called:
    if (!(entry instanceof ObjectEntry oe && oe.isBinaryData())) {
      switch (engine.read(uri)) {
        case EngineResult.Ok<ContentHandle> ok -> {
          try (InputStream in = ok.value().openStream()) {
            direct.addAll(UriLeafScanner.scan(in));
          } catch (final IOException e) {
            readError = "failed to scan content: " + e.getMessage();
          }
        }
        case EngineResult.Err<ContentHandle> err -> readError = err.error().toString();
      }
    }

    // scoped children are a path association, independent of whether the owner's own content
    // is readable at all - a real fixture confirms this matters: a path can host a scoped child
    // (storedRef/...) while having no .o of its own, existing purely as that child's scope
    // prefix. Only fail outright if there's truly nothing to report either way.
    final Set<UriProperty> scoped = scopedChildReferences(entry, uri);
    if (readError != null && scoped.isEmpty()) {
      return new AdjacencyResult.Err(entry, readError);
    }
    direct.addAll(scoped);
    return new AdjacencyResult.Ok(entry, direct);
  }

  private Set<UriProperty> scopedChildReferences(final StorageEntry owner, final EntryUri ownerUri) {
    if (!(owner instanceof ObjectEntry) || owner instanceof GodObjectEntry) {
      return Set.of();
    }
    final List<String> segments = ownerUri.segments();
    if (segments.isEmpty()) {
      return Set.of();
    }
    final String ownerPath = ownerUri.toString();
    final IndexingTarget target = new IndexingTarget(Set.of(ownerUri.scheme()), Set.of(segments.get(0)));

    final Set<UriProperty> found = new HashSet<>();
    try (Stream<StorageEngine.Listed> listed = engine.list(target)) {
      for (final StorageEngine.Listed item : listed.toList()) {
        if (item.uri().equals(ownerUri)) {
          continue;
        }
        final String candidatePath = item.uri().toString();
        if (!isScopedUnder(candidatePath, ownerPath)) {
          continue;
        }
        entryFactory.create(item.uri()).ifPresent(child -> {
          final UriProperty.Segment key = (child instanceof ObjectEntry oe)
              ? UriProperty.Segment.key(oe.uuid())
              : UriProperty.Segment.key(child.uri().toString());
          // UriProperty itself is still java.net.URI-based (its own EntryUri migration is
          // explicitly deferred - see this class's own earlier T3.2 landing note); convert at
          // this one boundary rather than pulling that migration forward here.
          found.add(UriProperty.of(new UriProperty.Segment[] {key}, URI.create(child.uri().toString())));
        });
      }
    }
    return found;
  }

  private static boolean isScopedUnder(final String candidatePath, final String ownerPath) {
    return SCOPE_MARKERS.stream().anyMatch(marker -> candidatePath.startsWith(ownerPath + marker));
  }

  @Override
  public void close() {
    // nothing of its own to release - see PassthroughContentIndex's own close() note.
  }
}

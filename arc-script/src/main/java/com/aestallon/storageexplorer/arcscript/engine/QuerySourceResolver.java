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

package com.aestallon.storageexplorer.arcscript.engine;

import java.util.Optional;
import java.util.Set;
import static java.util.stream.Collectors.toSet;
import java.util.stream.Stream;
import com.aestallon.storageexplorer.arcscript.internal.query.QueryInstructionImpl;
import com.aestallon.storageexplorer.core.model.EntryUri;
import com.aestallon.storageexplorer.core.model.entry.ListEntry;
import com.aestallon.storageexplorer.core.model.entry.MapEntry;
import com.aestallon.storageexplorer.core.model.entry.ObjectEntry;
import com.aestallon.storageexplorer.core.model.entry.StorageEntry;
import com.aestallon.storageexplorer.core.model.entry.UriProperty;
import com.aestallon.storageexplorer.core.model.instance.StorageInstance;
import com.aestallon.storageexplorer.core.model.loading.IndexingTarget;
import com.aestallon.storageexplorer.core.service.engine.LoadedContent;
import com.aestallon.storageexplorer.core.service.index.result.AdjacencyResult;
import com.aestallon.storageexplorer.core.service.index.result.ContentLoadResult;
import com.aestallon.storageexplorer.core.service.index.result.EntryResolution;
import com.aestallon.storageexplorer.core.util.Uris;

/**
 * Acquires the source set of a {@code query} instruction, shared by every {@link QueryEngine}
 * implementation.
 *
 * <p>
 * Type-based queries ({@code a}/{@code an}/{@code every}) draw from
 * {@link StorageInstance#query(IndexingTarget)}: a lazily populated stream of entries, discovered
 * (freshly, never cached) while the query is already consuming them. Collection-based queries
 * ({@code list}/{@code map}) instead acquire the named stored collection eagerly - failing
 * <em>before</em> any pipeline machinery spins up if it does not exist - and source the query
 * from the object entries its contained URIs point to.
 *
 * <p>
 * Callers <strong>must close</strong> the returned stream: for type-based queries it is chained to
 * live storage resources (walker threads or an open database cursor), and closing it is also what
 * cancels discovery on early termination.
 */
final class QuerySourceResolver {

  private QuerySourceResolver() {}

  static Stream<StorageEntry> resolveStream(final StorageInstance storageInstance,
                                            final QueryInstructionImpl query) {
    if (query._collectionKind == null) {
      return storageInstance.query(new IndexingTarget(query._schemas, query._types));
    }

    return resolveCollection(storageInstance, query).stream();
  }

  private static Set<StorageEntry> resolveCollection(final StorageInstance storageInstance,
                                                     final QueryInstructionImpl query) {
    if (query._schemas.size() != 1) {
      throw new IllegalArgumentException(
          "Exactly one schema must be specified for a collection query, but got: "
              + query._schemas);
    }

    final String schema = query._schemas.iterator().next();
    final String name = query._collectionName;
    final var kind = query._collectionKind;

    final StorageEntry collection = acquireCanonical(storageInstance, schema, name, kind)
        .orElse(null);
    if (collection == null || !exists(storageInstance, collection)) {
      throw new IllegalArgumentException(
          "Stored %s [ %s / %s ] does not exist in storage [ %s ]!".formatted(
              kind.name().toLowerCase(),
              schema,
              name,
              storageInstance.name()));
    }

    return outgoingReferences(storageInstance, collection)
        .map(it -> resolve(storageInstance, it))
        .flatMap(Optional::stream)
        // collections are expected to hold object entry URIs exclusively; anything else present
        // cannot participate in predicate evaluation or projection, so it is dropped:
        .filter(ObjectEntry.class::isInstance)
        .collect(toSet());
  }

  private static Optional<StorageEntry> acquireCanonical(final StorageInstance storageInstance,
                                                         final String schema,
                                                         final String name,
                                                         final QueryInstructionImpl.CollectionSourceKind kind) {
    // global stored collections live at a well-known location - single-versioned, in the
    // schema's dedicated collection scheme:
    final var uri = switch (kind) {
      case LIST -> Uris.constructList(schema, name);
      case MAP -> Uris.constructMap(schema, name);
    };
    return EntryUri.parse(uri.toString())
        .map(storageInstance::resolve)
        .flatMap(res -> switch (res) {
          case EntryResolution.Known(var entry) -> Optional.of(entry);
          case EntryResolution.Unknown u -> Optional.<StorageEntry>empty();
        })
        .filter(it -> switch (kind) {
          case LIST -> it instanceof ListEntry;
          case MAP -> it instanceof MapEntry;
        });
  }

  /** Whether {@code collection} actually carries stored content - a missing/placeholder
   *  collection loads as empty content, while a real one always carries at least its "uris"
   *  property. Replaces the old {@code StorageEntry#valid()}/{@code asSingleVersion()} check
   *  (removed in Milestone 4, T4.1) with a direct content load. */
  private static boolean exists(final StorageInstance storageInstance, final StorageEntry collection) {
    return switch (storageInstance.read(collection)) {
      case ContentLoadResult.Ok ok ->
          ok.content().materialize() instanceof LoadedContent.JsonObject(var data) && !data.isEmpty();
      case ContentLoadResult.Err err -> false;
    };
  }

  /** The URIs a stored collection's content points to - replaces the old {@code
   *  StorageEntry#uriProperties()} (removed in T4.1) with the same adjacency scan every other
   *  Milestone 3 index already uses for graph traversal; a stored collection's content is simply
   *  scanned for its embedded URI leaves ({@code "uris": [...]}), same as any other entry's. */
  private static Stream<java.net.URI> outgoingReferences(final StorageInstance storageInstance,
                                                          final StorageEntry collection) {
    return switch (storageInstance.newGraphIndex().outgoingReferences(collection)) {
      case AdjacencyResult.Ok(var entry, var references) -> references.stream().map(UriProperty::uri);
      case AdjacencyResult.Err err -> Stream.empty();
    };
  }

  private static Optional<StorageEntry> resolve(final StorageInstance storageInstance,
                                                 final java.net.URI uri) {
    return EntryUri.parse(uri.toString())
        .map(storageInstance::resolve)
        .flatMap(res -> switch (res) {
          case EntryResolution.Known(var entry) -> Optional.of(entry);
          case EntryResolution.Unknown u -> Optional.<StorageEntry>empty();
        });
  }

}

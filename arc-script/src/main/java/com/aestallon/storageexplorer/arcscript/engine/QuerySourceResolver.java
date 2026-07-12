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
import com.aestallon.storageexplorer.arcscript.internal.query.QueryInstructionImpl;
import com.aestallon.storageexplorer.core.model.entry.ListEntry;
import com.aestallon.storageexplorer.core.model.entry.MapEntry;
import com.aestallon.storageexplorer.core.model.entry.ObjectEntry;
import com.aestallon.storageexplorer.core.model.entry.ScopedEntry;
import com.aestallon.storageexplorer.core.model.entry.StorageEntry;
import com.aestallon.storageexplorer.core.model.instance.StorageInstance;
import com.aestallon.storageexplorer.core.model.loading.IndexingTarget;
import com.aestallon.storageexplorer.core.model.loading.ObjectEntryLoadResult;
import com.aestallon.storageexplorer.core.util.Uris;

/**
 * Acquires the source set of a {@code query} instruction, shared by every {@link QueryEngine}
 * implementation.
 *
 * <p>
 * Type-based queries ({@code a}/{@code an}/{@code every}) draw from the storage index, which the
 * engine-level implicit {@code index} instruction has already fully populated by the time a query
 * engine runs. Collection-based queries ({@code list}/{@code map}) instead acquire the named stored
 * collection directly - failing if it does not exist - and source the query from the object entries
 * its contained URIs point to; no schema-wide indexing is required for them.
 */
final class QuerySourceResolver {

  private QuerySourceResolver() {}

  static Set<StorageEntry> resolve(final StorageInstance storageInstance,
                                   final QueryInstructionImpl query) {
    if (query._collectionKind == null) {
      return storageInstance.index().get(new IndexingTarget(query._schemas, query._types));
    }

    return resolveCollection(storageInstance, query);
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

    final StorageEntry collection = findInIndex(storageInstance, schema, name, kind)
        .or(() -> acquireCanonical(storageInstance, schema, name, kind))
        .orElse(null);
    if (collection == null || !exists(collection)) {
      throw new IllegalArgumentException(
          "Stored %s [ %s / %s ] does not exist in storage [ %s ]!".formatted(
              kind.name().toLowerCase(),
              schema,
              name,
              storageInstance.name()));
    }

    return collection.uriProperties().stream()
        .map(it -> it.uri)
        .map(storageInstance::discover)
        .flatMap(Optional::stream)
        // collections are expected to hold object entry URIs exclusively; anything else present
        // cannot participate in predicate evaluation or projection, so it is dropped:
        .filter(ObjectEntry.class::isInstance)
        .collect(toSet());
  }

  private static Optional<StorageEntry> findInIndex(final StorageInstance storageInstance,
                                                    final String schema,
                                                    final String name,
                                                    final QueryInstructionImpl.CollectionSourceKind kind) {
    return storageInstance.index().entities()
        .filter(it -> !(it instanceof ScopedEntry))
        .filter(it -> switch (kind) {
          case LIST -> it instanceof ListEntry l
              && schema.equals(l.schema())
              && name.equals(l.name());
          case MAP -> it instanceof MapEntry m
              && schema.equals(m.schema())
              && name.equals(m.name());
        })
        .findFirst();
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
    return storageInstance.softDiscover(uri)
        .filter(it -> switch (kind) {
          case LIST -> it instanceof ListEntry;
          case MAP -> it instanceof MapEntry;
        });
  }

  private static boolean exists(final StorageEntry collection) {
    if (collection.valid()) {
      // already refreshed from actual storage content:
      return true;
    }

    final Optional<? extends ObjectEntryLoadResult.SingleVersion> version = switch (collection) {
      case ListEntry l -> l.asSingleVersion();
      case MapEntry m -> m.asSingleVersion();
      default -> Optional.empty();
    };
    // a missing single-version entry "loads" as a placeholder with empty content, while a real
    // stored collection always carries at least its "uris" property:
    return version
        .map(it -> !it.objectAsMap().isEmpty())
        .orElse(false);
  }

}

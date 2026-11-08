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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import com.aestallon.storageexplorer.core.model.entry.StorageEntry;
import com.aestallon.storageexplorer.core.model.entry.UriProperty;
import com.aestallon.storageexplorer.core.service.StorageInstanceExaminer;
import com.aestallon.storageexplorer.core.service.engine.EntryMetadata;
import com.aestallon.storageexplorer.core.service.index.CellValue;
import com.aestallon.storageexplorer.core.service.index.ColumnId;
import com.aestallon.storageexplorer.core.service.index.MetadataAware;
import com.aestallon.storageexplorer.core.service.index.PropertyPath;
import com.aestallon.storageexplorer.core.service.index.Projection;
import com.aestallon.storageexplorer.core.service.index.QueryableIndex;
import com.aestallon.storageexplorer.core.service.index.RowId;
import com.aestallon.storageexplorer.core.service.index.result.MetadataResult;

/**
 * The real, production-backed {@link Projection}: cells resolve through {@link
 * StorageInstanceExaminer#discoverProperty}, not a hand-rolled path walk - Milestone 8's (T8.1)
 * resolution of the sketch's "known-shaky" question of whether {@link CellValue} needed to grow a
 * sealed {@code Scalar}/{@code Reference} split.
 *
 * <p><strong>T8.1's finding</strong>: it didn't, but not for a shallow reason. {@code
 * discoverProperty}'s real algorithm is considerably richer than {@link InMemoryProjection}'s own
 * (deliberately simplified) path walk - it merges inline nested-object navigation with
 * {@code UriProperty}-based reference crossing, and supports flattening across a referenced list
 * ({@code a.bList.c} meaning "every element of a.bList, then c"), which ArcScript's {@code any}/
 * {@code all}/{@code none} list-element matching depends on. That richness is baked directly into
 * the DSL's compiled predicates ({@code PropertyPredicate} pattern-matches on {@code
 * StorageInstanceExaminer.PropertyDiscoveryResult} subtypes), so reshaping it into a
 * list-aware {@code CellValue} would mean rewriting ArcScript's predicate compiler - wildly out of
 * proportion to "wire real drop-row/drop-column behavior." Instead, this class keeps {@code
 * PropertyDiscoveryResult} as the real per-cell currency (cached by {@link #discover}, the
 * additional accessor beyond {@link Projection}'s own interface that condition evaluation needs),
 * and only flattens to a plain {@link CellValue.Scalar} for callers that just want a display
 * value (columns in {@code show}/{@code order}).
 *
 * <p>Thread-safe: every stage of the pipelined query engine resolves cells from separate virtual
 * threads sharing one {@code Projection} per execution. Column/row bookkeeping lives in {@code
 * ConcurrentHashMap}s; the per-cell discovery itself resolves outside any map-internal monitor
 * (same discipline as {@link StorageInstanceExaminer.ObjectEntryLookupTable}, for the same
 * reason - it's blocking I/O under the hood).
 */
public final class ExaminerBackedProjection implements Projection {

  private sealed interface ColumnSpec {
    record Path(String dotted) implements ColumnSpec {}

    record Metadata(MetadataField field) implements ColumnSpec {}
  }

  private record CellKey(RowId row, ColumnId column) {}

  private final StorageInstanceExaminer examiner;
  private final QueryableIndex index;
  private final StorageInstanceExaminer.ObjectEntryLookupTable cache;
  private final MetadataAware metadataIndex;

  private final AtomicLong nextRowId = new AtomicLong();
  private final AtomicLong nextColumnId = new AtomicLong();

  private final ConcurrentMap<RowId, StorageEntry> rowSeeds = new ConcurrentHashMap<>();
  private final ConcurrentMap<Object, ColumnId> columnsBySpec = new ConcurrentHashMap<>();
  private final ConcurrentMap<ColumnId, ColumnSpec> specsByColumn = new ConcurrentHashMap<>();
  private final ConcurrentMap<CellKey, MemoizedDiscovery> discovered = new ConcurrentHashMap<>();
  private final ConcurrentMap<CellKey, CellValue> metadataCells = new ConcurrentHashMap<>();

  public ExaminerBackedProjection(final StorageInstanceExaminer examiner,
                                  final QueryableIndex index,
                                  final StorageInstanceExaminer.ObjectEntryLookupTable cache,
                                  final MetadataAware metadataIndex) {
    this.examiner = Objects.requireNonNull(examiner, "examiner cannot be null!");
    this.index = Objects.requireNonNull(index, "index cannot be null!");
    this.cache = Objects.requireNonNull(cache, "cache cannot be null!");
    this.metadataIndex = Objects.requireNonNull(metadataIndex, "metadataIndex cannot be null!");
  }

  @Override
  public RowId addRow(final StorageEntry entry) {
    final RowId row = new RowId(nextRowId.getAndIncrement());
    rowSeeds.put(row, entry);
    return row;
  }

  @Override
  public void dropRow(final RowId row) {
    rowSeeds.remove(row);
    discovered.keySet().removeIf(k -> k.row().equals(row));
    metadataCells.keySet().removeIf(k -> k.row().equals(row));
  }

  @Override
  public ColumnId column(final PropertyPath path) {
    final String dotted = UriProperty.Segment.asString(path.segments());
    return columnsBySpec.computeIfAbsent(dotted, p -> {
      final ColumnId id = new ColumnId(nextColumnId.getAndIncrement());
      specsByColumn.put(id, new ColumnSpec.Path(dotted));
      return id;
    });
  }

  @Override
  public ColumnId metadataColumn(final MetadataField field) {
    return columnsBySpec.computeIfAbsent(field, f -> {
      final ColumnId id = new ColumnId(nextColumnId.getAndIncrement());
      specsByColumn.put(id, new ColumnSpec.Metadata(field));
      return id;
    });
  }

  @Override
  public void dropColumn(final ColumnId column) {
    final ColumnSpec spec = specsByColumn.remove(column);
    if (spec instanceof ColumnSpec.Path(String dotted)) {
      columnsBySpec.remove(dotted);
    } else if (spec instanceof ColumnSpec.Metadata(MetadataField field)) {
      columnsBySpec.remove(field);
    }
    discovered.keySet().removeIf(k -> k.column().equals(column));
    metadataCells.keySet().removeIf(k -> k.column().equals(column));
  }

  @Override
  public CellValue get(final RowId row, final ColumnId column) {
    final ColumnSpec spec = specsByColumn.get(column);
    return switch (spec) {
      case null -> CellValue.MISSING;
      case ColumnSpec.Path path -> toCellValue(discoverFor(row, column, path));
      case ColumnSpec.Metadata metadata -> resolveMetadataCell(row, column, metadata.field());
    };
  }

  @Override
  public StorageEntry provenanceOf(final RowId row, final ColumnId column) {
    final ColumnSpec spec = specsByColumn.get(column);
    if (spec instanceof ColumnSpec.Path path) {
      return switch (discoverFor(row, column, path)) {
        case StorageInstanceExaminer.Some some -> some.host();
        case StorageInstanceExaminer.None ignored -> rowSeeds.get(row);
        case null -> rowSeeds.get(row);
      };
    }

    // metadata columns never cross a reference - the entry they describe is always the seed:
    return rowSeeds.get(row);
  }

  /** Beyond {@link Projection}'s own interface: the full {@link
   *  StorageInstanceExaminer.PropertyDiscoveryResult} a {@code Path} column's cell resolved to,
   *  which condition evaluation needs (list-flattening, {@code any}/{@code all}/{@code none}
   *  matching) and a flattened {@link CellValue} cannot carry. Callers that only want a display
   *  value should use {@link #get} instead. */
  public StorageInstanceExaminer.PropertyDiscoveryResult discover(final RowId row,
                                                                   final ColumnId column) {
    final ColumnSpec spec = specsByColumn.get(column);
    if (!(spec instanceof ColumnSpec.Path path)) {
      return new StorageInstanceExaminer.NotFound(
          "column [ " + column + " ] is not a property-path column");
    }

    return discoverFor(row, column, path);
  }

  private StorageInstanceExaminer.PropertyDiscoveryResult discoverFor(final RowId row,
                                                                       final ColumnId column,
                                                                       final ColumnSpec.Path path) {
    return discovered
        .computeIfAbsent(new CellKey(row, column), k -> new MemoizedDiscovery())
        .resolve(row, path);
  }

  private CellValue resolveMetadataCell(final RowId row, final ColumnId column,
                                        final MetadataField field) {
    return metadataCells.computeIfAbsent(new CellKey(row, column), k -> {
      final StorageEntry seed = rowSeeds.get(row);
      if (seed == null) {
        return CellValue.MISSING;
      }

      return switch (metadataIndex.metadataOf(seed)) {
        case MetadataResult.Err err -> CellValue.MISSING;
        case MetadataResult.Ok ok -> fieldOf(ok.metadata(), field);
      };
    });
  }

  private static CellValue fieldOf(final EntryMetadata metadata, final MetadataField field) {
    final Object value = switch (field) {
      case QUALIFIED_NAME -> metadata.qualifiedName();
      case VERSION_NR -> metadata.versionNr();
      case CREATED_AT -> metadata.createdAt();
      case LAST_MODIFIED -> metadata.lastModified();
      case ENTRY_ID -> metadata.entryId();
    };
    return value == null ? CellValue.MISSING : new CellValue.Scalar(value);
  }

  private static CellValue toCellValue(final StorageInstanceExaminer.PropertyDiscoveryResult result) {
    return switch (result) {
      case StorageInstanceExaminer.None ignored -> CellValue.MISSING;
      case StorageInstanceExaminer.Some some -> new CellValue.Scalar(some.val());
    };
  }

  @Override
  public void considerSpill() {
    // no-op until Milestone 9 - hint, not a command, per Projection's own javadoc.
  }

  @Override
  public void close() {
    rowSeeds.clear();
    columnsBySpec.clear();
    specsByColumn.clear();
    discovered.clear();
    metadataCells.clear();
  }

  /** Memoizes a single cell's discovery so the (potentially blocking) resolution runs under a
   *  {@link ReentrantLock} - parking, not pinning, a virtual thread - instead of inside the
   *  owning map's bin monitor. Same pattern as {@code
   *  StorageInstanceExaminer.ObjectEntryLookupTable.MemoizedLoadRequest}. */
  private final class MemoizedDiscovery {

    private final ReentrantLock lock = new ReentrantLock();
    private volatile StorageInstanceExaminer.PropertyDiscoveryResult value;

    StorageInstanceExaminer.PropertyDiscoveryResult resolve(final RowId row,
                                                             final ColumnSpec.Path path) {
      final var present = value;
      if (present != null) {
        return present;
      }

      lock.lock();
      try {
        if (value == null) {
          final StorageEntry seed = rowSeeds.get(row);
          value = (seed == null)
              ? new StorageInstanceExaminer.NotFound("row [ " + row + " ] is no longer tracked")
              : examiner.discoverProperty(seed, path.dotted(), index, cache);
        }
        return value;
      } finally {
        lock.unlock();
      }
    }
  }

}

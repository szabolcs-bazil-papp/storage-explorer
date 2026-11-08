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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import com.aestallon.storageexplorer.core.model.EntryUri;
import com.aestallon.storageexplorer.core.model.entry.StorageEntry;
import com.aestallon.storageexplorer.core.model.entry.UriProperty;
import com.aestallon.storageexplorer.core.service.index.CellValue;
import com.aestallon.storageexplorer.core.service.index.ColumnId;
import com.aestallon.storageexplorer.core.service.index.PropertyPath;
import com.aestallon.storageexplorer.core.service.index.Projection;
import com.aestallon.storageexplorer.core.service.index.RowId;

/**
 * The columnar in-memory backing for {@link InMemoryTabular}: a {@code Map<ColumnId,
 * List<CellValue>>}-shaped store would need a fixed row count up front, which rows/columns being
 * independently add/droppable rules out cleanly — used instead: row seeds and columns as their
 * own maps, cell values/provenance keyed by the (row, column) pair, resolved lazily on first
 * {@link #get}.
 *
 * <p><strong>The path-walk algorithm</strong> (the one piece of actual logic here): starting at a
 * row's seed entry, each {@link PropertyPath} segment navigates one level into the current
 * entry's content ({@link InMemoryTabular.ContentSource}). If the value found partway through the
 * path (not at the final segment) is itself an {@link EntryUri} and {@link
 * InMemoryTabular.EntryResolver} can resolve it, the walk continues the remaining segments
 * against <em>that</em> entry's own content instead — and {@link #provenanceOf} reports whichever
 * entry the walk was standing on when it produced the final value, not the row's original seed.
 * A path segment that can't be navigated (a missing key, an out-of-range index, or an
 * unresolvable reference) yields {@link CellValue#MISSING} rather than failing the whole
 * projection.
 */
final class InMemoryProjection implements Projection {

  private record CellKey(RowId row, ColumnId column) {}

  private final InMemoryTabular.ContentSource contentSource;
  private final InMemoryTabular.EntryResolver entryResolver;

  private final AtomicLong nextRowId = new AtomicLong();
  private final AtomicLong nextColumnId = new AtomicLong();

  private final Map<RowId, StorageEntry> rowSeeds = new HashMap<>();
  private final Map<PropertyPath, ColumnId> columnsByPath = new HashMap<>();
  private final Map<ColumnId, PropertyPath> pathsByColumn = new HashMap<>();
  private final Map<CellKey, CellValue> values = new HashMap<>();
  private final Map<CellKey, StorageEntry> provenance = new HashMap<>();

  InMemoryProjection(final InMemoryTabular.ContentSource contentSource,
                     final InMemoryTabular.EntryResolver entryResolver) {
    this.contentSource = contentSource;
    this.entryResolver = entryResolver;
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
    values.keySet().removeIf(key -> key.row().equals(row));
    provenance.keySet().removeIf(key -> key.row().equals(row));
  }

  @Override
  public ColumnId column(final PropertyPath path) {
    return columnsByPath.computeIfAbsent(path, p -> {
      final ColumnId id = new ColumnId(nextColumnId.getAndIncrement());
      pathsByColumn.put(id, p);
      return id;
    });
  }

  @Override
  public ColumnId metadataColumn(final MetadataField field) {
    // this fixture is deliberately decoupled from StorageEngine/MetadataAware (see class
    // javadoc) - a real metadata-backed column needs ExaminerBackedProjection instead.
    throw new UnsupportedOperationException(
        "InMemoryProjection has no MetadataAware backing - metadata columns are not supported "
        + "by this test fixture.");
  }

  @Override
  public void dropColumn(final ColumnId column) {
    final PropertyPath path = pathsByColumn.remove(column);
    if (path != null) {
      columnsByPath.remove(path);
    }
    values.keySet().removeIf(key -> key.column().equals(column));
    provenance.keySet().removeIf(key -> key.column().equals(column));
  }

  @Override
  public CellValue get(final RowId row, final ColumnId column) {
    return values.computeIfAbsent(new CellKey(row, column), key -> resolveCell(row, column));
  }

  @Override
  public StorageEntry provenanceOf(final RowId row, final ColumnId column) {
    get(row, column);
    return provenance.get(new CellKey(row, column));
  }

  @Override
  public void considerSpill() {
    // no-op until Milestone 9 - hint, not a command, per Projection's own javadoc.
  }

  @Override
  public void close() {
    rowSeeds.clear();
    columnsByPath.clear();
    pathsByColumn.clear();
    values.clear();
    provenance.clear();
  }

  private CellValue resolveCell(final RowId row, final ColumnId column) {
    final StorageEntry seed = rowSeeds.get(row);
    final PropertyPath path = pathsByColumn.get(column);
    if (seed == null || path == null) {
      return CellValue.MISSING;
    }

    StorageEntry currentEntry = seed;
    Object current = contentSource.contentOf(currentEntry);
    final List<UriProperty.Segment> segments = path.segments();
    for (int i = 0; i < segments.size(); i++) {
      current = navigate(current, segments.get(i));
      if (current == null) {
        provenance.put(new CellKey(row, column), currentEntry);
        return CellValue.MISSING;
      }
      final boolean hasMoreSegments = i < segments.size() - 1;
      if (hasMoreSegments && current instanceof EntryUri reference) {
        final Optional<StorageEntry> next = entryResolver.resolve(reference);
        if (next.isEmpty()) {
          provenance.put(new CellKey(row, column), currentEntry);
          return CellValue.MISSING;
        }
        currentEntry = next.get();
        current = contentSource.contentOf(currentEntry);
      }
    }
    provenance.put(new CellKey(row, column), currentEntry);
    return new CellValue.Scalar(current);
  }

  private static Object navigate(final Object current, final UriProperty.Segment segment) {
    return switch (segment) {
      case UriProperty.Segment.Key(String name) ->
          (current instanceof Map<?, ?> map) ? map.get(name) : null;
      case UriProperty.Segment.Idx(int index) ->
          (current instanceof List<?> list && index >= 0 && index < list.size())
              ? list.get(index)
              : null;
    };
  }
}

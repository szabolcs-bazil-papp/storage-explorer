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

import com.aestallon.storageexplorer.core.model.entry.StorageEntry;

/**
 * Rows = {@link StorageEntry} candidates. Columns = property paths ("arcs") referenced by
 * {@code where}/{@code order}/{@code yield} at the current ArcScript query phase. Every cell
 * remembers the medial {@code StorageEntry} it was reached through, because ArcScript needs to
 * continue a property path from <em>that</em> entry, not just read a terminal scalar off the
 * seed row's entry — see {@link #provenanceOf}.
 *
 * <p>One projection per query execution (or per sub-phase, if CTEs end up wanting their own) —
 * see {@link Tabular#newProjection()}.
 */
public interface Projection extends AutoCloseable {

  /** Seeds a new row for {@code entry}; returns a row handle. */
  RowId addRow(StorageEntry entry);

  void dropRow(RowId row);

  /** Get-or-create a column for a property path; columns are shared across all rows. */
  ColumnId column(PropertyPath path);

  /** A built-in, computed column resolved from an entry's header metadata (e.g. creation/
   *  modification timestamps) rather than walking its content — T8.3's wiring of {@code
   *  MetadataAware} as a selectable column kind alongside ordinary property paths. Get-or-create,
   *  same as {@link #column(PropertyPath)}. */
  ColumnId metadataColumn(MetadataField field);

  void dropColumn(ColumnId column);

  /** The computed metadata fields selectable via {@link #metadataColumn(MetadataField)}, mirroring
   *  {@link com.aestallon.storageexplorer.core.service.engine.EntryMetadata}'s own fields. */
  enum MetadataField { QUALIFIED_NAME, VERSION_NR, CREATED_AT, LAST_MODIFIED, ENTRY_ID }

  /** The value at (row, column) — may trigger the index to resolve/load the next hop the first
   *  time a given (row, column) pair is touched. */
  CellValue get(RowId row, ColumnId column);

  /** The medial entry this cell's value was reached through — what the recursive arc-walk
   *  continues from, replacing today's {@code StorageInstanceExaminer}-held discoverer chain. */
  StorageEntry provenanceOf(RowId row, ColumnId column);

  /** Hint, not a command — implementation decides whether/when to actually spill. Called by the
   *  query engine between phases once it has a size estimate. A no-op until Milestone 9. */
  void considerSpill();

  @Override
  void close();
}

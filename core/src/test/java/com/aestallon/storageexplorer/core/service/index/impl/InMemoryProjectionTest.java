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

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.aestallon.storageexplorer.core.model.EntryUri;
import com.aestallon.storageexplorer.core.model.entry.StorageEntry;
import com.aestallon.storageexplorer.core.model.entry.StorageEntryFactory;
import com.aestallon.storageexplorer.core.model.entry.UriProperty;
import com.aestallon.storageexplorer.core.model.instance.dto.StorageId;
import com.aestallon.storageexplorer.core.service.index.CellValue;
import com.aestallon.storageexplorer.core.service.index.ColumnId;
import com.aestallon.storageexplorer.core.service.index.PropertyPath;
import com.aestallon.storageexplorer.core.service.index.Projection;
import com.aestallon.storageexplorer.core.service.index.RowId;

/**
 * {@link InMemoryProjection}/{@link InMemoryTabular} against a small synthetic entry graph — two
 * real (fixture-backed) {@link StorageEntry} identities standing in for "A" and "B", with made-up
 * content wired through {@link InMemoryTabular.ContentSource}, exactly as T3.5 asks for: proving
 * the shape is usable, not exercising real storage content.
 */
class InMemoryProjectionTest {

  private static final Path FIXTURES = Path.of("..", "..", "sb4a-fs", "crates", "sb4a-fs", "tests",
      "fixtures", "dev-fs");

  private static final String A_URI = "org:/org_smartbit4all_api_storage_bean_StorageSettings/settings";
  private static final String B_URI =
      "account-ticket:/hu_it4all_eji_fiok_domain_model_AccountTicket/2025/12/19/16/18/"
          + "5baf7e92-4abe-414b-81dd-e8018f1a99c5";

  // one shared StorageId + factory so every entry constructed in a test compares equal to
  // itself regardless of how many times it's (re)built - StorageId(null) mints a fresh random
  // UUID per call otherwise, which would make separately-constructed "the same" entries unequal.
  private static final StorageId STORAGE_ID = new StorageId(null);

  private static StorageEntryFactory entryFactory() {
    return new StorageEntryFactory(STORAGE_ID);
  }

  private static StorageEntry entry(final String uri) {
    return entryFactory().create(EntryUri.parse(uri).orElseThrow()).orElseThrow();
  }

  private static final StorageEntry A = entry(A_URI);
  private static final StorageEntry B = entry(B_URI);

  /** entry A's content references B via an {@link EntryUri} value (not a URI-shaped string —
   *  this milestone's resolver detects references by type, not by parsing strings; see {@link
   *  InMemoryProjection}'s own javadoc on that deliberate simplification). entry B's content has
   *  a plain scalar "name" field. */
  private static InMemoryTabular tabular() {
    final EntryUri bUri = EntryUri.parse(B_URI).orElseThrow();

    final Map<StorageEntry, Map<String, Object>> content = new HashMap<>();
    content.put(A, Map.of("owner", bUri, "label", "entry A"));
    content.put(B, Map.of("name", "entry B's name"));

    final Map<EntryUri, StorageEntry> byUri = Map.of(
        EntryUri.parse(A_URI).orElseThrow(), A,
        bUri, B);

    return new InMemoryTabular(
        STORAGE_ID,
        content::get,
        (EntryUri u) -> Optional.ofNullable(byUri.get(u)));
  }

  private static StorageEntry a() {
    return A;
  }

  @Test
  @DisplayName("provenance crosses a reference: a two-hop path reports B, not A")
  void provenanceCrossesAReference() {
    try (Projection projection = tabular().newProjection()) {
      final RowId row = projection.addRow(a());
      final ColumnId ownerName = projection.column(PropertyPath.of(
          UriProperty.Segment.key("owner"), UriProperty.Segment.key("name")));

      final CellValue value = projection.get(row, ownerName);
      assertThat(value).isEqualTo(new CellValue.Scalar("entry B's name"));

      final StorageEntry provenance = projection.provenanceOf(row, ownerName);
      assertThat(provenance.uri().toString()).isEqualTo(B_URI);
    }
  }

  @Test
  @DisplayName("a single-hop path reports the seed entry as its own provenance")
  void singleHopPathReportsTheSeedEntry() {
    try (Projection projection = tabular().newProjection()) {
      final RowId row = projection.addRow(a());
      final ColumnId label = projection.column(PropertyPath.of(UriProperty.Segment.key("label")));

      assertThat(projection.get(row, label)).isEqualTo(new CellValue.Scalar("entry A"));
      assertThat(projection.provenanceOf(row, label).uri().toString()).isEqualTo(A_URI);
    }
  }

  @Test
  @DisplayName("a path that doesn't resolve yields MISSING, not an exception")
  void unresolvablePathYieldsMissing() {
    try (Projection projection = tabular().newProjection()) {
      final RowId row = projection.addRow(a());
      final ColumnId missing = projection.column(PropertyPath.of(UriProperty.Segment.key("nope")));
      assertThat(projection.get(row, missing)).isEqualTo(CellValue.MISSING);
    }
  }

  @Test
  @DisplayName("columns are shared across rows for the same path")
  void columnsAreSharedAcrossRowsForTheSamePath() {
    try (Projection projection = tabular().newProjection()) {
      final ColumnId c1 = projection.column(PropertyPath.of(UriProperty.Segment.key("label")));
      final ColumnId c2 = projection.column(PropertyPath.of(UriProperty.Segment.key("label")));
      assertThat(c1).isEqualTo(c2);
    }
  }

  @Test
  @DisplayName("dropRow removes its cells; a later get on the same row re-seeds nothing and reports MISSING")
  void dropRowRemovesItsCells() {
    try (Projection projection = tabular().newProjection()) {
      final RowId row = projection.addRow(a());
      final ColumnId label = projection.column(PropertyPath.of(UriProperty.Segment.key("label")));
      assertThat(projection.get(row, label)).isEqualTo(new CellValue.Scalar("entry A"));

      projection.dropRow(row);
      assertThat(projection.get(row, label)).isEqualTo(CellValue.MISSING);
    }
  }

  @Test
  @DisplayName("dropColumn removes its cells across every row")
  void dropColumnRemovesItsCellsAcrossEveryRow() {
    try (Projection projection = tabular().newProjection()) {
      final RowId row = projection.addRow(a());
      final ColumnId label = projection.column(PropertyPath.of(UriProperty.Segment.key("label")));
      assertThat(projection.get(row, label)).isEqualTo(new CellValue.Scalar("entry A"));

      projection.dropColumn(label);
      // a fresh column() call for the same path must mint a new id (the old one is gone), and
      // that new column starts with no cached value for the row:
      final ColumnId relabeled = projection.column(PropertyPath.of(UriProperty.Segment.key("label")));
      assertThat(relabeled).isNotEqualTo(label);
    }
  }
}

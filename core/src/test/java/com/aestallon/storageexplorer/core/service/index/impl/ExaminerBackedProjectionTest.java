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
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.aestallon.storageexplorer.core.model.EntryUri;
import com.aestallon.storageexplorer.core.model.entry.StorageEntry;
import com.aestallon.storageexplorer.core.model.entry.StorageEntryFactory;
import com.aestallon.storageexplorer.core.model.entry.UriProperty;
import com.aestallon.storageexplorer.core.model.instance.dto.StorageId;
import com.aestallon.storageexplorer.core.service.StorageInstanceExaminer;
import com.aestallon.storageexplorer.core.service.engine.EngineResult;
import com.aestallon.storageexplorer.core.service.fs.FsStorageEngine;
import com.aestallon.storageexplorer.core.service.index.CellValue;
import com.aestallon.storageexplorer.core.service.index.ColumnId;
import com.aestallon.storageexplorer.core.service.index.Projection;
import com.aestallon.storageexplorer.core.service.index.PropertyPath;
import com.aestallon.storageexplorer.core.service.index.RowId;

/**
 * {@link ExaminerBackedProjection} against the real fixture corpus - the production-backed
 * counterpart of {@link InMemoryProjectionTest}, proving {@code Projection} can be driven by
 * {@code StorageInstanceExaminer#discoverProperty} itself (T8.1), including the metadata-column
 * capability (T8.3).
 */
class ExaminerBackedProjectionTest {

  private static final Path FIXTURES = Path.of("..", "..", "sb4a-fs", "crates", "sb4a-fs", "tests",
      "fixtures", "dev-fs");

  private static final String SETTINGS = "org:/org_smartbit4all_api_storage_bean_StorageSettings/settings";

  private static final StorageId STORAGE_ID = new StorageId(null);

  private static StorageEntryFactory entryFactory() {
    return new StorageEntryFactory(STORAGE_ID);
  }

  private static StorageEntry entry(final String uri) {
    return entryFactory().create(EntryUri.parse(uri).orElseThrow()).orElseThrow();
  }

  private static ExaminerBackedProjection projection() {
    final var engine = ((EngineResult.Ok<FsStorageEngine>) FsStorageEngine.open(FIXTURES))
        .value()
        .asStorageEngine();
    final var index = new CompositeQueryableIndex(STORAGE_ID, engine, entryFactory());
    final var metadataIndex = new HeaderMetadataIndex(STORAGE_ID, engine, entryFactory());
    return new ExaminerBackedProjection(
        new StorageInstanceExaminer(),
        index,
        StorageInstanceExaminer.ObjectEntryLookupTable.newInstance(),
        metadataIndex);
  }

  @Test
  @DisplayName("a property-path column resolves through the real examiner")
  void propertyPathColumnResolvesThroughTheRealExaminer() {
    final ExaminerBackedProjection projection = projection();
    final RowId row = projection.addRow(entry(SETTINGS));
    final ColumnId uri = projection.column(PropertyPath.of(UriProperty.Segment.key("uri")));

    assertThat(projection.get(row, uri)).isEqualTo(new CellValue.Scalar(SETTINGS));
  }

  @Test
  @DisplayName("an unresolvable path yields MISSING, not an exception")
  void unresolvablePathYieldsMissing() {
    final ExaminerBackedProjection projection = projection();
    final RowId row = projection.addRow(entry(SETTINGS));
    final ColumnId nope = projection.column(PropertyPath.of(UriProperty.Segment.key("nope")));

    assertThat(projection.get(row, nope)).isEqualTo(CellValue.MISSING);
  }

  @Test
  @DisplayName("discover exposes the raw PropertyDiscoveryResult a flattened CellValue can't carry")
  void discoverExposesTheRawDiscoveryResult() {
    final ExaminerBackedProjection projection = projection();
    final RowId row = projection.addRow(entry(SETTINGS));
    final ColumnId uri = projection.column(PropertyPath.of(UriProperty.Segment.key("uri")));

    assertThat(projection.discover(row, uri)).isInstanceOf(StorageInstanceExaminer.StringFound.class);
  }

  @Test
  @DisplayName("a metadata column resolves creation/version info without walking the body")
  void metadataColumnResolvesHeaderFields() {
    final ExaminerBackedProjection projection = projection();
    final RowId row = projection.addRow(entry(SETTINGS));
    final ColumnId versionNr = projection.metadataColumn(Projection.MetadataField.VERSION_NR);
    final ColumnId createdAt = projection.metadataColumn(Projection.MetadataField.CREATED_AT);

    assertThat(projection.get(row, versionNr)).isEqualTo(new CellValue.Scalar(2L));
    assertThat(projection.get(row, createdAt)).isNotEqualTo(CellValue.MISSING);
  }

  @Test
  @DisplayName("metadataColumn is get-or-create, same as column")
  void metadataColumnIsSharedAcrossCalls() {
    final ExaminerBackedProjection projection = projection();
    final ColumnId c1 = projection.metadataColumn(Projection.MetadataField.CREATED_AT);
    final ColumnId c2 = projection.metadataColumn(Projection.MetadataField.CREATED_AT);
    assertThat(c1).isEqualTo(c2);
  }

  @Test
  @DisplayName("dropRow removes its cells; a later get on the same row reports MISSING")
  void dropRowRemovesItsCells() {
    final ExaminerBackedProjection projection = projection();
    final RowId row = projection.addRow(entry(SETTINGS));
    final ColumnId uri = projection.column(PropertyPath.of(UriProperty.Segment.key("uri")));
    assertThat(projection.get(row, uri)).isEqualTo(new CellValue.Scalar(SETTINGS));

    projection.dropRow(row);
    assertThat(projection.get(row, uri)).isEqualTo(CellValue.MISSING);
  }

  @Test
  @DisplayName("dropColumn removes its cells across every row and frees the path for reuse")
  void dropColumnRemovesItsCells() {
    final ExaminerBackedProjection projection = projection();
    final RowId row = projection.addRow(entry(SETTINGS));
    final ColumnId uri = projection.column(PropertyPath.of(UriProperty.Segment.key("uri")));
    assertThat(projection.get(row, uri)).isEqualTo(new CellValue.Scalar(SETTINGS));

    projection.dropColumn(uri);
    final ColumnId relabeled = projection.column(PropertyPath.of(UriProperty.Segment.key("uri")));
    assertThat(relabeled).isNotEqualTo(uri);
  }
}

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

package com.aestallon.storageexplorer.core.service;

import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.aestallon.storageexplorer.core.model.EntryUri;
import com.aestallon.storageexplorer.core.model.entry.ObjectEntry;
import com.aestallon.storageexplorer.core.model.entry.StorageEntry;
import com.aestallon.storageexplorer.core.model.entry.StorageEntryFactory;
import com.aestallon.storageexplorer.core.model.instance.dto.StorageId;
import com.aestallon.storageexplorer.core.service.engine.EngineResult;
import com.aestallon.storageexplorer.core.service.fs.FsStorageEngine;
import com.aestallon.storageexplorer.core.service.index.QueryableIndex;
import com.aestallon.storageexplorer.core.service.index.impl.CompositeQueryableIndex;

/**
 * Focused unit test for the one piece of {@link StorageInstanceExaminer} this is adding to: the
 * {@code BinaryDataObject} refusal in {@code discoverProperty}. The rest of the class's behavior
 * is already exercised thoroughly through {@code arc-script}'s own suites
 * ({@code AssertionOperationTest}/{@code PipelinedQueryEngineTest}/...).
 */
class StorageInstanceExaminerTest {

  private static final Path FIXTURES = Path.of("..", "..", "sb4a-fs", "crates", "sb4a-fs", "tests",
      "fixtures", "dev-fs");

  private static final StorageId STORAGE_ID = new StorageId(null);

  private static StorageEntryFactory entryFactory() {
    return new StorageEntryFactory(STORAGE_ID);
  }

  private static QueryableIndex index() {
    final var engine = ((EngineResult.Ok<FsStorageEngine>) FsStorageEngine.open(FIXTURES))
        .value()
        .asStorageEngine();
    return new CompositeQueryableIndex(STORAGE_ID, engine, entryFactory());
  }

  private static StorageEntry entry(final String uri) {
    return entryFactory().create(EntryUri.parse(uri).orElseThrow()).orElseThrow();
  }

  @Test
  @DisplayName("discoverProperty refuses to load a BinaryData entry's content")
  void discoverPropertyRefusesBinaryData() {
    // doesn't need to exist on disk - the refusal is a zero-I/O URI check that must happen before
    // any attempt to load the entry, so a nonexistent address still produces a clean refusal
    // rather than a NotFound-from-missing-file:
    final StorageEntry binaryData = entry(
        "org:/org_smartbit4all_api_binarydata_BinaryDataObject/does-not-need-to-exist");
    assertThat(((ObjectEntry) binaryData).isBinaryData()).isTrue();

    final var examiner = new StorageInstanceExaminer();
    final var cache = StorageInstanceExaminer.ObjectEntryLookupTable.newInstance();
    try (QueryableIndex index = index()) {
      final var result = examiner.discoverProperty(binaryData, "anyProperty", index, cache);
      assertThat(result).isInstanceOf(StorageInstanceExaminer.NotFound.class);
      assertThat(((StorageInstanceExaminer.NotFound) result).errMsg()).contains("BinaryData");
    }
  }
}

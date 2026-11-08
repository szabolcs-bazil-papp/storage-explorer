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
import java.nio.file.Path;
import java.util.Set;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.aestallon.storageexplorer.core.model.EntryUri;
import com.aestallon.storageexplorer.core.model.entry.ObjectEntry;
import com.aestallon.storageexplorer.core.model.entry.StorageEntry;
import com.aestallon.storageexplorer.core.model.entry.StorageEntryFactory;
import com.aestallon.storageexplorer.core.model.entry.UriProperty;
import com.aestallon.storageexplorer.core.model.instance.dto.StorageId;
import com.aestallon.storageexplorer.core.service.engine.ContentHandle;
import com.aestallon.storageexplorer.core.service.engine.EngineResult;
import com.aestallon.storageexplorer.core.service.engine.StorageEngine;
import com.aestallon.storageexplorer.core.service.fs.FsStorageEngine;
import com.aestallon.storageexplorer.core.service.index.UriLeafScanner;
import com.aestallon.storageexplorer.core.service.index.result.AdjacencyResult;

/** {@link StreamingAdjacencyIndex} against the real fixture corpus. */
class StreamingAdjacencyIndexTest {

  private static final Path FIXTURES = Path.of("..", "..", "sb4a-fs", "crates", "sb4a-fs", "tests",
      "fixtures", "dev-fs");

  private static final String SETTINGS = "org:/org_smartbit4all_api_storage_bean_StorageSettings/settings";
  private static final String DOCUMENT_FILE =
      "documentdossier-collections:/org_smartbit4all_documentdossier_domain_DocumentFile/2026/10/15/21/26/"
          + "08476c48-f129-49d7-a84b-ebac11af8d8c";
  private static final String DOCUMENT_FILE_SCOPED_CHILD = DOCUMENT_FILE + "/storedRef/"
      + "DOCUMENT_STORAGE_ITEM_HISTORY_REF-s";

  private static StorageEntryFactory entryFactory() {
    return new StorageEntryFactory(new StorageId(null));
  }

  private static StorageEngine engine() {
    return ((EngineResult.Ok<FsStorageEngine>) FsStorageEngine.open(FIXTURES)).value().asStorageEngine();
  }

  private static StreamingAdjacencyIndex index() {
    return new StreamingAdjacencyIndex(new StorageId(null), engine(), entryFactory());
  }

  private static StorageEntry entry(final String uri) {
    return entryFactory().create(EntryUri.parse(uri).orElseThrow()).orElseThrow();
  }

  @Test
  @DisplayName("outgoingReferences agrees with UriLeafScanner's own direct scan, for an entry with no scoped children")
  void agreesWithUriLeafScannerForAPlainEntry() throws IOException {
    final StorageEngine engine = engine();
    final StorageEntry owner = entry(SETTINGS);

    final Set<UriProperty> direct;
    final ContentHandle handle = ((EngineResult.Ok<ContentHandle>) engine.read(
        com.aestallon.storageexplorer.core.model.EntryUri.parse(SETTINGS).orElseThrow())).value();
    try (InputStream in = handle.openStream()) {
      direct = UriLeafScanner.scan(in);
    }

    final AdjacencyResult result = index().outgoingReferences(owner);
    assertThat(result).isInstanceOf(AdjacencyResult.Ok.class);
    final AdjacencyResult.Ok ok = (AdjacencyResult.Ok) result;
    // settings has no scoped children, so wiring must not have added or dropped anything
    assertThat(ok.references()).isEqualTo(direct);
  }

  @Test
  @DisplayName("a scoped child nested under its owner folds into the owner's references")
  void scopedChildFoldsIntoTheOwnersReferences() {
    final StorageEntry owner = entry(DOCUMENT_FILE);
    assertThat(owner).isInstanceOf(ObjectEntry.class);

    final AdjacencyResult result = index().outgoingReferences(owner);
    assertThat(result).isInstanceOf(AdjacencyResult.Ok.class);
    final Set<UriProperty> references = ((AdjacencyResult.Ok) result).references();

    assertThat(references)
        .extracting(p -> p.uri().toString())
        .contains(DOCUMENT_FILE_SCOPED_CHILD);
  }

  @Test
  @DisplayName("querying the scoped child itself succeeds and never reports itself as a reference")
  void queryingTheScopedChildItselfSucceeds() {
    final StorageEntry scoped = entry(DOCUMENT_FILE_SCOPED_CHILD);
    final AdjacencyResult result = index().outgoingReferences(scoped);
    assertThat(result).isInstanceOf(AdjacencyResult.Ok.class);
    assertThat(((AdjacencyResult.Ok) result).references())
        .extracting(p -> p.uri().toString())
        .doesNotContain(DOCUMENT_FILE_SCOPED_CHILD);
  }

  @Test
  @DisplayName("a BinaryData entry's content is never read for adjacency scanning")
  void binaryDataContentIsNeverReadForAdjacencyScanning() {
    // doesn't need to exist on disk - isBinaryData() is a zero-I/O URI check, and that's exactly
    // the point: outgoingReferences must never even attempt engine.read(uri) for this entry, so a
    // nonexistent address still succeeds with an empty result instead of failing:
    final StorageEntry binaryData = entry(
        "org:/org_smartbit4all_api_binarydata_BinaryDataObject/does-not-need-to-exist");
    assertThat(binaryData).isInstanceOf(ObjectEntry.class);
    assertThat(((ObjectEntry) binaryData).isBinaryData()).isTrue();

    final AdjacencyResult result = index().outgoingReferences(binaryData);
    assertThat(result).isInstanceOf(AdjacencyResult.Ok.class);
    assertThat(((AdjacencyResult.Ok) result).references()).isEmpty();
  }
}

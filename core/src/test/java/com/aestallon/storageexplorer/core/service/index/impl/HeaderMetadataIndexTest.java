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
import com.aestallon.storageexplorer.core.model.entry.StorageEntry;
import com.aestallon.storageexplorer.core.model.entry.StorageEntryFactory;
import com.aestallon.storageexplorer.core.model.instance.dto.StorageId;
import com.aestallon.storageexplorer.core.service.engine.EngineResult;
import com.aestallon.storageexplorer.core.service.engine.EntryMetadata;
import com.aestallon.storageexplorer.core.service.engine.StorageEngine;
import com.aestallon.storageexplorer.core.service.fs.FsStorageEngine;
import com.aestallon.storageexplorer.core.service.index.result.MetadataResult;

/** {@link HeaderMetadataIndex} against the real fixture corpus. */
class HeaderMetadataIndexTest {

  private static final Path FIXTURES = Path.of("..", "..", "sb4a-fs", "crates", "sb4a-fs", "tests",
      "fixtures", "dev-fs");

  private static final String SETTINGS = "org:/org_smartbit4all_api_storage_bean_StorageSettings/settings";

  private static StorageEntryFactory entryFactory() {
    return new StorageEntryFactory(new StorageId(null));
  }

  private static HeaderMetadataIndex index() {
    final StorageEngine engine = ((EngineResult.Ok<FsStorageEngine>) FsStorageEngine.open(FIXTURES))
        .value()
        .asStorageEngine();
    return new HeaderMetadataIndex(new StorageId(null), engine, entryFactory());
  }

  private static StorageEntry entry(final String uri) {
    return entryFactory().create(com.aestallon.storageexplorer.core.model.EntryUri.parse(uri).orElseThrow())
        .orElseThrow();
  }

  @Test
  @DisplayName("metadataOf reads header fields without parsing the body")
  void metadataOfReadsHeaderFieldsWithoutParsingTheBody() {
    final MetadataResult result = index().metadataOf(entry(SETTINGS));
    assertThat(result).isInstanceOf(MetadataResult.Ok.class);
    final EntryMetadata metadata = ((MetadataResult.Ok) result).metadata();
    assertThat(metadata.qualifiedName()).isEqualTo("org.smartbit4all.api.storage.bean.StorageSettings");
    assertThat(metadata.versionNr()).isEqualTo(2L);
    assertThat(metadata.createdAt()).isNotNull();
  }

  @Test
  @DisplayName("metadataOf of a classifiable-but-missing entry reports Err, not a crash")
  void metadataOfAMissingEntryReportsErr() {
    final StorageEntry missing = entry("org:/nothing/here");
    assertThat(index().metadataOf(missing)).isInstanceOf(MetadataResult.Err.class);
  }

  @Test
  @DisplayName("resolve finds and classifies a real entry")
  void resolveFindsARealEntry() {
    final var resolution = index().resolve(
        com.aestallon.storageexplorer.core.model.EntryUri.parse(SETTINGS).orElseThrow());
    assertThat(resolution).isInstanceOf(
        com.aestallon.storageexplorer.core.service.index.result.EntryResolution.Known.class);
  }
}

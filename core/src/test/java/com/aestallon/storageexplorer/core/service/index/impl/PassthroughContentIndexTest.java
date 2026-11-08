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
import com.aestallon.storageexplorer.core.service.engine.LoadedContent;
import com.aestallon.storageexplorer.core.service.engine.StorageEngine;
import com.aestallon.storageexplorer.core.service.fs.FsStorageEngine;
import com.aestallon.storageexplorer.core.service.index.result.ContentLoadResult;
import com.aestallon.storageexplorer.core.service.index.result.EntryResolution;

/**
 * {@link PassthroughContentIndex} against the real fixture corpus. {@link StorageEntry}
 * construction still goes through the pre-Milestone-4 {@link StorageEntryFactory}/{@link
 * FileSystemStorageIndex} pair (the old index only used here to get a {@link StorageEntryFactory}
 * instance — never to load or refresh anything through it); the class under test depends only on
 * the factory, never the old index class itself. See {@link PassthroughContentIndex}'s own
 * javadoc for why.
 */
class PassthroughContentIndexTest {

  private static final Path FIXTURES = Path.of("..", "..", "sb4a-fs", "crates", "sb4a-fs", "tests",
      "fixtures", "dev-fs");

  private static final String SETTINGS = "org:/org_smartbit4all_api_storage_bean_StorageSettings/settings";

  private static StorageEntryFactory entryFactory() {
    return new StorageEntryFactory(new StorageId(null));
  }

  private static PassthroughContentIndex index() {
    final StorageId storageId = new StorageId(null);
    final StorageEngine engine = ((EngineResult.Ok<FsStorageEngine>) FsStorageEngine.open(FIXTURES))
        .value()
        .asStorageEngine();
    return new PassthroughContentIndex(storageId, engine, entryFactory());
  }

  private static EntryUri uri(final String text) {
    return EntryUri.parse(text).orElseThrow();
  }

  @Test
  @DisplayName("resolve finds a real entry and classifies it correctly")
  void resolveFindsARealEntry() {
    final PassthroughContentIndex index = index();
    final EntryResolution resolution = index.resolve(uri(SETTINGS));
    assertThat(resolution).isInstanceOf(EntryResolution.Known.class);
    final StorageEntry entry = ((EntryResolution.Known) resolution).entry();
    assertThat(entry).isInstanceOf(ObjectEntry.class);
    assertThat(entry.uri().toString()).isEqualTo(SETTINGS);
  }

  @Test
  @DisplayName("resolve reports an unknown address as Unknown, not an exception")
  void resolveReportsUnknownAddress() {
    final PassthroughContentIndex index = index();
    final EntryResolution resolution = index.resolve(uri("org:/nothing/here"));
    assertThat(resolution).isInstanceOf(EntryResolution.Unknown.class);
  }

  @Test
  @DisplayName("load reads straight through the engine, with no cache of its own")
  void loadReadsStraightThroughTheEngine() throws IOException {
    final PassthroughContentIndex index = index();
    final StorageEntry entry = ((EntryResolution.Known) index.resolve(uri(SETTINGS))).entry();

    final ContentLoadResult result = index.load(entry);
    assertThat(result).isInstanceOf(ContentLoadResult.Ok.class);
    final ContentLoadResult.Ok ok = (ContentLoadResult.Ok) result;
    assertThat(ok.entry()).isSameAs(entry);

    final LoadedContent content = ok.content().materialize();
    assertThat(content).isInstanceOf(LoadedContent.JsonObject.class);
    assertThat(((LoadedContent.JsonObject) content).data()).containsKey("uri");
  }

  @Test
  @DisplayName("load of a missing entry reports Err, not an exception")
  void loadOfAMissingEntryReportsErr() {
    final PassthroughContentIndex index = index();
    // an entry the factory can classify from the URI shape alone, even though no such file
    // exists - load() must still fail cleanly rather than throw (resolve() would never hand
    // back a Known for this address in the first place, so this entry is built directly).
    final StorageEntry missing = entryFactory().create(EntryUri.parse("org:/nothing/here").orElseThrow())
        .orElseThrow();
    assertThat(index.load(missing)).isInstanceOf(ContentLoadResult.Err.class);
  }
}

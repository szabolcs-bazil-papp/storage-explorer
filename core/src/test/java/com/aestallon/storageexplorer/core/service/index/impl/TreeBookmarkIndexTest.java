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
import java.util.Set;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.aestallon.storageexplorer.core.model.EntryUri;
import com.aestallon.storageexplorer.core.model.entry.StorageEntryFactory;
import com.aestallon.storageexplorer.core.model.instance.dto.StorageId;
import com.aestallon.storageexplorer.core.model.loading.IndexingTarget;
import com.aestallon.storageexplorer.core.service.engine.EngineResult;
import com.aestallon.storageexplorer.core.service.engine.StorageEngine;
import com.aestallon.storageexplorer.core.service.fs.FsStorageEngine;
import com.aestallon.storageexplorer.core.service.index.result.EntryResolution;

/** {@link TreeBookmarkIndex} against the real fixture corpus. */
class TreeBookmarkIndexTest {

  private static final Path FIXTURES = Path.of("..", "..", "sb4a-fs", "crates", "sb4a-fs", "tests",
      "fixtures", "dev-fs");

  private static final String SETTINGS = "org:/org_smartbit4all_api_storage_bean_StorageSettings/settings";

  private static StorageEntryFactory entryFactory() {
    return new StorageEntryFactory(new StorageId(null));
  }

  private static StorageEngine engine() {
    return ((EngineResult.Ok<FsStorageEngine>) FsStorageEngine.open(FIXTURES)).value().asStorageEngine();
  }

  private static TreeBookmarkIndex index() {
    return new TreeBookmarkIndex(new StorageId(null), engine(), entryFactory());
  }

  private static TreeBookmarkIndex index(final int surfaceRebuildLimit) {
    return new TreeBookmarkIndex(new StorageId(null), engine(), entryFactory(), surfaceRebuildLimit);
  }

  private static EntryUri uri(final String text) {
    return EntryUri.parse(text).orElseThrow();
  }

  @Test
  @DisplayName("resolve only reports a tracked entry, never anything merely existing")
  void resolveOnlyReportsATrackedEntry() {
    final TreeBookmarkIndex index = index();
    assertThat(index.resolve(uri(SETTINGS))).isInstanceOf(EntryResolution.Unknown.class);

    final EntryResolution tracked = index.track(uri(SETTINGS));
    assertThat(tracked).isInstanceOf(EntryResolution.Known.class);
    assertThat(index.resolve(uri(SETTINGS))).isInstanceOf(EntryResolution.Known.class);
    assertThat(index.entries()).hasSize(1);
  }

  @Test
  @DisplayName("forget removes a tracked entry")
  void forgetRemovesATrackedEntry() {
    final TreeBookmarkIndex index = index();
    index.track(uri(SETTINGS));
    assertThat(index.entries()).hasSize(1);

    index.forget(uri(SETTINGS));
    assertThat(index.entries()).isEmpty();
    assertThat(index.resolve(uri(SETTINGS))).isInstanceOf(EntryResolution.Unknown.class);
  }

  @Test
  @DisplayName("track of a missing address reports Unknown and tracks nothing")
  void trackOfAMissingAddressReportsUnknown() {
    final TreeBookmarkIndex index = index();
    assertThat(index.track(uri("org:/nothing/here"))).isInstanceOf(EntryResolution.Unknown.class);
    assertThat(index.entries()).isEmpty();
  }

  @Test
  @DisplayName("load delegates through to the engine, same as PassthroughContentIndex")
  void loadDelegatesThroughToTheEngine() {
    final TreeBookmarkIndex index = index();
    final var entry = ((EntryResolution.Known) index.track(uri(SETTINGS))).entry();
    assertThat(index.load(entry)).isInstanceOf(
        com.aestallon.storageexplorer.core.service.index.result.ContentLoadResult.Ok.class);
  }

  @Test
  @DisplayName("rebuildSurface discovers entries and replaces the tracked set")
  void rebuildSurfaceDiscoversEntriesAndReplacesTheTrackedSet() {
    final TreeBookmarkIndex index = index();
    // a pre-existing, unrelated bookmark (a different scheme entirely) must not survive a
    // surface rebuild that doesn't rediscover it ("repopulate", not "merge"):
    index.track(uri(
        "account-ticket:/hu_it4all_eji_fiok_domain_model_AccountTicket/2025/12/19/16/18/"
            + "5baf7e92-4abe-414b-81dd-e8018f1a99c5"));
    assertThat(index.entries()).hasSize(1);

    final TreeBookmarkIndex.SurfaceRebuildResult result = index.rebuildSurface(
        new IndexingTarget(Set.of("org"), Set.of()));
    assertThat(result).isInstanceOf(TreeBookmarkIndex.SurfaceRebuildResult.Completed.class);
    assertThat(((TreeBookmarkIndex.SurfaceRebuildResult.Completed) result).count()).isEqualTo(1);
    assertThat(index.entries()).hasSize(1);
    assertThat(index.resolve(uri(SETTINGS))).isInstanceOf(EntryResolution.Known.class);
  }

  @Test
  @DisplayName("rebuildSurface aborts cleanly, leaving the tracked set untouched, once past the size limit")
  void rebuildSurfaceAbortsPastTheSizeLimit() {
    final TreeBookmarkIndex index = index(1);
    index.track(uri(SETTINGS));
    assertThat(index.entries()).hasSize(1);

    // the "org" scheme alone has exactly one entry in the fixture corpus, so scanning "org" with
    // a limit of 1 does not trip it; widen to every scheme to guarantee more than 1 candidate:
    final TreeBookmarkIndex.SurfaceRebuildResult result = index.rebuildSurface(IndexingTarget.any());
    assertThat(result).isInstanceOf(TreeBookmarkIndex.SurfaceRebuildResult.Aborted.class);
    // the previously-tracked entry must survive an aborted rebuild untouched:
    assertThat(index.entries()).hasSize(1);
    assertThat(index.resolve(uri(SETTINGS))).isInstanceOf(EntryResolution.Known.class);
  }
}

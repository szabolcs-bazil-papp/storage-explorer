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

package com.aestallon.storageexplorer.core.service.fs;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.aestallon.storageexplorer.core.model.EntryUri;
import com.aestallon.storageexplorer.core.model.loading.IndexingTarget;
import com.aestallon.storageexplorer.core.service.engine.EngineResult;

/**
 * {@link FsStorageEngine#list} against the real fixture corpus — mirrors the gist of
 * {@code list.rs}'s own test suite (exact counts/kinds asserted there), adapted to this engine's
 * {@link FileSystemStorageWalker}-backed, kind-tagged listing (T1.8).
 */
class FsStorageEngineListTest {

  private static final Path FIXTURES = Path.of("..", "..", "sb4a-fs", "crates", "sb4a-fs", "tests",
      "fixtures", "dev-fs");

  private static FsStorageEngine fixtures() {
    final EngineResult<FsStorageEngine> result = FsStorageEngine.open(FIXTURES);
    if (result instanceof EngineResult.Ok<FsStorageEngine> ok) {
      return ok.value();
    }
    throw new AssertionError("expected Ok, got " + result);
  }

  private static List<FsStorageEngine.Listed> list(final FsStorageEngine st, final IndexingTarget target) {
    try (Stream<FsStorageEngine.Listed> s = st.list(target)) {
      return s.toList();
    }
  }

  @Test
  @DisplayName("lists a scheme and its one object")
  void listsASchemeAndItsOneObject() {
    final FsStorageEngine st = fixtures();
    final List<FsStorageEngine.Listed> listed = list(st, new IndexingTarget(Set.of("org"), Set.of()));
    assertThat(listed).hasSize(1);
    assertThat(listed.get(0).kind()).isEqualTo(FileSystemStorageWalker.Kind.OBJECT);
    assertThat(listed.get(0).uri())
        .isEqualTo(EntryUri.parse("org:/org_smartbit4all_api_storage_bean_StorageSettings/settings")
            .orElseThrow());
  }

  @Test
  @DisplayName("table data is discovered with the table-data kind, version files are not objects")
  void tableDataIsDiscoveredWithTheTableDataKind() {
    final FsStorageEngine st = fixtures();
    final List<FsStorageEngine.Listed> listed = list(st,
        new IndexingTarget(Set.of("tabledatacontents"), Set.of()));
    assertThat(listed).hasSize(1);
    assertThat(listed.get(0).kind()).isEqualTo(FileSystemStorageWalker.Kind.TABLE_DATA);
    assertThat(listed.get(0).uri().toString())
        .isEqualTo("tabledatacontents:/2025/12/19/16/10/43/51fa609a-9a85-40ea-abf3-32d9333bc5d6");
  }

  @Test
  @DisplayName("a type filter narrows to the matching folder, by suffix")
  void aTypeFilterNarrowsToTheMatchingFolder() {
    final FsStorageEngine st = fixtures();
    final List<FsStorageEngine.Listed> listed = list(st,
        new IndexingTarget(Set.of("aclSubjectModel"), Set.of("SubjectSelection")));
    assertThat(listed).hasSize(1);
    assertThat(listed.get(0).uri().toString()).isEqualTo(
        "aclSubjectModel:/org_smartbit4all_api_org_bean_SubjectSelection/2026/9/2/13/43/"
            + "45ad7bb3-618b-4460-9299-7c84816bea5c");
  }

  @Test
  @DisplayName("nested objects are discovered too, with no depth limit by default")
  void nestedObjectsAreDiscoveredToo() {
    final FsStorageEngine st = fixtures();
    final List<FsStorageEngine.Listed> listed = list(st,
        new IndexingTarget(Set.of("documentdossier-collections"), Set.of()));
    final String nested = "documentdossier-collections:/org_smartbit4all_documentdossier_domain_DocumentFile/"
        + "2026/10/15/21/26/08476c48-f129-49d7-a84b-ebac11af8d8c/storedRef/DOCUMENT_STORAGE_ITEM_HISTORY_REF-s";
    // the nested object itself, plus its own object stream - both now visible, since a folder
    // that already yielded an entry is still read in full (T1.8's unconditional-recursion fix):
    assertThat(listed).hasSize(2);
    assertThat(listed).anySatisfy(l -> {
      assertThat(l.kind()).isEqualTo(FileSystemStorageWalker.Kind.OBJECT);
      assertThat(l.uri().toString()).isEqualTo(nested);
    });
    assertThat(listed).anySatisfy(l -> {
      assertThat(l.kind()).isEqualTo(FileSystemStorageWalker.Kind.OBJECT_STREAM);
      assertThat(l.uri().toString()).isEqualTo(nested + "/events");
    });
  }

  @Test
  @DisplayName("an unknown scheme lists nothing, rather than erroring")
  void anUnknownSchemeListsNothing() {
    final FsStorageEngine st = fixtures();
    assertThat(list(st, new IndexingTarget(Set.of("nothing"), Set.of()))).isEmpty();
  }
}

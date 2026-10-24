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

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.aestallon.storageexplorer.common.util.IO;
import com.aestallon.storageexplorer.core.model.entry.ObjectEntry;
import com.aestallon.storageexplorer.core.model.entry.ScopedEntry;
import com.aestallon.storageexplorer.core.model.entry.StorageEntry;
import com.aestallon.storageexplorer.core.model.instance.dto.StorageId;
import com.aestallon.storageexplorer.core.model.loading.IndexingTarget;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the streaming discovery path ({@link StorageIndex#find(IndexingTarget)}) against a
 * real, file-system backed storage layout on a temporary directory. Entries are never
 * <em>loaded</em> (empty {@code .o} files suffice for discovery), so no smartbit4all platform
 * context is required.
 */
class StorageIndexFindTest {

  @TempDir
  Path tempDir;

  private FileSystemStorageIndex index;

  @BeforeEach
  void setUp() {
    index = new FileSystemStorageIndex(
        new StorageId(UUID.randomUUID()),
        null,
        null,
        tempDir,
        false);
  }

  private URI touch(final String uriString) throws IOException {
    final URI uri = URI.create(uriString);
    final Path path = IO.uriToPath(tempDir, uri);
    Files.createDirectories(path.getParent());
    Files.createFile(path);
    return uri;
  }

  private List<StorageEntry> findAll(final IndexingTarget target) {
    try (final Stream<StorageEntry> s = index.find(target)) {
      return s.toList();
    }
  }

  @Test
  void findDiscoversMatchingEntries_andIndexesThemAsASideEffect() throws IOException {
    final URI foo1 = touch("test:/com_example_Foo/2026/1/1/1/1/foo-1");
    final URI foo2 = touch("test:/com_example_Foo/2026/1/1/1/2/foo-2");
    final URI bar = touch("other:/com_example_Bar/2026/1/1/1/1/bar-1");

    final var found = findAll(new IndexingTarget(Set.of("test"), Set.of()));

    assertThat(found)
        .extracting(StorageEntry::uri)
        .containsExactlyInAnyOrder(foo1, foo2);
    // side effect - every discovered entry is included in the index:
    assertThat(index.get(foo1)).isPresent();
    assertThat(index.get(foo2)).isPresent();
    assertThat(index.get(bar)).isEmpty();
  }

  @Test
  void findReturnsCanonicalInstances_repeatedFindsYieldTheSameEntries() throws IOException {
    touch("test:/com_example_Foo/2026/1/1/1/1/foo-1");

    final var target = new IndexingTarget(Set.of("test"), Set.of());
    final var first = findAll(target);
    final var second = findAll(target);

    assertThat(first).hasSize(1);
    assertThat(second).hasSize(1);
    assertThat(second.getFirst()).isSameAs(first.getFirst());
    assertThat(index.get(first.getFirst().uri())).containsSame(first.getFirst());
  }

  @Test
  void findFiltersExactly_walkerTypeOverApproximationDoesNotLeakThrough() throws IOException {
    final URI user = touch("test:/com_example_User/2026/1/1/1/1/user-1");
    // the walker matches type directories by suffix, so com_example_SampleUser is walked for
    // target type "User" - the exact predicate must filter it out of the results:
    final URI sampleUser = touch("test:/com_example_SampleUser/2026/1/1/1/1/sample-1");

    final var found = findAll(new IndexingTarget(Set.of("test"), Set.of("User")));

    assertThat(found)
        .extracting(StorageEntry::uri)
        .containsExactly(user);
    assertThat(found)
        .allSatisfy(it -> assertThat(((ObjectEntry) it).typeName()).isEqualTo("User"));
    // parity with the eager path:
    assertThat(index.get(new IndexingTarget(Set.of("test"), Set.of("User"))))
        .extracting(StorageEntry::uri)
        .doesNotContain(sampleUser);
  }

  @Test
  void findWiresScopedEntriesToTheirHosts_onStreamCompletion() throws IOException {
    // the walker only descends past .o files inside special directories (objectDefinition, apis,
    // storedSeq), so a host + scoped-collection pair is only ever batch-discovered there; the
    // collection scheme must carry a '-' suffix (ListEntry derives its logical schema from it),
    // and the host path a timestamp segment (Uris.parse rejects the scope URI otherwise):
    final URI host = touch("test-collections:/objectDefinition/2026/1/1/1/host-1");
    final URI scoped = touch("test-collections:/objectDefinition/2026/1/1/1/host-1/storedlist/items-s");

    final var found = findAll(new IndexingTarget(Set.of("test-collections"), Set.of()));


    assertThat(found)
        .extracting(StorageEntry::uri)
        .containsExactlyInAnyOrder(host, scoped);
    final ObjectEntry hostEntry = found.stream()
        .filter(it -> host.equals(it.uri()))
        .map(ObjectEntry.class::cast)
        .findFirst()
        .orElseThrow();
    assertThat(hostEntry.scopedEntries())
        .extracting(it -> ((StorageEntry) it).uri())
        .containsExactly(scoped);
    assertThat(found.stream().filter(it -> scoped.equals(it.uri())).findFirst().orElseThrow())
        .isInstanceOf(ScopedEntry.class);
  }

  @Test
  void closingWithoutDraining_releasesResources_andSubsequentFindsSucceed() throws IOException {
    for (int i = 0; i < 50; i++) {
      touch("test:/com_example_Foo/2026/1/1/1/" + i + "/foo-" + i);
    }

    final var target = new IndexingTarget(Set.of("test"), Set.of());
    try (final Stream<StorageEntry> s = index.find(target)) {
      assertThat(s.limit(3).toList()).hasSize(3);
    }

    // a fresh discovery is unaffected by the abandoned one:
    assertThat(findAll(target)).hasSize(50);
  }

  @Test
  void findOnEmptyStorage_completesEmpty() {
    assertThat(findAll(new IndexingTarget(Set.of("test"), Set.of()))).isEmpty();
    assertThat(IntStream.range(0, 3)
        .mapToObj(i -> findAll(IndexingTarget.any()))
        .flatMap(List::stream))
        .isEmpty();
  }

}

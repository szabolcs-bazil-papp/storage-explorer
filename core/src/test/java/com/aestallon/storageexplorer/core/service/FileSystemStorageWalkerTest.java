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
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.aestallon.storageexplorer.common.util.IO;
import com.aestallon.storageexplorer.core.model.loading.IndexingTarget;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/**
 * Exercises the lazily populated {@link FileSystemStorageWalker#walk(IndexingTarget)} stream:
 * discovery parity with the historical snapshot semantics, prompt availability of early elements,
 * cancellation-on-close, and the exhaustion race between the final producer and the consumer.
 */
class FileSystemStorageWalkerTest {

  @TempDir
  Path tempDir;

  private URI touch(final String uriString) throws IOException {
    final URI uri = URI.create(uriString);
    final Path path = IO.uriToPath(tempDir, uri);
    Files.createDirectories(path.getParent());
    Files.createFile(path);
    return uri;
  }

  private FileSystemStorageWalker walker() {
    return FileSystemStorageWalker.of(tempDir);
  }

  private List<URI> walkAll(final IndexingTarget target) {
    try (final Stream<URI> s = walker().walk(target)) {
      return s.toList();
    }
  }

  @Test
  void walkDiscoversTheFullLandscape_matchingSnapshotSemantics() throws IOException {
    final URI foo1 = touch("test:/com_example_Foo/2026/1/1/1/1/foo-1");
    final URI foo2 = touch("test:/com_example_Foo/2026/1/2/3/4/foo-2");
    final URI bar = touch("test:/com_example_Bar/2026/1/1/1/1/bar-1");
    final URI other = touch("other:/com_example_Baz/2026/1/1/1/1/baz-1");
    // apis schema: root-level .o files are emitted, and .o files nested under type dirs too:
    final URI apiRoot = touch("apis:/rootApi-s");
    final URI apiNested = touch("apis:/com_example_SomeApi/2026/1/1/1/1/api-1");
    // special dirs are descended past .o files:
    final URI seq = touch("test:/storedSeq/counter-s");
    final URI seqNested = touch("test:/storedSeq/counter-s/deeper/other-s");
    // NON-special dirs are not descended once .o files are found - this must stay invisible:
    touch("test:/com_example_Foo/2026/1/1/1/1/foo-1/storedlist/items-s");

    assertThat(walkAll(IndexingTarget.any()))
        .containsExactlyInAnyOrder(foo1, foo2, bar, other, apiRoot, apiNested, seq, seqNested);
  }

  @Test
  void walkFiltersBySchemaAndTypeDirectories() throws IOException {
    final URI foo = touch("test:/com_example_Foo/2026/1/1/1/1/foo-1");
    final URI sampleFoo = touch("test:/com_example_SampleFoo/2026/1/1/1/1/sample-1");
    touch("test:/com_example_Bar/2026/1/1/1/1/bar-1");
    touch("other:/com_example_Foo/2026/1/1/1/1/foo-other");

    // schema filtering is exact:
    assertThat(walkAll(new IndexingTarget(Set.of("test"), Set.of("Foo"))))
        // type-directory filtering over-approximates by suffix - documented behaviour, corrected
        // by the exact predicate in StorageIndex.find/get:
        .containsExactlyInAnyOrder(foo, sampleFoo);
  }

  @Test
  void firstElementIsAvailable_withoutDrainingTheWholeWalk() throws IOException {
    for (int i = 0; i < 500; i++) {
      touch("test:/com_example_Foo/2026/1/1/1/" + i + "/foo-" + i);
    }

    final URI first = assertTimeoutPreemptively(Duration.ofSeconds(10L), () -> {
      try (final Stream<URI> s = walker().walk(IndexingTarget.any())) {
        return s.findFirst().orElseThrow();
      }
    });
    assertThat(first.getScheme()).isEqualTo("test");
  }

  @Test
  void closingAPartiallyConsumedWalk_terminatesPromptly_andWalksRemainReusable()
      throws IOException {
    for (int i = 0; i < 500; i++) {
      touch("test:/com_example_Foo/2026/1/1/1/" + i + "/foo-" + i);
    }

    assertTimeoutPreemptively(Duration.ofSeconds(10L), () -> {
      try (final Stream<URI> s = walker().walk(IndexingTarget.any())) {
        assertThat(s.limit(3).toList()).hasSize(3);
      } // close() cancels the traversal and joins the coordinator
    });

    // a subsequent, fully drained walk over the same storage is unaffected:
    assertThat(walkAll(IndexingTarget.any())).hasSize(500);
  }

  @Test
  void exhaustionRace_tinyTreeInATightLoop_neverHangsNorDropsTheElement() throws IOException {
    final URI only = touch("test:/com_example_Foo/2026/1/1/1/1/foo-1");

    assertTimeoutPreemptively(Duration.ofSeconds(30L), () -> {
      for (int i = 0; i < 50; i++) {
        assertThat(walkAll(IndexingTarget.any())).containsExactly(only);
      }
    });
  }

  @Test
  void walkOnEmptyStorage_completesEmpty() {
    assertThat(walkAll(IndexingTarget.any())).isEmpty();
  }

}

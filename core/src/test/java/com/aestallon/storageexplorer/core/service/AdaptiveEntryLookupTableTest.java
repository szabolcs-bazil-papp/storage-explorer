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

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.aestallon.storageexplorer.core.model.entry.ObjectEntry;
import com.aestallon.storageexplorer.core.model.instance.dto.StorageId;
import com.aestallon.storageexplorer.core.model.loading.ObjectEntryLoadRequest;
import com.aestallon.storageexplorer.core.model.loading.ObjectEntryLoadResult;
import static org.assertj.core.api.Assertions.assertThat;

class AdaptiveEntryLookupTableTest {

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

  private ObjectEntry mint(final int i) {
    final var uri = URI.create("test:/com_example_Foo/2026/1/1/1/1/" + i);
    final var result = index.getOrCreate(uri);
    assertThat(result).isInstanceOf(StorageIndex.EntryAcquisitionResult.New.class);
    return (ObjectEntry) ((StorageIndex.EntryAcquisitionResult.New) result).entry();
  }

  private static ObjectEntryLoadRequest dummyLoad() {
    return new ObjectEntryLoadRequest.FileSystemObjectEntryLoadRequest(
        ObjectEntryLoadResult.err("test"));
  }

  @Test
  void repeatedAccessOfTheSameKey_invokesTheMappingFunctionOnlyOnce() {
    final var cache = StorageInstanceExaminer.ObjectEntryLookupTable
        .adaptive(4L, Duration.ofMinutes(1L));
    final var entry = mint(0);
    final var invocations = new AtomicLong();

    final var first = cache.computeIfAbsent(entry, e -> {
      invocations.incrementAndGet();
      return dummyLoad();
    });
    final var second = cache.computeIfAbsent(entry, e -> {
      invocations.incrementAndGet();
      return dummyLoad();
    });

    assertThat(invocations).hasValue(1L);
    assertThat(second).isSameAs(first);
  }

  @Test
  void insertionsBeyondTheBound_evictInsteadOfGrowing() {
    final var cache = (AdaptiveEntryLookupTable) StorageInstanceExaminer.ObjectEntryLookupTable
        .adaptive(4L, Duration.ofMinutes(1L));

    final List<ObjectEntry> entries = new ArrayList<>();
    for (int i = 0; i < 100; i++) {
      entries.add(mint(i));
    }
    for (final var entry : entries) {
      cache.computeIfAbsent(entry, e -> dummyLoad());
    }

    assertThat(cache.estimatedSize()).isLessThanOrEqualTo(4L);
  }

  @Test
  void evictedKeys_areReloadedOnNextAccess() {
    final var cache = StorageInstanceExaminer.ObjectEntryLookupTable
        .adaptive(4L, Duration.ofMinutes(1L));
    final var invocations = new AtomicLong();

    final List<ObjectEntry> entries = new ArrayList<>();
    for (int i = 0; i < 100; i++) {
      entries.add(mint(i));
    }
    // two full passes over 100 keys against a 4-entry cache: the second pass must re-invoke the
    // mapping function for (at least some of the) evicted keys - and correctness must not depend
    // on which keys survived:
    for (final var entry : entries) {
      cache.computeIfAbsent(entry, e -> {
        invocations.incrementAndGet();
        return dummyLoad();
      });
    }
    for (final var entry : entries) {
      assertThat(cache.computeIfAbsent(entry, e -> {
        invocations.incrementAndGet();
        return dummyLoad();
      })).isNotNull();
    }

    assertThat(invocations.get()).isGreaterThan(100L);
  }

}

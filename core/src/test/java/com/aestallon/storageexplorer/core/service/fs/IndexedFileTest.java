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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.aestallon.storageexplorer.core.service.engine.EngineResult;

/**
 * Mirrors {@code sb4a-fs}'s own {@code indexed} module test suite
 * ({@code ../sb4a-fs/crates/sb4a-fs/src/indexed.rs}).
 */
class IndexedFileTest {

  private static final Path FIXTURES = Path.of("..", "..", "sb4a-fs", "crates", "sb4a-fs", "tests",
      "fixtures", "dev-fs");

  private static <T> T ok(final EngineResult<T> result) {
    if (result instanceof EngineResult.Ok<T> ok) {
      return ok.value();
    }
    throw new AssertionError("expected Ok, got " + result);
  }

  private static byte[] b(final String s) {
    return s.getBytes(StandardCharsets.UTF_8);
  }

  @Test
  @DisplayName("A new file has an empty index of 0xFF")
  void newFileHasAnEmptyIndexOfFf(@TempDir final Path dir) throws IOException {
    final Path path = dir.resolve("a/00.iv");
    final IndexedFile f = ok(IndexedFile.openOrCreate(path, 256));
    assertThat(ok(f.entries())).isEmpty();
    assertThat(ok(f.readSlot(0))).isEmpty();
    f.close();

    final byte[] bytes = Files.readAllBytes(path);
    assertThat(bytes).hasSize(4096);
    for (final byte value : bytes) {
      assertThat(value).isEqualTo((byte) 0xFF);
    }
  }

  @Test
  @DisplayName("A missing file opens as empty")
  void missingFileOpensAsNone(@TempDir final Path dir) {
    assertThat(ok(IndexedFile.open(dir.resolve("00.iv"), 256))).isEmpty();
  }

  @Test
  @DisplayName("Write, read, overwrite")
  void writeReadOverwrite(@TempDir final Path dir) throws IOException {
    final Path path = dir.resolve("00.iv");
    final IndexedFile f = ok(IndexedFile.openOrCreate(path, 256));

    assertThat(f.writeSlot(0, List.of(b("{\"v\":0}"), b("payload zero")))).isInstanceOf(EngineResult.Ok.class);
    assertThat(f.writeSlot(255, List.of(b("last"), new byte[0]))).isInstanceOf(EngineResult.Ok.class);
    final byte[] bigPart = new byte[100_000];
    java.util.Arrays.fill(bigPart, (byte) 7);
    assertThat(f.writeSlot(7, List.of(bigPart, b("x")))).isInstanceOf(EngineResult.Ok.class);
    assertThat(ok(f.deadSpace())).isEqualTo(0L);

    assertThat(ok(f.readSlot(0)).orElseThrow().get(0)).isEqualTo(b("{\"v\":0}"));
    assertThat(ok(f.readSlot(0)).orElseThrow().get(1)).isEqualTo(b("payload zero"));
    assertThat(ok(f.readSlot(255)).orElseThrow().get(0)).isEqualTo(b("last"));
    assertThat(ok(f.readSlot(255)).orElseThrow().get(1)).isEmpty();
    assertThat(ok(f.readSlot(7)).orElseThrow().get(0)).isEqualTo(bigPart);
    assertThat(ok(f.readSlot(1))).isEmpty();

    final List<Integer> present = ok(f.entries()).stream().map(SlotEntry::slot).toList();
    assertThat(present).containsExactly(0, 7, 255);

    // overwrite: the old segment stays as dead space, the other slots are untouched
    assertThat(f.writeSlot(0, List.of(b("{\"v\":0,\"amended\":true}"), b("new"))))
        .isInstanceOf(EngineResult.Ok.class);
    assertThat(ok(f.readSlot(0)).orElseThrow().get(1)).isEqualTo(b("new"));
    assertThat(ok(f.readSlot(255)).orElseThrow().get(0)).isEqualTo(b("last"));
    assertThat(ok(f.deadSpace())).isGreaterThan(0L);

    f.close();

    // reopen read-only: same content
    final IndexedFile reopened = ok(IndexedFile.open(path, 256)).orElseThrow();
    assertThat(ok(reopened.readSlot(0)).orElseThrow().get(1)).isEqualTo(b("new"));
    reopened.close();
  }

  @Test
  @DisplayName("Rejects bad calls and bad files")
  void rejectsBadCallsAndBadFiles(@TempDir final Path dir) throws IOException {
    final Path path = dir.resolve("00.iv");
    final IndexedFile f = ok(IndexedFile.openOrCreate(path, 4));
    assertThat(f.readSlot(4)).isInstanceOf(EngineResult.Err.class);
    assertThat(f.writeSlot(4, List.of(b("x")))).isInstanceOf(EngineResult.Err.class);
    assertThat(f.writeSlot(0, List.of())).isInstanceOf(EngineResult.Err.class);
    assertThat(IndexedFile.openOrCreate(dir.resolve("zero"), 0)).isInstanceOf(EngineResult.Err.class);
    f.close();

    // opened with more slots than the file has: the index region does not fit
    assertThat(IndexedFile.open(path, 256)).isInstanceOf(EngineResult.Err.class);

    // an index entry that points outside of the file
    final byte[] bytes = Files.readAllBytes(path);
    ByteBuffer.wrap(bytes).putLong(0, 1000L).putLong(8, 50L);
    Files.write(path, bytes);
    final IndexedFile reopened = ok(IndexedFile.open(path, 4)).orElseThrow();
    assertThat(reopened.readSlot(0)).isInstanceOf(EngineResult.Err.class);
    reopened.close();

    // a segment that is not deflate data
    final byte[] bad = new byte[64 + 8 + 5];
    java.util.Arrays.fill(bad, 0, 64, (byte) 0xFF);
    ByteBuffer.wrap(bad).putLong(64, 5L);
    bad[72] = (byte) 0xFF;
    bad[73] = (byte) 0xFF;
    bad[74] = (byte) 0xFF;
    bad[75] = (byte) 0xFF;
    bad[76] = (byte) 0xFF;
    ByteBuffer.wrap(bad).putLong(0, 64L).putLong(8, 13L);
    Files.write(path, bad);
    final IndexedFile reopened2 = ok(IndexedFile.open(path, 4)).orElseThrow();
    assertThat(reopened2.readSlot(0)).isInstanceOf(EngineResult.Err.class);
    reopened2.close();
  }

  @Test
  @DisplayName("Reads the platform-written single-slot file")
  void readsThePlatformWrittenSingleSlotFile() throws IOException {
    final Path path = FIXTURES.resolve(Path.of("apis", "hu", "it4all", "customerportal", "api",
        "CustomerPortalInternalApi", "hu.it4all.customerportal.api.CustomerPortalInternalApi", "00.iv"));
    final IndexedFile f = ok(IndexedFile.open(path, 256)).orElseThrow();
    final List<SlotEntry> entries = ok(f.entries());
    assertThat(entries).hasSize(1);
    assertThat(entries.get(0)).isEqualTo(new SlotEntry(0, 4096, 0x3ed));
    assertThat(ok(f.deadSpace())).isEqualTo(0L);

    final List<byte[]> parts = ok(f.readSlot(0)).orElseThrow();
    assertThat(parts).hasSize(2);
    assertThat(new String(parts.get(0), StandardCharsets.UTF_8)).startsWith("{\"serialNoData\":0,");
    assertThat(new String(parts.get(1), StandardCharsets.UTF_8)).startsWith("{");
    assertThat(ok(f.readSlot(1))).isEmpty();
    f.close();
  }

  @Test
  @DisplayName("Reads a platform-written multi-slot file and rewrites it losslessly")
  void readsAPlatformWrittenMultiSlotFileAndRewritesItLosslessly(@TempDir final Path dir) throws IOException {
    final Path path = FIXTURES.resolve(Path.of("aclSubjectModel", "org_smartbit4all_api_org_bean_SubjectSelection",
        "2026", "9", "2", "13", "43", "45ad7bb3-618b-4460-9299-7c84816bea5c", "00.iv"));
    final IndexedFile f = ok(IndexedFile.open(path, 256)).orElseThrow();
    final List<Integer> slots = ok(f.entries()).stream().map(SlotEntry::slot).toList();
    assertThat(slots).containsExactly(0, 1, 2, 3);
    assertThat(ok(f.deadSpace())).isEqualTo(0L);

    // work on a copy: write every slot back, in reverse order, and compare the parts
    final Path copy = dir.resolve("00.iv");
    Files.copy(path, copy);
    final IndexedFile copyFile = ok(IndexedFile.openOrCreate(copy, 256));
    for (int slot = 3; slot >= 0; slot--) {
      final List<byte[]> parts = ok(f.readSlot(slot)).orElseThrow();
      assertThat(new String(parts.get(0), StandardCharsets.UTF_8))
          .startsWith("{\"serialNoData\":" + slot + ",");
      assertThat(copyFile.writeSlot(slot, parts)).isInstanceOf(EngineResult.Ok.class);
      assertThat(ok(copyFile.readSlot(slot)).orElseThrow().get(0)).isEqualTo(parts.get(0));
      assertThat(ok(copyFile.readSlot(slot)).orElseThrow().get(1)).isEqualTo(parts.get(1));
    }
    for (int slot = 0; slot < 4; slot++) {
      assertThat(ok(copyFile.readSlot(slot)).orElseThrow().get(0))
          .isEqualTo(ok(f.readSlot(slot)).orElseThrow().get(0));
    }
    assertThat(ok(copyFile.deadSpace())).isGreaterThan(0L);
    copyFile.close();
    f.close();
  }

  @Test
  @DisplayName("A larger rewrite of a middle slot leaves every other slot byte-identical")
  void aLargerRewriteOfAMiddleSlotLeavesEveryOtherSlotByteIdentical(@TempDir final Path dir) throws IOException {
    final Path path = dir.resolve("00.iv");
    final IndexedFile f = ok(IndexedFile.openOrCreate(path, 8));
    for (int slot = 0; slot < 6; slot++) {
      final byte[] payload = new byte[50];
      java.util.Arrays.fill(payload, (byte) slot);
      assertThat(f.writeSlot(slot, List.of(b("v" + slot), payload))).isInstanceOf(EngineResult.Ok.class);
    }
    final byte[] before = Files.readAllBytes(path);
    final List<SlotEntry> entriesBefore = ok(f.entries());

    // slot 2 gets a payload that is thousands of times larger (and does not compress away)
    long state = 0x9E3779B97F4A7C15L;
    final byte[] big = new byte[200_000];
    for (int i = 0; i < big.length; i++) {
      state ^= state << 13;
      state ^= state >>> 7;
      state ^= state << 17;
      big[i] = (byte) (state >>> 24);
    }
    assertThat(f.writeSlot(2, List.of(b("v2"), big.clone()))).isInstanceOf(EngineResult.Ok.class);
    final byte[] after = Files.readAllBytes(path);
    final List<SlotEntry> entriesAfter = ok(f.entries());

    assertThat(after.length).isGreaterThan(before.length + 100_000);
    final int region = 8 * 16;
    // everything that was there is still there, byte for byte, except the one index entry
    assertThat(java.util.Arrays.copyOfRange(after, region, before.length))
        .isEqualTo(java.util.Arrays.copyOfRange(before, region, before.length));
    for (int slot = 0; slot < 8; slot++) {
      if (slot == 2) {
        continue;
      }
      assertThat(java.util.Arrays.copyOfRange(after, slot * 16, slot * 16 + 16))
          .as("index entry %d", slot)
          .isEqualTo(java.util.Arrays.copyOfRange(before, slot * 16, slot * 16 + 16));
    }
    assertThat(java.util.Arrays.copyOfRange(after, 32, 48))
        .isNotEqualTo(java.util.Arrays.copyOfRange(before, 32, 48));
    assertThat(entriesAfter.get(2).start()).isEqualTo(before.length);
    for (final int slot : new int[] {0, 1, 3, 4, 5}) {
      assertThat(findBySlot(entriesAfter, slot)).isEqualTo(findBySlot(entriesBefore, slot));
    }

    assertThat(ok(f.readSlot(2)).orElseThrow().get(1)).isEqualTo(big);
    for (final int slot : new int[] {0, 1, 3, 4, 5}) {
      final byte[] expected = new byte[50];
      java.util.Arrays.fill(expected, (byte) slot);
      assertThat(ok(f.readSlot(slot)).orElseThrow().get(1)).isEqualTo(expected);
    }
    assertThat(ok(f.deadSpace())).isGreaterThan(0L);
    f.close();
  }

  private static SlotEntry findBySlot(final List<SlotEntry> entries, final int slot) {
    return entries.stream().filter(e -> e.slot() == slot).findFirst().orElseThrow();
  }
}

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

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.OptionalLong;
import java.util.zip.Deflater;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.aestallon.storageexplorer.core.service.engine.EngineResult;

/**
 * Mirrors {@code sb4a-fs}'s own {@code objectstream} module test suite
 * ({@code ../sb4a-fs/crates/sb4a-fs/src/objectstream.rs}).
 */
class ObjectStreamTest {

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

  /** Frames the contents as stream entries, compressed at the best level, like the test helper
   *  the Rust library keeps for its own tests. */
  private static byte[] encode(final byte[]... entries) {
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    for (final byte[] entry : entries) {
      final Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION, true);
      deflater.setInput(entry);
      deflater.finish();
      final ByteArrayOutputStream compressed = new ByteArrayOutputStream();
      final byte[] buf = new byte[8192];
      while (!deflater.finished()) {
        final int n = deflater.deflate(buf);
        compressed.write(buf, 0, n);
      }
      deflater.end();
      final byte[] c = compressed.toByteArray();
      final byte[] length = java.nio.ByteBuffer.allocate(4).putInt(c.length).array();
      out.write(length, 0, 4);
      out.write(c, 0, c.length);
      out.write(length, 0, 4);
    }
    return out.toByteArray();
  }

  @Test
  @DisplayName("Reads entries up to the head")
  void readsEntriesUpToTheHead() {
    final byte[] bytes = encode(b("{\"a\":1}"), b("{\"b\":2}"), b("{\"c\":3}"));
    final StreamScan all = ok(ObjectStream.scan(bytes, OptionalLong.of(bytes.length)));
    assertThat(all.entries()).hasSize(3);
    assertThat(all.entries().get(1).data()).isEqualTo(b("{\"b\":2}"));
    assertThat(all.entries().get(0).start()).isEqualTo(0);
    assertThat(all.entries().get(0).end()).isEqualTo(all.entries().get(1).start());
    assertThat(all.end()).isEqualTo(bytes.length);
    assertThat(all.trailingBytes()).isEqualTo(0);

    // the head after the second entry: the third one is not part of the stream yet
    final long head = all.entries().get(1).end();
    final StreamScan two = ok(ObjectStream.scan(bytes, OptionalLong.of(head)));
    assertThat(two.entries()).hasSize(2);
    assertThat(two.trailingBytes()).isEqualTo(bytes.length - head);

    assertThat(ok(ObjectStream.scan(new byte[0], OptionalLong.of(0))).entries()).isEmpty();
    assertThat(ok(ObjectStream.scan(new byte[0], OptionalLong.empty())).entries()).isEmpty();
  }

  @Test
  @DisplayName("A head that is not on an entry boundary is an error")
  void aHeadThatIsNotOnAnEntryBoundaryIsAnError() {
    final byte[] bytes = encode(b("{\"a\":1}"), b("{\"b\":2}"));
    final long firstEnd = ok(ObjectStream.scan(bytes, OptionalLong.empty())).entries().get(0).end();
    assertThat(ObjectStream.scan(bytes, OptionalLong.of(firstEnd + 3))).isInstanceOf(EngineResult.Err.class);
    assertThat(ObjectStream.scan(bytes, OptionalLong.of(bytes.length + 1))).isInstanceOf(EngineResult.Err.class);
  }

  @Test
  @DisplayName("Without a head, the scan stops at the first bad entry")
  void withoutAHeadTheScanStopsAtTheFirstBadEntry() {
    final byte[] good = encode(b("{\"a\":1}"));
    final long goodLength = good.length;
    final byte[] bytes = new byte[good.length + 7];
    System.arraycopy(good, 0, bytes, 0, good.length);
    System.arraycopy(new byte[] {0, 0, 0, 9, 1, 2, 3}, 0, bytes, good.length, 7);

    final StreamScan scanned = ok(ObjectStream.scan(bytes, OptionalLong.empty()));
    assertThat(scanned.entries()).hasSize(1);
    assertThat(scanned.end()).isEqualTo(goodLength);
    assertThat(scanned.trailingBytes()).isEqualTo(7);

    // with a head the same bytes are fine as long as the head stays before them ...
    assertThat(ok(ObjectStream.scan(bytes, OptionalLong.of(goodLength))).entries()).hasSize(1);
    // ... and an error if the head claims them
    assertThat(ObjectStream.scan(bytes, OptionalLong.of(bytes.length))).isInstanceOf(EngineResult.Err.class);
  }

  @Test
  @DisplayName("Detects damage")
  void detectsDamage() {
    final byte[] bytes = encode(b("{\"a\":1}"));
    final OptionalLong head = OptionalLong.of(bytes.length);

    final byte[] wrongFooter = bytes.clone();
    wrongFooter[wrongFooter.length - 1] ^= 1;
    assertThat(ObjectStream.scan(wrongFooter, head)).isInstanceOf(EngineResult.Err.class);

    final byte[] negative = bytes.clone();
    System.arraycopy(java.nio.ByteBuffer.allocate(4).putInt(-5).array(), 0, negative, 0, 4);
    assertThat(ObjectStream.scan(negative, head)).isInstanceOf(EngineResult.Err.class);

    final byte[] huge = bytes.clone();
    System.arraycopy(java.nio.ByteBuffer.allocate(4).putInt(Integer.MAX_VALUE).array(), 0, huge, 0, 4);
    assertThat(ObjectStream.scan(huge, head)).isInstanceOf(EngineResult.Err.class);

    // not deflate data, with consistent lengths
    final ByteArrayOutputStream garbage = new ByteArrayOutputStream();
    garbage.writeBytes(java.nio.ByteBuffer.allocate(4).putInt(5).array());
    garbage.writeBytes(new byte[] {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF});
    garbage.writeBytes(java.nio.ByteBuffer.allocate(4).putInt(5).array());
    final byte[] garbageBytes = garbage.toByteArray();
    assertThat(ObjectStream.scan(garbageBytes, OptionalLong.of(garbageBytes.length)))
        .isInstanceOf(EngineResult.Err.class);
  }

  @Test
  @DisplayName("Reads a real platform-written stream")
  void readsARealPlatformStream() {
    final Path path = FIXTURES.resolve(Path.of("documentdossier-collections",
        "org_smartbit4all_documentdossier_domain_DocumentFile", "2026", "10", "15", "21", "26",
        "08476c48-f129-49d7-a84b-ebac11af8d8c", "storedRef", "DOCUMENT_STORAGE_ITEM_HISTORY_REF-s",
        "events.os"));
    final StreamScan scanned = ok(ObjectStream.readFile(path, OptionalLong.of(626))).orElseThrow();
    assertThat(scanned.fileLength()).isEqualTo(626);
    assertThat(scanned.trailingBytes()).isEqualTo(0);
    assertThat(scanned.entries()).isNotEmpty();
    // the first segment is 0x11d = 285 compressed bytes
    assertThat(scanned.entries().get(0).end() - scanned.entries().get(0).start()).isEqualTo(285 + 8);
    final com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
    for (final StreamEntry entry : scanned.entries()) {
      assertThatCodeDoesNotThrowJson(mapper, entry);
    }
    assertThat(ok(ObjectStream.readFile(path.resolveSibling("missing.os"), OptionalLong.empty()))).isEmpty();
  }

  private static void assertThatCodeDoesNotThrowJson(final com.fasterxml.jackson.databind.ObjectMapper mapper,
                                                       final StreamEntry entry) {
    try {
      mapper.readTree(entry.data());
    } catch (final Exception e) {
      throw new AssertionError(new String(entry.data(), StandardCharsets.UTF_8), e);
    }
  }
}

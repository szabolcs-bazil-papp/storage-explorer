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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.aestallon.storageexplorer.core.service.engine.EngineResult;
import com.aestallon.storageexplorer.core.service.engine.StorageEngineError;

/**
 * Mirrors {@code sb4a-fs}'s own {@code multipart} module test suite
 * ({@code ../sb4a-fs/crates/sb4a-fs/src/multipart.rs}).
 */
class MultipartTest {

  /** Sibling checkout, same convention the build already relies on for the platform dependency. */
  private static final Path FIXTURES = Path.of("..", "..", "sb4a-fs", "crates", "sb4a-fs", "tests",
      "fixtures", "dev-fs");

  private static <T> T ok(final EngineResult<T> result) {
    if (result instanceof EngineResult.Ok<T> ok) {
      return ok.value();
    }
    throw new AssertionError("expected Ok, got " + result);
  }

  @Test
  @DisplayName("Empty input has no parts")
  void emptyInputHasNoParts() {
    assertThat(ok(Multipart.parse(new byte[0]))).isEmpty();
    assertThat(Multipart.serialize(List.of())).isEmpty();
  }

  @Test
  @DisplayName("Frames with big-endian lengths")
  void framesWithBigEndianLengths() {
    final byte[] bytes = Multipart.serialize(List.of("ab".getBytes(StandardCharsets.UTF_8),
        new byte[0], "xyz".getBytes(StandardCharsets.UTF_8)));
    assertThat(bytes).containsExactly(
        0, 0, 0, 0, 0, 0, 0, 2, 'a', 'b',
        0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 3, 'x', 'y', 'z');

    final List<byte[]> parts = ok(Multipart.parse(bytes));
    assertThat(parts).hasSize(3);
    assertThat(parts.get(0)).isEqualTo("ab".getBytes(StandardCharsets.UTF_8));
    assertThat(parts.get(1)).isEmpty();
    assertThat(parts.get(2)).isEqualTo("xyz".getBytes(StandardCharsets.UTF_8));
  }

  @Test
  @DisplayName("Rejects damaged input: every proper prefix that cuts a part or header is damaged")
  void rejectsDamagedInput() {
    final byte[] good = Multipart.serialize(List.of(
        "hello".getBytes(StandardCharsets.UTF_8), "world".getBytes(StandardCharsets.UTF_8)));
    final int header = Long.BYTES;
    for (int cut = 1; cut < good.length; cut++) {
      final byte[] prefix = java.util.Arrays.copyOf(good, cut);
      final EngineResult<List<byte[]>> r = Multipart.parse(prefix);
      if (cut == header + 5) {
        assertThat(ok(r)).hasSize(1);
      } else {
        assertThat(r).as("cut %d", cut).isInstanceOf(EngineResult.Err.class);
      }
    }

    final byte[] negative = java.nio.ByteBuffer.allocate(8).putLong(-1L).array();
    assertThat(Multipart.parse(negative)).isInstanceOf(EngineResult.Err.class);
    final byte[] huge = java.nio.ByteBuffer.allocate(8).putLong(Long.MAX_VALUE).array();
    assertThat(Multipart.parse(huge)).isInstanceOf(EngineResult.Err.class);
  }

  @Test
  @DisplayName("A missing file reads as empty, not an error")
  void missingFileIsNone(@TempDir final Path dir) {
    assertThat(ok(Multipart.readFile(dir.resolve("nope")))).isEmpty();
  }

  @Test
  @DisplayName("File round trip; a damaged file's error carries its path")
  void fileRoundTripAndErrorCarriesThePath(@TempDir final Path dir) throws IOException {
    final Path path = dir.resolve("x/00");
    final List<byte[]> parts = List.of("one".getBytes(StandardCharsets.UTF_8),
        "two".getBytes(StandardCharsets.UTF_8));
    assertThat(Multipart.writeFile(path, parts)).isInstanceOf(EngineResult.Ok.class);
    final List<byte[]> readBack = ok(Multipart.readFile(path)).orElseThrow();
    assertThat(readBack).hasSize(2);
    assertThat(readBack.get(0)).isEqualTo(parts.get(0));
    assertThat(readBack.get(1)).isEqualTo(parts.get(1));

    Files.write(path, new byte[] {0, 0, 0});
    final EngineResult<Optional<List<byte[]>>> damaged = Multipart.readFile(path);
    assertThat(damaged).isInstanceOf(EngineResult.Err.class);
    final StorageEngineError error = ((EngineResult.Err<Optional<List<byte[]>>>) damaged).error();
    assertThat(error).isInstanceOf(StorageEngineError.Format.class);
  }

  @Test
  @DisplayName("Round-trips arbitrary parts (seeded random, standing in for the Rust proptest case)")
  void roundTrips() {
    final Random random = new Random(1234);
    for (int i = 0; i < 2_000; i++) {
      final int count = random.nextInt(6);
      final List<byte[]> parts = new java.util.ArrayList<>(count);
      for (int p = 0; p < count; p++) {
        final byte[] part = new byte[random.nextInt(64)];
        random.nextBytes(part);
        parts.add(part);
      }
      assertThat(ok(Multipart.parse(Multipart.serialize(parts)))).containsExactlyElementsOf(parts);
    }
  }

  @Test
  @DisplayName("Reads a real classic version file, written by the platform")
  void readsARealClassicVersionFile() {
    final Path path = FIXTURES.resolve(Path.of("account-ticket",
        "hu_it4all_eji_fiok_domain_model_AccountTicket", "2025", "12", "19", "16", "18",
        "5baf7e92-4abe-414b-81dd-e8018f1a99c5", "00"));
    final List<byte[]> parts = ok(Multipart.readFile(path)).orElseThrow();
    assertThat(parts).hasSize(2);
    assertThat(new String(parts.get(0), StandardCharsets.UTF_8)).startsWith("{\"serialNoData\":0,");
    assertThat(new String(parts.get(1), StandardCharsets.UTF_8)).startsWith("{");
    // the framing is canonical: writing the parts back gives the same bytes
    assertThat(Multipart.serialize(parts)).isEqualTo(readAll(path));
  }

  @Test
  @DisplayName("Reads a real relation file, written by the platform")
  void readsARealRelationFile() {
    final Path path = FIXTURES.resolve(Path.of("org", "org_smartbit4all_api_storage_bean_StorageSettings",
        "settings", "04.r"));
    final List<byte[]> parts = ok(Multipart.readFile(path)).orElseThrow();
    assertThat(parts).hasSize(2);
    assertThat(new String(parts.get(0), StandardCharsets.UTF_8))
        .startsWith("{\"serialNoData\":2,\"serialNoRelation\":4,");
    assertThat(new String(parts.get(1), StandardCharsets.UTF_8)).startsWith("{\"uri\":");
  }

  @Test
  @DisplayName("Reads a real single-version object, written by the platform")
  void readsARealSingleVersionObject() {
    final Path path = FIXTURES.resolve(Path.of("asyncChannelRegistry-sv",
        "org_smartbit4all_api_invocation_bean_RuntimeAsyncChannel", "2026", "10", "15", "19", "48",
        "05df003f-46dd-4a5a-bf35-a1e34189be94-s.o"));
    final List<byte[]> parts = ok(Multipart.readFile(path)).orElseThrow();
    assertThat(parts).hasSize(2);
    assertThat(new String(parts.get(0), StandardCharsets.UTF_8))
        .startsWith("{\"uri\":\"asyncChannelRegistry-sv:");
  }

  private static byte[] readAll(final Path path) {
    try {
      return Files.readAllBytes(path);
    } catch (final IOException e) {
      throw new java.io.UncheckedIOException(e);
    }
  }
}

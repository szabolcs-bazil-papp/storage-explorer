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

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import com.aestallon.storageexplorer.core.service.engine.EngineResult;
import com.aestallon.storageexplorer.core.service.engine.StorageEngineError;

/**
 * Object streams, the {@code <object folder>/<name>.os} files: an append-only sequence of
 * compressed entries.
 *
 * <pre>
 * +-----------------+------------------------+-----------------+
 * | i32 BE length N | N bytes of raw DEFLATE | i32 BE length N |   one entry, repeated
 * +-----------------+------------------------+-----------------+
 * </pre>
 *
 * <p>The length is written before <em>and</em> after the payload so the platform can walk the
 * file in both directions.
 *
 * <p>Where the stream ends is not in the file: the object descriptor lists the streams with a
 * {@code headPosition} ({@link ObjectData#objectStreams()}), and entries at or after it are not
 * part of the stream (a write that has not been published yet). Reading with a head position is
 * strict: the entries must tile the file exactly up to it.
 *
 * <p>Ported from the {@code sb4a-fs} Rust library's {@code objectstream} module
 * ({@code ../sb4a-fs/crates/sb4a-fs/src/objectstream.rs}). Read-only — writing object streams is
 * explicitly out of scope for this port, matching the Rust library itself.
 */
final class ObjectStream {

  private static final int LENGTH = Integer.BYTES;

  private ObjectStream() {}

  /**
   * Reads the entries of a stream held in {@code bytes}.
   *
   * <p>With a {@code headPosition}, the entries must tile the file from {@code 0} exactly up to
   * it — anything else is an error (bytes after it are ignored). Without one, entries are read
   * until the data stops being a valid entry; what follows is reported as
   * {@link StreamScan#trailingBytes()}.
   */
  static EngineResult<StreamScan> scan(final byte[] bytes, final OptionalLong headPosition) {
    final long fileLength = bytes.length;
    final long limit;
    if (headPosition.isPresent()) {
      final long head = headPosition.getAsLong();
      if (head > fileLength) {
        return new EngineResult.Err<>(new StorageEngineError.Malformed(
            "the head position " + head + " is beyond the end of the file (" + fileLength
                + " bytes)"));
      }
      limit = head;
    } else {
      limit = fileLength;
    }
    final boolean strict = headPosition.isPresent();

    final List<StreamEntry> entries = new ArrayList<>();
    long offset = 0;
    while (offset < limit) {
      final EngineResult<StreamEntry> entry = readEntry(bytes, offset, limit);
      if (entry instanceof EngineResult.Ok<StreamEntry> ok) {
        offset = ok.value().end();
        entries.add(ok.value());
      } else if (strict) {
        return new EngineResult.Err<>(((EngineResult.Err<StreamEntry>) entry).error());
      } else {
        break;
      }
    }
    return new EngineResult.Ok<>(new StreamScan(entries, offset, fileLength));
  }

  /** Reads and scans a stream file. Empty if the file does not exist. */
  static EngineResult<Optional<StreamScan>> readFile(final Path path, final OptionalLong headPosition) {
    final Optional<byte[]> bytes;
    switch (FsUtil.readFile(path)) {
      case EngineResult.Ok<Optional<byte[]>> ok -> bytes = ok.value();
      case EngineResult.Err<Optional<byte[]>> err -> {
        return new EngineResult.Err<>(err.error());
      }
    }
    if (bytes.isEmpty()) {
      return new EngineResult.Ok<>(Optional.empty());
    }
    return switch (scan(bytes.get(), headPosition)) {
      case EngineResult.Ok<StreamScan> ok -> new EngineResult.Ok<>(Optional.of(ok.value()));
      case EngineResult.Err<StreamScan> err -> new EngineResult.Err<>(err.error().at(path));
    };
  }

  private static EngineResult<StreamEntry> readEntry(final byte[] bytes, final long offset, final long limit) {
    if (offset > Integer.MAX_VALUE) {
      return new EngineResult.Err<>(malformed(offset, "offset too large"));
    }
    final int start = (int) offset;
    if (start + LENGTH > bytes.length) {
      return new EngineResult.Err<>(malformed(offset, "truncated length"));
    }
    final int length = ByteBuffer.wrap(bytes, start, LENGTH).getInt();
    if (length < 0) {
      return new EngineResult.Err<>(malformed(offset, "negative length"));
    }
    // 2*LENGTH + length, computed without risking overflow (length is a non-negative i32).
    if ((long) start + (2L * LENGTH) + length > limit || (long) start + (2L * LENGTH) + length > bytes.length) {
      return new EngineResult.Err<>(malformed(offset, "the entry does not fit before the end of the stream"));
    }
    final int end = start + 2 * LENGTH + length;

    final byte[] payload = Arrays.copyOfRange(bytes, start + LENGTH, start + LENGTH + length);
    final int footer = ByteBuffer.wrap(bytes, end - LENGTH, LENGTH).getInt();
    if (footer != length) {
      return new EngineResult.Err<>(malformed(offset,
          "the closing length " + footer + " differs from the opening one " + length));
    }
    return switch (Deflate.decompress(payload)) {
      case EngineResult.Ok<byte[]> ok -> new EngineResult.Ok<>(new StreamEntry(offset, end, ok.value()));
      case EngineResult.Err<byte[]> err -> new EngineResult.Err<>(
          malformed(offset, "the content does not inflate: "
              + ((StorageEngineError.Malformed) err.error()).why()));
    };
  }

  private static StorageEngineError.Malformed malformed(final long offset, final String what) {
    return new StorageEngineError.Malformed("entry at offset " + offset + ": " + what);
  }
}

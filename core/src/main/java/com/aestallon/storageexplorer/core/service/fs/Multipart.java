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
import com.aestallon.storageexplorer.core.service.engine.EngineResult;
import com.aestallon.storageexplorer.core.service.engine.StorageEngineError;

/**
 * The classic multipart framing: a sequence of {@code [i64 big-endian length][bytes]}. There is
 * no header, footer or checksum, so the only integrity check is that the lengths add up to
 * exactly the size of the file. Used by classic version files, relation files and single-version
 * {@code .o} files.
 *
 * <p>Ported from the {@code sb4a-fs} Rust library's {@code multipart} module
 * ({@code ../sb4a-fs/crates/sb4a-fs/src/multipart.rs}).
 */
final class Multipart {

  private static final int HEADER = Long.BYTES;

  private Multipart() {}

  /** Splits the bytes into their parts. An empty input has no parts. */
  static EngineResult<List<byte[]>> parse(final byte[] bytes) {
    final List<byte[]> parts = new ArrayList<>();
    int offset = 0;
    while (offset < bytes.length) {
      if (offset + HEADER > bytes.length) {
        return new EngineResult.Err<>(
            new StorageEngineError.Malformed("truncated part header at offset " + offset));
      }
      final long length = readLongBE(bytes, offset);
      offset += HEADER;
      if (length < 0) {
        return new EngineResult.Err<>(new StorageEngineError.Malformed(
            "negative part length " + length + " before offset " + offset));
      }
      // bytes.length - offset is a small, non-negative int: safe to compare against length
      // without the overflow a naive "offset + length" addition would risk for a huge length.
      if (length > bytes.length - offset) {
        return new EngineResult.Err<>(new StorageEngineError.Malformed(
            "part of " + length + " bytes at offset " + offset + " exceeds the " + bytes.length
                + " bytes of the data"));
      }
      final int end = offset + (int) length;
      parts.add(Arrays.copyOfRange(bytes, offset, end));
      offset = end;
    }
    return new EngineResult.Ok<>(parts);
  }

  /** Frames the parts. */
  static byte[] serialize(final List<byte[]> parts) {
    long size = 0;
    for (final byte[] part : parts) {
      size += HEADER + part.length;
    }
    final ByteBuffer out = ByteBuffer.allocate((int) size);
    for (final byte[] part : parts) {
      out.putLong(part.length);
      out.put(part);
    }
    return out.array();
  }

  /** Reads and splits a multipart file. Empty (not an error) if the file does not exist. */
  static EngineResult<Optional<List<byte[]>>> readFile(final Path path) {
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
    return switch (parse(bytes.get())) {
      case EngineResult.Ok<List<byte[]>> ok -> new EngineResult.Ok<>(Optional.of(ok.value()));
      case EngineResult.Err<List<byte[]>> err -> new EngineResult.Err<>(err.error().at(path));
    };
  }

  /**
   * Writes a multipart file through a temp file + atomic replace. The parent folders are
   * created.
   */
  static EngineResult<Void> writeFile(final Path path, final List<byte[]> parts) {
    return FsUtil.writeFileAtomic(path, serialize(parts));
  }

  private static long readLongBE(final byte[] bytes, final int offset) {
    return ByteBuffer.wrap(bytes, offset, HEADER).getLong();
  }
}

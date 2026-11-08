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
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;
import com.aestallon.storageexplorer.core.service.engine.EngineResult;
import com.aestallon.storageexplorer.core.service.engine.StorageEngineError;

/**
 * Raw DEFLATE (no zlib header/trailer) compression, shared by {@link IndexedFile}'s segments and
 * object stream entries — both use the same framing the platform does.
 */
final class Deflate {

  private Deflate() {}

  static byte[] compress(final byte[] data, final int level) {
    final Deflater deflater = new Deflater(level, true);
    try {
      deflater.setInput(data);
      deflater.finish();
      final ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(64, data.length / 2));
      final byte[] buf = new byte[8192];
      while (!deflater.finished()) {
        final int n = deflater.deflate(buf);
        out.write(buf, 0, n);
      }
      return out.toByteArray();
    } finally {
      deflater.end();
    }
  }

  static EngineResult<byte[]> decompress(final byte[] compressed) {
    final Inflater inflater = new Inflater(true);
    try {
      inflater.setInput(compressed);
      final ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(64, compressed.length * 2));
      final byte[] buf = new byte[8192];
      while (!inflater.finished()) {
        final int n;
        try {
          n = inflater.inflate(buf);
        } catch (final DataFormatException e) {
          return new EngineResult.Err<>(
              new StorageEngineError.Malformed("a part does not inflate: " + e.getMessage()));
        }
        if (n == 0 && !inflater.finished()
            && (inflater.needsInput() || inflater.needsDictionary())) {
          return new EngineResult.Err<>(
              new StorageEngineError.Malformed("a part does not inflate: truncated input"));
        }
        out.write(buf, 0, n);
      }
      return new EngineResult.Ok<>(out.toByteArray());
    } finally {
      inflater.end();
    }
  }
}

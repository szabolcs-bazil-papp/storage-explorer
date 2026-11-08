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

package com.aestallon.storageexplorer.core.service.engine;

import java.io.IOException;
import java.nio.file.Path;

/**
 * The shared vocabulary of storage-interaction outcomes a {@code StorageEngine} implementation
 * reports as failure. Backend-agnostic by design: both the file-system implementation and any
 * future SQL-backed one raise these same shapes rather than each inventing their own.
 *
 * <p>Ported from the {@code sb4a-fs} Rust library's {@code Error} enum
 * ({@code ../sb4a-fs/crates/sb4a-fs/src/error.rs}).
 */
public sealed interface StorageEngineError {

  /** The text is not a usable entry URI, or would escape the storage root. */
  record InvalidUri(String uri, String reason) implements StorageEngineError {}

  record NotFound(String message) implements StorageEngineError {}

  record Io(Path path, IOException cause) implements StorageEngineError {}

  /** A file does not have the expected structure. */
  record Format(Path path, String why) implements StorageEngineError {}

  /** The entry exists but has no data version yet (it only carries relations, or nothing). */
  record NoData(String message) implements StorageEngineError {}

  /**
   * The payload is encrypted with a rule the caller gave no key for. Kept for forward
   * compatibility even though encryption support itself is out of scope for this port.
   */
  record MissingKey(String rule) implements StorageEngineError {}

  /** A valid request this engine does not support. */
  record Unsupported(String message) implements StorageEngineError {}

  /** An RDB-backed engine's connection/query failure — {@link Io}'s counterpart for a backend
   *  with no {@link Path} of its own to attach. */
  record Sql(String message, Exception cause) implements StorageEngineError {}

  /** Somebody else holds the entry's write lock. */
  record Locked(Path path, String owner) implements StorageEngineError {}

  /** An in-flight-or-crashed write marker sits next to the entry; resolve it before writing. */
  record InDoubt(Path path) implements StorageEngineError {}

  record AlreadyExists(String message) implements StorageEngineError {}

  /**
   * Bytes that don't have the expected structure, not yet attached to the file they came from.
   * Internal to engine implementations: callers attach a path via {@link #at(Path)} before this
   * is ever handed back as a {@code StorageEngine} result.
   */
  record Malformed(String why) implements StorageEngineError {}

  /**
   * Attaches {@code path} to a {@link Malformed} error, turning it into a {@link Format}; any
   * other error is returned unchanged. Mirrors {@code sb4a-fs}'s {@code Error::at}.
   */
  default StorageEngineError at(final Path path) {
    return (this instanceof Malformed malformed)
        ? new Format(path, malformed.why())
        : this;
  }
}

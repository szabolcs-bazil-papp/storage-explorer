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

package com.aestallon.storageexplorer.core.model.instance.dto;

import java.nio.file.Path;
import java.util.Optional;

/**
 * The eager, pre-connection half of {@link Availability} classification (decisions log §10's
 * settled sub-decisions). Validates a {@link StorageLocation} <strong>locally, without
 * attempting a connection</strong> — this is what lets a caller (Milestone 4's {@code
 * StorageInstanceProvider}) short-circuit {@code StorageIndexFactory.create(...)} entirely for a
 * configuration that's already known to be invalid, rather than letting it fail downstream for a
 * reason detectable right here. Everything else that fails to connect (DB unreachable, FS root
 * not mounted) is {@link Availability#UNAVAILABLE}, classified by the caller after actually
 * attempting the connection — not this class's job.
 *
 * <p>Pure functions, no wiring yet — Milestone 4 is where a caller actually invokes this before
 * {@code StorageIndexFactory.create(...)}.
 */
public final class AvailabilityClassifier {

  private AvailabilityClassifier() {}

  /** Whether {@code location} is misconfigured, i.e. invalid in a way no connection attempt
   *  could ever fix. {@code true} iff {@link #misconfiguredReason} is present. */
  public static boolean isMisconfigured(final StorageLocation location) {
    return misconfiguredReason(location).isPresent();
  }

  /** Why {@code location} is misconfigured, or empty if it looks locally valid (a connection
   *  attempt may still fail for other reasons — that's {@link Availability#UNAVAILABLE}, not
   *  this method's concern). */
  public static Optional<String> misconfiguredReason(final StorageLocation location) {
    return switch (location) {
      case FsStorageLocation fs -> fsReason(fs);
      case SqlStorageLocation sql -> sqlReason(sql);
      case null -> Optional.of("no storage location given");
    };
  }

  private static Optional<String> fsReason(final FsStorageLocation fs) {
    final Path path = fs.getPath();
    if (path == null) {
      return Optional.of("no path given");
    }
    // a Path that was successfully constructed at all has already passed the JDK's own
    // filesystem-provider syntax checks (e.g. a NUL byte throws InvalidPathException on
    // construction, on every platform this runs on) - there is nothing left to reject here
    // beyond "is there actually a path", short of touching the filesystem (UNAVAILABLE's job).
    if (path.toString().isBlank()) {
      return Optional.of("path is blank");
    }
    return Optional.empty();
  }

  private static Optional<String> sqlReason(final SqlStorageLocation sql) {
    final DatabaseConnectionData data = sql.getDbConnectionData();
    if (data == null) {
      return Optional.of("no connection data given");
    }
    final String url = data.getUrl();
    if (url == null || url.isBlank()) {
      return Optional.of("no JDBC URL given");
    }
    if (!url.startsWith("jdbc:")) {
      return Optional.of("not a JDBC URL (must start with \"jdbc:\"): " + url);
    }
    return Optional.empty();
  }
}

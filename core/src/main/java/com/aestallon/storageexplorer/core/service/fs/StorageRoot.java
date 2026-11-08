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

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import com.aestallon.storageexplorer.core.model.EntryUri;

/**
 * Where things live on disk. Pure path arithmetic: nothing here touches the disk.
 *
 * <p>For an entry {@code scheme:/a/b/id} with {@code base = <root>/scheme/a/b/id}:
 * <pre>
 * head descriptor               base.o
 * pending write of descriptor   base.t
 * lock                          base.l
 * classic data version          base/&lt;hex path of the data serial&gt;
 * relation version               base/&lt;hex path of the relation serial&gt;.r
 * indexed data versions         base/&lt;hex path of serial / index size&gt;.iv, slot serial % index size
 * </pre>
 *
 * <p>Ported from the {@code sb4a-fs} Rust library's {@code layout} module
 * ({@code ../sb4a-fs/crates/sb4a-fs/src/layout.rs}).
 */
public final class StorageRoot {

  private static final String OBJECT_EXTENSION = ".o";
  private static final String TRANSACTION_EXTENSION = ".t";
  private static final String LOCK_EXTENSION = ".l";
  private static final String RELATION_EXTENSION = ".r";
  private static final String INDEXED_VERSION_EXTENSION = ".iv";
  private static final String STREAM_EXTENSION = ".os";
  private static final String TABLE_DATA_EXTENSION = ".td";

  /** The index size (slots per {@code .iv} file) the platform uses for new indexed objects. */
  public static final int DEFAULT_INDEX_SIZE = 256;

  private final Path root;

  public StorageRoot(final Path root) {
    this.root = root;
  }

  public Path path() {
    return root;
  }

  /** {@code <root>/<scheme>/<path of the uri>}: the folder of the version files. */
  public Path versionDir(final EntryUri uri) {
    Path dir = root.resolve(uri.scheme());
    for (final String segment : uri.segments()) {
      dir = dir.resolve(segment);
    }
    return dir;
  }

  /** The head descriptor ({@code .o}). */
  public Path objectFile(final EntryUri uri) {
    return withExtension(uri, OBJECT_EXTENSION);
  }

  /** The pending write of the descriptor ({@code .t}). */
  public Path transactionFile(final EntryUri uri) {
    return withExtension(uri, TRANSACTION_EXTENSION);
  }

  /** The lock file ({@code .l}). */
  public Path lockFile(final EntryUri uri) {
    return withExtension(uri, LOCK_EXTENSION);
  }

  /** The file of an object stream: {@code <entry folder>/<name>.os}. */
  public Path streamFile(final EntryUri uri, final String name) {
    return versionDir(uri).resolve(name + STREAM_EXTENSION);
  }

  /** The serialized table data of the URI: {@code <root>/<scheme>/<path>.td}. */
  public Path tableDataFile(final EntryUri uri) {
    return withExtension(uri, TABLE_DATA_EXTENSION);
  }

  /** The relation version file of a relation serial. */
  public Path relationFile(final EntryUri uri, final long relationSerial) {
    return append(versionDir(uri), hexPath(relationSerial), RELATION_EXTENSION);
  }

  /**
   * Where the data version {@code serial} lives. The layout comes from the descriptor: the
   * indexed layout is used only if {@code strategy} is {@link Strategy#INDEXED} <strong>and</strong>
   * an index size is known, exactly like the platform does; anything else is classic.
   */
  public VersionLocation locateVersion(final EntryUri uri,
                                        final long serial,
                                        final Strategy strategy,
                                        final Integer indexSize) {
    if (strategy == Strategy.INDEXED && indexSize != null && indexSize > 0) {
      final long slotsWide = Integer.toUnsignedLong(indexSize);
      final Path file = append(versionDir(uri), hexPath(serial / slotsWide),
          INDEXED_VERSION_EXTENSION);
      final int slot = (int) (serial % slotsWide);
      return new VersionLocation.Indexed(file, slot, indexSize);
    }
    return new VersionLocation.Classic(append(versionDir(uri), hexPath(serial), ""));
  }

  /**
   * The inverse of {@link #objectFile(EntryUri)}: the URI of the entry whose descriptor is the
   * given {@code .o} file. Empty if the path is not an object file of this storage.
   */
  public Optional<EntryUri> uriOfObjectFile(final Path file) {
    if (!file.startsWith(root)) {
      return Optional.empty();
    }
    final String relative = root.relativize(file).toString();
    if (!relative.endsWith(OBJECT_EXTENSION)) {
      return Optional.empty();
    }
    final String stem = relative.substring(0, relative.length() - OBJECT_EXTENSION.length());
    final String[] segments = stem.split("[/\\\\]", -1);
    if (segments.length == 0) {
      return Optional.empty();
    }
    final String scheme = segments[0];
    final List<String> rest = List.of(segments).subList(1, segments.length);
    return EntryUri.fromSegments(scheme, rest);
  }

  private Path withExtension(final EntryUri uri, final String extension) {
    return append(versionDir(uri), "", extension);
  }

  private static Path append(final Path base, final String hexPath, final String extension) {
    return Path.of(base.toString() + hexPath + extension);
  }

  /**
   * The path (with a leading {@code /}) of an index in an unlimited, 256-wide-bucket structure.
   *
   * <p>The low byte of {@code index} is the file name (two upper-case hex digits); the rest of
   * the index ({@code index >>> 8}, if not zero) becomes one folder per upper-case hex digit:
   * {@code hexPath(5) == "/05"}, {@code hexPath(255) == "/FF"}, {@code hexPath(256) == "/1/00"},
   * {@code hexPath(0x1A305) == "/1/A/3/05"}.
   */
  public static String hexPath(final long index) {
    final long bucket = index >>> 8;
    final StringBuilder path = new StringBuilder();
    if (bucket > 0) {
      final String hex = Long.toHexString(bucket).toUpperCase();
      for (int i = 0; i < hex.length(); i++) {
        path.append('/').append(hex.charAt(i));
      }
    }
    path.append('/').append(String.format("%02X", index & 0xFF));
    return path.toString();
  }
}

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

package com.aestallon.storageexplorer.core.model;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * An entry URI: {@code scheme:/alias/.../id}, optionally pointing at a specific version
 * ({@code scheme:/alias/.../id.v<N>} — no version means the head/latest version).
 *
 * <p>The URI is also the address of the entry on disk (for a file-system-backed storage), so
 * everything that comes out of this type is safe to join onto a storage root: no empty, {@code .}
 * or {@code ..} segments, no path separators or other characters special in a Windows path.
 *
 * <p>Ported from the {@code sb4a-fs} Rust library's {@code uri} module
 * ({@code ../sb4a-fs/crates/sb4a-fs/src/uri.rs}) — {@code ObjectUri} and {@code VersionedUri} are
 * merged into this one immutable value, with version as an optional field, matching how the rest
 * of this codebase already treats a URI as a single value with the version embedded in it, rather
 * than as two separate types.
 *
 * <p>This is a shared, backend-agnostic type: a future SQL-backed storage implementation
 * addresses its entries with the same {@code EntryUri}, not a separate type of its own.
 */
public final class EntryUri {

  private static final String VERSION_POSTFIX = ".v";
  private static final String SINGLE_VERSION_POSTFIX = "-s";
  private static final String UNRESERVED_EXTRA = "-_.~@!$&'()+,;=";

  /** Percent-decoded, always starts with {@code /}, never ends with {@code /}. */
  private final String path;
  private final String scheme;
  private final Long version;

  private EntryUri(final String scheme, final String path, final Long version) {
    this.scheme = scheme;
    this.path = path;
    this.version = version;
  }

  /**
   * Parses {@code scheme:/path[.v<N>][#fragment]}. The fragment is ignored (it never addresses a
   * file). Empty if {@code text} is not a usable entry URI.
   */
  public static Optional<EntryUri> parse(final String text) {
    final int hashIdx = text.indexOf('#');
    final String withoutFragment = (hashIdx < 0) ? text : text.substring(0, hashIdx);

    final int colonIdx = withoutFragment.indexOf(':');
    if (colonIdx < 0) {
      return Optional.empty();
    }
    final String scheme = withoutFragment.substring(0, colonIdx);
    final String rawPath = withoutFragment.substring(colonIdx + 1);
    if (!isValidScheme(scheme) || !rawPath.startsWith("/")) {
      return Optional.empty();
    }

    final Optional<String> decoded = percentDecode(rawPath);
    if (decoded.isEmpty()) {
      return Optional.empty();
    }
    String path = decoded.get();

    Long version = null;
    final VersionSplit split = splitVersion(path);
    if (split != null) {
      path = path.substring(0, split.stemLen());
      version = split.version();
    }

    if (path.length() < 2) {
      return Optional.empty();
    }
    for (final String segment : path.substring(1).split("/", -1)) {
      if (checkSegment(segment) != null) {
        return Optional.empty();
      }
    }

    return Optional.of(new EntryUri(scheme, path, version));
  }

  /**
   * Builds the URI of an entry from its scheme and path segments (as found on disk). The
   * segments are taken literally: nothing is decoded and no version is split off.
   */
  public static Optional<EntryUri> fromSegments(final String scheme,
                                                            final List<String> segments) {
    final StringBuilder pathBuilder = new StringBuilder();
    for (final String segment : segments) {
      if (checkSegment(segment) != null) {
        return Optional.empty();
      }
      pathBuilder.append('/').append(segment);
    }
    final String path = pathBuilder.toString();
    if (!isValidScheme(scheme) || path.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(new EntryUri(scheme, path, null));
  }

  public String scheme() {
    return scheme;
  }

  /** The decoded path, starting with {@code /}. */
  public String path() {
    return path;
  }

  /** The path segments, outermost first. The last one is the id of the entry. */
  public List<String> segments() {
    return List.of(path.substring(1).split("/", -1));
  }

  /** The last path segment (the id, ending in {@code -s} for single-version entries). */
  public String id() {
    final int lastSlash = path.lastIndexOf('/');
    return path.substring(lastSlash + 1);
  }

  /** Whether the entry is stored with the single-version policy (the id ends with {@code -s}). */
  public boolean isSingleVersion() {
    return path.endsWith(SINGLE_VERSION_POSTFIX);
  }

  /** Empty means the head (latest) version. */
  public OptionalLong version() {
    return (version == null) ? OptionalLong.empty() : OptionalLong.of(version);
  }

  public boolean isHead() {
    return version == null;
  }

  /** This entry at its head (latest) version. */
  public EntryUri latest() {
    return (version == null) ? this : new EntryUri(scheme, path, null);
  }

  public EntryUri withVersion(final long version) {
    return new EntryUri(scheme, path, version);
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder(scheme).append(':');
    percentEncodePath(path, sb);
    if (version != null) {
      sb.append(VERSION_POSTFIX).append(version);
    }
    return sb.toString();
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof EntryUri other)) {
      return false;
    }
    return scheme.equals(other.scheme)
        && path.equals(other.path)
        && Objects.equals(version, other.version);
  }

  @Override
  public int hashCode() {
    return Objects.hash(scheme, path, version);
  }

  // --- parsing/formatting internals, ported from uri.rs -----------------------------------

  private record VersionSplit(int stemLen, long version) {}

  /**
   * Finds the version postfix. The text after the last {@code .v} must be a non-empty sequence of
   * digits that fits a {@code long}; anything else means the path has no version part.
   */
  private static VersionSplit splitVersion(final String path) {
    final int idx = path.lastIndexOf(VERSION_POSTFIX);
    if (idx < 0) {
      return null;
    }
    final String digits = path.substring(idx + VERSION_POSTFIX.length());
    if (digits.isEmpty() || !digits.chars().allMatch(c -> c >= '0' && c <= '9')) {
      return null;
    }
    try {
      return new VersionSplit(idx, Long.parseLong(digits));
    } catch (final NumberFormatException e) {
      return null;
    }
  }

  private static boolean isValidScheme(final String scheme) {
    if (scheme.isEmpty()) {
      return false;
    }
    for (int i = 0; i < scheme.length(); i++) {
      final char c = scheme.charAt(i);
      if (!isAsciiAlphanumeric(c) && c != '-' && c != '_' && c != '.' && c != '+') {
        return false;
      }
    }
    return true;
  }

  /** Non-null means {@code segment} is rejected, for this reason. */
  private static String checkSegment(final String segment) {
    if (segment.isEmpty()) {
      return "empty path segment";
    }
    if (segment.equals(".") || segment.equals("..")) {
      return "`.` and `..` segments are not allowed";
    }
    if (segment.endsWith(".") || segment.endsWith(" ")) {
      // Windows silently strips these, so two different URIs would share one file.
      return "a segment must not end with `.` or a space";
    }
    for (int i = 0; i < segment.length(); i++) {
      final char c = segment.charAt(i);
      if (Character.isISOControl(c) || "\\:*?\"<>|".indexOf(c) >= 0) {
        return "a segment contains a character that is not allowed in a file name";
      }
    }
    return null;
  }

  private static boolean isAsciiAlphanumeric(final char c) {
    return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9');
  }

  private static Optional<String> percentDecode(final String text) {
    if (!text.contains("%")) {
      return Optional.of(text);
    }
    final byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
    final ByteArrayOutputStream out = new ByteArrayOutputStream(bytes.length);
    int i = 0;
    while (i < bytes.length) {
      if (bytes[i] == '%') {
        if (i + 2 >= bytes.length) {
          return Optional.empty();
        }
        final int hi = Character.digit(bytes[i + 1], 16);
        final int lo = Character.digit(bytes[i + 2], 16);
        if (hi < 0 || lo < 0) {
          return Optional.empty();
        }
        out.write((hi << 4) | lo);
        i += 3;
      } else {
        out.write(bytes[i]);
        i += 1;
      }
    }
    final CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT);
    try {
      return Optional.of(decoder.decode(ByteBuffer.wrap(out.toByteArray())).toString());
    } catch (final CharacterCodingException e) {
      return Optional.empty();
    }
  }

  private static void percentEncodePath(final String path, final StringBuilder out) {
    path.codePoints().forEach(cp -> {
      if (cp < 128 && (isAsciiAlphanumeric((char) cp) || UNRESERVED_EXTRA.indexOf(cp) >= 0
          || cp == '/')) {
        out.appendCodePoint(cp);
      } else {
        for (final byte b : new String(Character.toChars(cp)).getBytes(StandardCharsets.UTF_8)) {
          out.append('%').append(String.format("%02X", b & 0xFF));
        }
      }
    });
  }
}

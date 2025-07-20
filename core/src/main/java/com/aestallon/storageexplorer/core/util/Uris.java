/*
 * Copyright (C) 2025 Szabolcs Bazil Papp
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

package com.aestallon.storageexplorer.core.util;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import org.smartbit4all.domain.data.storage.ObjectStorageImpl;
import com.aestallon.storageexplorer.core.model.entry.StorageEntryFactory;
import com.google.common.base.Strings;

public final class Uris {

  private Uris() {}

  private static final Set<String> STORED_COLLECTION_IDENTIFIERS = Set.of(
      StorageEntryFactory.STORED_LIST_MARKER,
      StorageEntryFactory.STORED_MAP_MARKER,
      StorageEntryFactory.STORED_REF_MARKER,
      StorageEntryFactory.STORED_SEQ_MARKER);
  private static final String REGEX_TIMESTAMP = "/\\d{4}/\\d{1,2}/\\d{1,2}/\\d{1,2}";
  private static final Pattern PATTERN_TIMESTAMP = Pattern.compile(REGEX_TIMESTAMP);

  public static boolean equalIgnoringVersion(URI u1, URI u2) {
    return Objects.equals(
        (u1 == null) ? null : ObjectStorageImpl.getUriWithoutVersion(u1),
        (u2 == null) ? null : ObjectStorageImpl.getUriWithoutVersion(u2));
  }

  public static BiPredicate<URI, URI> equalIgnoringVersion() {
    return Uris::equalIgnoringVersion;
  }

  public static Predicate<URI> equalsIgnoringVersion(URI uri) {
    return u -> equalIgnoringVersion().test(u, uri);
  }

  public static URI latest(URI uri) {
    final String uriString = uri.toString();
    final int dotIdx = uriString.lastIndexOf('.');
    if (dotIdx > -1) {
      return URI.create(uriString.substring(0, dotIdx));
    }

    return uri;
  }

  public static Optional<URI> parse(Object o) {
    if (o instanceof URI uri) {
      return Optional.of(uri);
    }

    if (o instanceof String s) {
      return parseStr(s);
    }

    return Optional.empty();
  }

  public static String getTypeName(final URI uri) {
    final String[] typeElements = uri.getPath().split("/")[1].split("_");
    return typeElements[typeElements.length - 1];
  }

  public static String getUuid(final URI uri) {
    final var pathElements = uri.getPath().split("/");
    return pathElements[pathElements.length - 1];
  }

  public static long getVersion(final URI uri) {
    final Long boxed = ObjectStorageImpl.getUriVersion(uri);
    return (boxed == null) ? -1L : boxed;
  }

  public static URI atVersion(final URI uri, final long version) {
    return ObjectStorageImpl.getUriWithVersion(uri, version);
  }

  public static boolean isSingleVersion(final URI uri) {
    return uri.toString().endsWith("-s");
  }

  private static boolean containsStoredCollectionIdentifier(final String input) {
    return STORED_COLLECTION_IDENTIFIERS.stream().anyMatch(input::contains);
  }

  private static boolean containsTimestampPattern(final String input) {
    return PATTERN_TIMESTAMP.matcher(input).find();
  }
  
  public static Optional<URI> parseStr(final String s) {
    if (s == null || s.isBlank()) {
      return Optional.empty();
    }

    if (s.length() < 4) {
      return Optional.empty();
    }

    if (!s.contains(":/")) {
      return Optional.empty();
    }

    if (s.endsWith(".") || s.endsWith(".v")) {
      return Optional.empty();
    }
    
    if (Strings.isNullOrEmpty(s.substring(0, s.indexOf(':')))) {
      return Optional.empty();
    }

    // must contain a stored collection identifier or a timestamp segment:
    if (!containsStoredCollectionIdentifier(s) && !containsTimestampPattern(s)) {
      return Optional.empty();
    }

    try {
      
      return Optional.of(new URI(s));
    } catch (final URISyntaxException e) {
      return Optional.empty();
    }
  }
  
}

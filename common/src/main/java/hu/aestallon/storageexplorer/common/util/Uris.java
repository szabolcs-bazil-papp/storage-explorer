/*
 * Copyright (C) 2024 it4all Hungary Kft.
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

package hu.aestallon.storageexplorer.common.util;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import org.smartbit4all.domain.data.storage.ObjectStorageImpl;
import com.google.common.base.Strings;

public final class Uris {

  private Uris() {}


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
    return ObjectStorageImpl.getUriWithoutVersion(uri);
  }

  public static Optional<URI> parse(Object o) {
    if (o instanceof URI) {
      return Optional.of((URI) o);
    }

    if (o instanceof String) {
      try {
        final URI uri = new URI((String) o);
        return (Strings.isNullOrEmpty(uri.getScheme()) || Strings.isNullOrEmpty(uri.getPath()))
            ? Optional.empty()
            : Optional.of(uri);
      } catch (URISyntaxException e) {
        return Optional.empty();
      }
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

}

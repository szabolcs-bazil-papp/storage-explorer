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

package com.aestallon.storageexplorer.core.model.entry;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import org.springframework.util.Assert;

public final class UriProperty implements Comparable<UriProperty> {

  public sealed interface Segment {

    static Segment[] parse(final String s) {
      if (s == null || s.isEmpty()) {
        return new Segment[0];
      }

      final String[] arr = s.split("\\.");
      final Segment[] segments = new Segment[arr.length];
      for (int i = 0; i < arr.length; i++) {
        final String key = arr[i];
        try {
          final int value = Integer.parseInt(key);
          segments[i] = new Idx(value);
        } catch (NumberFormatException e) {
          segments[i] = new Key(key);
        }
      }

      return segments;
    }

    static Segment[] join(final Segment[] segments, Segment segment) {
      final Segment[] result = new Segment[segments.length + 1];
      System.arraycopy(segments, 0, result, 0, segments.length);
      result[segments.length] = segment;
      return result;
    }

    static Segment key(final String s) {
      return new Key(s);
    }

    static Segment idx(final int i) {
      return new Idx(i);
    }

    static String asString(final List<Segment> segments) {
      return asString(segments.toArray(Segment[]::new));
    }

    static String asString(final Segment[] segments) {
      return asString(segments, 0);
    }

    static String asString(final Segment[] segments, int start) {
      Objects.requireNonNull(segments, "segments cannot be null!");
      if (start < 0 || start > segments.length) {
        throw new IllegalArgumentException("start index cannot be less than or equal to zero!");
      }

      if (start == segments.length) {
        return "";
      }

      final StringBuilder sb = new StringBuilder();
      for (int i = start; i < segments.length; i++) {
        if (!sb.isEmpty()) {
          sb.append('.');
        }

        sb.append(segments[i].toString());
      }
      return sb.toString();
    }
    
    static boolean isOwnUri(Segment[] segments) {
      return segments.length == 1 && segments[0] instanceof Key(String s) && OWN.equals(s);
    }


    record Key(String value) implements Segment {

      @Override
      public String toString() {
        return value;
      }

    }


    record Idx(int value) implements Segment {

      @Override
      public String toString() {
        return String.valueOf(value);
      }

    }

  }


  public static final String OWN = "uri";

  public static UriProperty of(final Segment[] segments, final URI uri) {
    Assert.notNull(segments, "segments cannot be null!");
    Assert.notNull(uri, "uri cannot be null!");

    return new UriProperty(segments, uri);
  }

  private static OptionalInt findLastInt(Segment[] arr) {
    for (int i = arr.length - 1; i >= 0; i--) {
      if (arr[i] instanceof Segment.Idx(int value)) {
        return OptionalInt.of(value);
      }
    }
    return OptionalInt.empty();
  }

  public final Segment[] segments;
  public final URI uri;
  public final int position;

  private UriProperty(Segment[] segments, URI uri) {
    this.segments = segments;
    this.uri = uri;
    this.position = findLastInt(segments).orElse(-1);
  }

  public URI uri() {
    return uri;
  }

  public Segment[] segments() {
    return segments;
  }

  public int length() {
    return segments.length;
  }

  public boolean isStandalone() {
    return position < 0;
  }

  public String label() {
    if (isStandalone()) {
      return toString();
    }

    final String prefix;
    if (segments[segments.length - 1] instanceof Segment.Idx lastSegment) {
      final Segment[] temp = new Segment[segments.length - 1];
      System.arraycopy(segments, 0, temp, 0, segments.length - 1);
      prefix = Segment.asString(temp);
    } else {
      prefix = Segment.asString(segments);
    }
    return prefix + " (" + position + ")";
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;
    UriProperty that = (UriProperty) o;
    return Arrays.equals(segments, that.segments) && Objects.equals(uri, that.uri);
  }

  @Override
  public int hashCode() {
    return Objects.hash(Arrays.hashCode(segments), uri);
  }

  @Override
  public String toString() {
    return Segment.asString(segments) + " --> " + uri + " (" + position + ")";
  }

  @Override
  public int compareTo(UriProperty o) {
    final int min = Math.min(segments.length, o.segments.length);
    int result = 0;
    for (int i = 0; i < min; i++) {
      final var thisSegment = segments[i];
      final var thatSegment = o.segments[i];
      result = switch (thisSegment) {
        case Segment.Idx(int thisIdx) -> switch (thatSegment) {
          case Segment.Idx(int thatIdx) -> thisIdx - thatIdx;
          case Segment.Key thatKey -> -1;
        };
        case Segment.Key(String thisKey) -> switch (thatSegment) {
          case Segment.Key(String thatKey) -> thisKey.compareTo(thatKey);
          case Segment.Idx thatIdx -> 1;
        };
      };

      if (result != 0) {
        return result;
      }
    }
    return result;
  }

}

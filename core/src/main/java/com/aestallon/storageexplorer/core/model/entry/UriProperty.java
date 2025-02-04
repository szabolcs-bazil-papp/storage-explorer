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
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import org.smartbit4all.domain.annotation.property.Id;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.util.Assert;
import com.aestallon.storageexplorer.common.util.Uris;

public final class UriProperty {

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
  
  public static String join(final String a, final String b) {
    final StringBuilder sb = new StringBuilder();
    if (a != null && !a.isEmpty()) {
      sb.append(a);
    }
    
    if (b != null && !b.isEmpty()) {
      if (!sb.isEmpty()) {
        sb.append('.');
      }
      
      sb.append(b);
    }
    
    return sb.toString();
  }


  public static final String OWN = "uri";

  public static UriProperty standalone(String propertyName, URI uri) {
    Assert.notNull(propertyName, "java.lang.String propertyName must not be null!");
    Assert.notNull(uri, "java.net.URI uri must not be null!");

    return new UriProperty(propertyName, Uris.latest(uri), -1);
  }

  public static UriProperty listElement(String propertyName, URI uri, int idx) {
    Assert.notNull(propertyName, "java.lang.String propertyName must not be null!");
    Assert.notNull(uri, "java.net.URI uri must not be null!");
    if (idx < 0) {
      throw new IllegalArgumentException("idx [ " + idx + " ] must not be negative!");
    }

    return new UriProperty(propertyName, Uris.latest(uri), idx);
  }

  public static UriProperty parse(String propertyName, URI uri) {
    Assert.notNull(propertyName, "java.lang.String propertyName must not be null!");
    Assert.notNull(uri, "java.net.URI uri must not be null!");

    String[] pathElements = propertyName.split("\\.");
    final OptionalInt idx = findLastInt(pathElements);

    return (idx.isPresent())
        ? listElement(propertyName, uri, idx.getAsInt())
        : standalone(propertyName, uri);
  }

  private static OptionalInt findLastInt(String[] arr) {
    for (int i = arr.length - 1; i >= 0; i--) {
      try {
        int value = Integer.parseInt(arr[i]);
        return OptionalInt.of(value);
      } catch (NumberFormatException e) {
        // ignored
      }
    }
    return OptionalInt.empty();
  }

  private static boolean isInt(String s) {
    try {
      Integer.parseInt(s);
      return true;
    } catch (NumberFormatException e) {
      return false;
    }
  }

  public final String propertyName;
  public final URI uri;
  public final int position;

  public final Segment[] segments;

  private UriProperty(String propertyName, URI uri, int position) {
    this.propertyName = propertyName;
    this.uri = uri;
    this.position = position;
    segments = Segment.parse(propertyName);
  }

  public String propertyName() {
    return propertyName;
  }

  public URI uri() {
    return uri;
  }

  public Segment[] segments() {
    return segments;
  }

  public boolean isStandalone() {
    return position < 0;
  }

  public String label() {
    if (isStandalone()) {
      return propertyName;
    }

    final String[] pathElements = propertyName.split("\\.");
    final String prefix =
        (isInt(pathElements[pathElements.length - 1]) && propertyName.lastIndexOf('.') > 0)
            ? propertyName.substring(0, propertyName.lastIndexOf('.'))
            : propertyName;
    return prefix + " (" + position + ")";
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;
    UriProperty that = (UriProperty) o;
    return position == that.position && Objects.equals(propertyName, that.propertyName)
           && Objects.equals(uri, that.uri);
  }

  @Override
  public int hashCode() {
    return Objects.hash(propertyName, uri, position);
  }

  @Override
  public String toString() {
    return "UriProperty {" + "\n" +
           "    propertyName: '" + propertyName + '\'' + "\n" +
           "    uri: " + uri + "\n" +
           "    position: " + position + "\n" +
           "}";
  }

}

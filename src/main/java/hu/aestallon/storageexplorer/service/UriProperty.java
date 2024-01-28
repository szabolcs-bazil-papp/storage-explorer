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

package hu.aestallon.storageexplorer.service;

import java.net.URI;
import java.util.Objects;
import java.util.OptionalInt;
import org.springframework.util.Assert;

public final class UriProperty {

  static final String OWN = "uri";

  static UriProperty standalone(String propertyName, URI uri) {
    Assert.notNull(propertyName, "java.lang.String propertyName must not be null!");
    Assert.notNull(uri, "java.net.URI uri must not be null!");

    return new UriProperty(propertyName, uri, -1);
  }

  static UriProperty listElement(String propertyName, URI uri, int idx) {
    Assert.notNull(propertyName, "java.lang.String propertyName must not be null!");
    Assert.notNull(uri, "java.net.URI uri must not be null!");
    if (idx < 0) {
      throw new IllegalArgumentException("idx [ " + idx + " ] must not be negative!");
    }

    return new UriProperty(propertyName, uri, idx);
  }

  static UriProperty parse(String propertyName, URI uri) {
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

  final String propertyName;
  final URI uri;
  final int position;

  private UriProperty(String propertyName, URI uri, int position) {
    this.propertyName = propertyName;
    this.uri = uri;
    this.position = position;
  }

  boolean isStandalone() {
    return position < 0;
  }

  String label() {
    if (isStandalone()) {
      return propertyName;
    }

    final String[] pathElements = propertyName.split("\\.");
    final String prefix = (isInt(pathElements[pathElements.length - 1]))
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

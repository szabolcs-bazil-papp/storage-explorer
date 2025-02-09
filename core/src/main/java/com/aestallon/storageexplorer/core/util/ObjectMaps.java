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


import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import com.aestallon.storageexplorer.common.util.Pair;
import com.aestallon.storageexplorer.core.model.entry.UriProperty;

public final class ObjectMaps {

  private ObjectMaps() {}

  public static Stream<Pair<UriProperty.Segment[], Object>> flatten(Map<String, Object> m) {
    return m.entrySet().stream()
        .map(Pair::of)
        .map(Pair.onA(s -> new UriProperty.Segment[] { UriProperty.Segment.key(s) }))
        .flatMap(ObjectMaps::flattenToPrimitivePair);
  }

  private static Stream<Pair<UriProperty.Segment[], Object>> flattenToPrimitivePair(
      final Pair<UriProperty.Segment[], Object> e) {
    final UriProperty.Segment[] property = e.a();
    final Object value = e.b();

    if (value instanceof Map<?, ?>) {
      @SuppressWarnings({ "unchecked" })
      final Map<String, Object> m = (Map<String, Object>) value;
      return m.entrySet().stream()
          .map(Pair::of)
          .map(Pair.onA(it -> UriProperty.Segment.join(property, UriProperty.Segment.key(it))))
          .flatMap(ObjectMaps::flattenToPrimitivePair);
    }

    if (value instanceof List<?>) {
      @SuppressWarnings({ "unchecked" })
      final List<Object> l = (List<Object>) value;
      return IntStream.range(0, l.size())
          .mapToObj(i -> Pair.of(
              UriProperty.Segment.join(property, UriProperty.Segment.idx(i)),
              l.get(i)))
          .flatMap(ObjectMaps::flattenToPrimitivePair);
    }

    if (value == null) {
      return Stream.of(Pair.of(property, "NULL"));
    }

    return Stream.of(e);
  }

}

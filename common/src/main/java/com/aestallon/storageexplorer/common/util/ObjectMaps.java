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

package com.aestallon.storageexplorer.common.util;


import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.smartbit4all.core.utility.StringConstant;

public final class ObjectMaps {

  private ObjectMaps() {}

  public static Map<String, Object> flatten(Map<String, Object> m) {
    return m.entrySet().stream()
        .map(Pair::of)
        .flatMap(ObjectMaps::flattenToPrimitivePair)
        .collect(Pair.toMap());
  }

  private static Stream<Pair<String, Object>> flattenToPrimitivePair(Pair<String, Object> e) {
    final String property = e.a();
    final Object value = e.b();

    if (value instanceof Map<?, ?>) {
      @SuppressWarnings({"unchecked"})
      final Map<String, Object> m = (Map<String, Object>) value;
      return m.entrySet().stream()
          .map(Pair::of)
          .map(Pair.onA(it -> property + StringConstant.DOT + it))
          .flatMap(ObjectMaps::flattenToPrimitivePair);
    }

    if (value instanceof List<?>) {
      @SuppressWarnings({"unchecked"})
      final List<Object> l = (List<Object>) value;
      return IntStream.range(0, l.size())
          .mapToObj(i -> Pair.of(property + StringConstant.DOT + i, l.get(i)))
          .flatMap(ObjectMaps::flattenToPrimitivePair);
    }

    if (value == null) {
      return Stream.of(Pair.of(property, "NULL"));
    }

    return Stream.of(e);
  }

}

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
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import com.aestallon.storageexplorer.common.util.Pair;
import com.aestallon.storageexplorer.core.model.entry.UriProperty;
import com.aestallon.storageexplorer.core.model.type.EntityType;
import com.aestallon.storageexplorer.core.model.type.Property;
import com.aestallon.storageexplorer.core.model.type.PropertyType;

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

    if (value instanceof List<?> l) {
      return IntStream.range(0, l.size())
          .mapToObj(i -> Pair.of(
              UriProperty.Segment.join(property, UriProperty.Segment.idx(i)),
              (Object) l.get(i)))
          .flatMap(ObjectMaps::flattenToPrimitivePair);
    }

    if (value == null) {
      return Stream.of(Pair.of(property, "NULL"));
    }

    return Stream.of(e);
  }

  public static EntityType entityTypeOf(String name, Map<String, Object> m) {
    final var type = new EntityType(name, propertiesOf(m));
    type.properties().removeIf(it -> UriProperty.OWN.equals(it.key()));
    return type;
  }

  static List<Property> propertiesOf(Map<String, Object> m) {
    return m.entrySet().stream()
        .map(e -> new Property(e.getKey(), typeOf(e.getValue())))
        .collect(Collectors.toList());
  }

  static PropertyType typeOf(Object o) {
    return switch (o) {
      case null -> PropertyType.NULL;
      case String s -> Uris.parseStr(s)
          .map(Uris::getTypeName)
          .<PropertyType>map(it -> new PropertyType.Ref(it, PropertyType.Arity.ONE))
          .orElse(PropertyType.STR);
      case URI uri -> new PropertyType.Ref(Uris.getTypeName(uri), PropertyType.Arity.ONE);
      case Number n -> PropertyType.NUM;
      case Boolean b -> PropertyType.BOOL;
      case List<?> l -> typeOfList(l);
      case Map<?, ?> m -> {
        @SuppressWarnings({ "unchecked" })
        final Map<String, Object> m2 = (Map<String, Object>) m;
        yield new PropertyType.Complex(propertiesOf(m2), PropertyType.Arity.ONE);
      }
      default -> throw new IllegalArgumentException("unsupported type: " + o.getClass());
    };
  }

  static PropertyType typeOfList(List<?> list) {
    final var elementProps = new LinkedHashSet<PropertyType>();
    for (final var e : list) {
      elementProps.add(typeOf(e));
    }

    if (elementProps.isEmpty()) {
      // TODO: Make a distinct type for UNKNOWN and its list version:
      return PropertyType.NULL;
    } else if (elementProps.size() == 1) {
      return elementProps.getFirst().withArity(PropertyType.Arity.MANY);
    } else {
      return new PropertyType.Union(new ArrayList<>(elementProps), PropertyType.Arity.MANY);
    }
  }

}

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

package com.aestallon.storageexplorer.core.model.type;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import com.google.common.base.Strings;

public record EntityType(String name, List<Property> properties)
    implements PropertyHolder, StructuredType {

  private static String joinDot(String a, String b) {
    if (Strings.isNullOrEmpty(a)) {
      return b;
    }

    if (Strings.isNullOrEmpty(b)) {
      return a;
    }

    return a + "." + b;
  }

  @Override
  public Set<Association> associations() {
    final Set<Association> associations = new HashSet<>();
    properties
        .stream()
        .map(it -> collectAssociations("", it))
        .forEach(associations::addAll);
    return associations;
  }

  private Set<Association> collectAssociations(String prefix, Property p) {
    final String pKey = p.key();
    return collectAssociations(prefix, pKey, p.type());
  }

  private Set<Association> collectAssociations(String prefix, String pKey, PropertyType pType) {
    return switch (pType) {
      case PropertyType.Primitive primitive -> Collections.emptySet();
      case PropertyType.EmptyArray ea -> Collections.emptySet();
      case PropertyType.Ref(var entityName, var arity) -> Set.of(new Association(
          name,
          entityName,
          arity,
          joinDot(prefix, pKey)));
      case PropertyType.Complex complex -> complex.properties().stream()
          .map(it -> collectAssociations(joinDot(prefix, pKey), it))
          .flatMap(Set::stream)
          .collect(Collectors.toSet());
      case PropertyType.Union union -> union.types().stream()
          .map(it -> collectAssociations(prefix, pKey, it))
          .flatMap(Set::stream)
          .map(it -> it.withOptional(true))
          .collect(Collectors.toSet());
    };
  }

  @Override
  public StructuredType amend(StructuredType other) {
    return switch (other) {
      case Unknown unknown -> this;
      case EntityType entity -> merge(entity);
    };
  }

  private EntityType merge(EntityType other) {
    final var lhsProps = new LinkedHashMap<String, PropertyType>();
    properties().forEach(it -> lhsProps.put(it.key(), it.type()));
    final var rhsProps = new LinkedHashMap<String, PropertyType>();
    other.properties().forEach(it -> rhsProps.put(it.key(), it.type()));
    final var sharedKeys = lhsProps.keySet().stream()
        .filter(rhsProps::containsKey)
        .collect(Collectors.toSet());
    return new EntityType(name, mergePropList(lhsProps, rhsProps, sharedKeys));
  }

  static List<Property> mergePropList(LinkedHashMap<String, PropertyType> lhsProps,
                            LinkedHashMap<String, PropertyType> rhsProps, Set<String> sharedKeys) {
    final var allProps = new LinkedHashSet<>(lhsProps.sequencedKeySet());
    allProps.addAll(rhsProps.sequencedKeySet());

    final var newProps = new ArrayList<Property>();
    for (final var k : allProps) {
      if (sharedKeys.contains(k)) {
        final var l = new Property(k, lhsProps.get(k));
        final var r = rhsProps.get(k);
        newProps.add(l.merge(r));
      } else if (lhsProps.containsKey(k)) {
        final var l = new Property(k, lhsProps.get(k));
        newProps.add(l.merge(PropertyType.NULL));
      } else {
        final var r = new Property(k, rhsProps.get(k));
        newProps.add(r.merge(PropertyType.NULL));
      }
    }

    return newProps;
  }

}

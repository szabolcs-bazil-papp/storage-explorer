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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Collectors;
import com.aestallon.storageexplorer.common.util.NotImplementedException;

public record Property(String key, PropertyType type) {

  public PropertyType.Arity arity() {
    return type.arity();
  }

  Property merge(final PropertyType t) {
    if (type.equals(t)) {
      return this;
    }

    final List<PropertyType> unionTypes = new ArrayList<>();
    final PropertyType.Arity unionArity;
    if (PropertyType.Arity.MANY == type.arity() && PropertyType.Arity.MANY == t.arity()) {
      // case 1: List<A>              -- List<B: A | X> --> B is a superset of A, we can return B
      // case 2: List<A: X | B>       -- List<B>        --> A is a superset of B, we can return A
      // case 3: List<A>              -- List<B>        --> A and B are disjunct, return List<A> | List<B>
      // case 4: A: List<X> | List<Y> -- B: List<X | Y> --> widen!
    } else if (PropertyType.Arity.MANY == type.arity() || PropertyType.Arity.MANY == t.arity()) {
      // we either have A | List<B>, or List<A> | B --> return their union with arity ONE
      final var props = new ArrayList<PropertyType>();
      props.add(type);
      props.add(t);
      return new Property(key, new PropertyType.Union(props, PropertyType.Arity.ONE));
    } else {

      return switch (type) {
        case PropertyType.Union u1 when u1.isWiderThan(t) -> new Property(key, u1);
        case PropertyType.Union u1 -> switch (t) {
          case PropertyType.Union u2 when u2.isWiderThan(u1) -> new Property(key, u2);
          case PropertyType.Union u2 -> {
            final var props = new LinkedHashSet<PropertyType>();
            props.addAll(u1.types());
            props.addAll(u2.types());
            yield new Property(
                key,
                new PropertyType.Union(new ArrayList<>(props),
                    PropertyType.Arity.ONE));
          }
          default -> {
            final var props = new ArrayList<>(u1.types());
            props.add(t);
            yield new Property(key, new PropertyType.Union(props, PropertyType.Arity.ONE));
          }
        };
        default -> switch (t) {
          case PropertyType.Union u2 when u2.isWiderThan(type) -> new Property(key, u2);
          case PropertyType.Union u2 -> {
            final var props = new ArrayList<>(u2.types());
            props.add(type);
            yield new Property(key, new PropertyType.Union(props, PropertyType.Arity.ONE));
          }
          default -> {
            final var props = new ArrayList<PropertyType>();
           if (type instanceof PropertyType.Complex c1 && t instanceof PropertyType.Complex c2) {
             final var c1Keys = c1.properties().stream()
                 .map(Property::key)
                 .collect(Collectors.toSet());
             final var c2Keys = c2.properties().stream()
                 .map(Property::key)
                 .collect(Collectors.toSet());
             if (c1Keys.stream().anyMatch(c2Keys::contains)) {
                yield mergeComplex(c1, c2);
             }
           }

            props.add(type);
            props.add(t);
            yield new Property(key, new PropertyType.Union(props, PropertyType.Arity.ONE));
          }
        };

      };

    }
    throw new NotImplementedException("type union not implemented yet");
  }

  private Property mergeComplex(PropertyType.Complex c1, PropertyType.Complex c2) {
    final var allProps = new LinkedHashMap<String, Property>();
    for (var p : c1.properties()) {
      allProps.put(p.key(), p);
    }

    for (var p : c2.properties()) {
      final var existingProp = allProps.get(p.key());
      if (existingProp != null) {
        allProps.put(p.key(), existingProp.merge(p.type()));
      } else {
        allProps.put(p.key(), p);
      }
    }

    return new Property(
        key,
        new PropertyType.Complex(new ArrayList<>(allProps.values()), arity()));
  }

  @Override
  public String toString() {
    return key + ": " + type;
  }
}

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

  // battle plan
  //
  // I. EMPTY ARR handling
  // if lhs && rhs == EMPTY_ARR -> EMPTY_ARR
  // if one side is EMPTY_ARR and the other is any ARR, we return the other
  // if one side is EMPTY_ARR, and rhs is a union containing the EMPTY_ARR, we return rhs
  // if one side is EMPTY_ARR, and rhs is a union containing any ARR, we return rhs,
  // if one side is EMPTY_ARR, and rhs is a union without any ARR, we return rhs added EMPTY_ARR
  // if one side is any non-empty ARR, and rhs is a union containing EMPTY_ARR, we return rhs with
  //     EMPTY_ARR removed
  //
  // II. Any side is primitive or ref
  // for primitive x primitive, ref x ref and primitive x ref we check if they are equal, or we
  //     return their union.
  // for primitive x complex and ref x complex, we always return the union
  // for primitive x union and ref x union, we check if lhs is a component in the union, and add it
  //     if missing
  //
  //
  // III: Complexes and Unions
  // alpha: for complex x complex
  // A) Both are ARITY ONE
  //   1. check if they share at least 1 property key. if they do, then
  //     - return a new complex, where the shared properties' types are merged
  //     - the rest of the properties are merged with NULL
  //   2. If no property keys are shared, return a union of (lhs | rhs)
  // B) Both are ARITY MANY
  //    we actually do the same: if at least one key is shared, we shall SUPPOSE that these lists
  //    are actually of the same type, but one list was consistently filled out with only a subset
  //        of properties, and another with a different subset. If no property keys are shared, we
  //        must assume they are distinct types.
  // C) Their arity is different:
  //   return a union of (lhs | rhs)
  //
  // beta: for complex x union
  // A) C x U
  //   1. Filter the U's types to their complex components of arity ONE C1, C2...named Uc
  //   2. if a complex type Ci exists in Uc such that C and Ci share at least 1 property:
  //     - remove Ci from Uc
  //     - merge C and Ci resulting in Ca
  //     - repeat step 2 until no element can be removed and merged from Uc
  //     - add the resulting complex type back to Uc
  //     - replace the complex components of U with the elements of Uc
  // B) C x [U]
  //   return their union: C | [U]
  // C) [C] x U
  //   filter out U's type to their complex components of arity many [C1], [C2]... do as point A)
  // D) [C] x [U]
  //   do as point A) -> filter out the complex components of arity ONE C1, C2 and merge with them
  // Points C) and D) follow the same reasoning as III/alpha/B.
  //
  // gamma: for union x union
  // A) U x V
  //   - extract all primitives and refs Up1, Up2... from U and Vp1, Vp2... from V regardless
  //       of arity
  //   - every distinct element is a member of the result
  //   - extract all arity ONE complex components and merge them as written above
  //   - extract all arity MANY complex components and merge...
  //   - nested unions:
  //      str |
  // B) [U] x V
  // C) [U] x [V]

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

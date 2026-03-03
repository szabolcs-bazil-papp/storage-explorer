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

  // private static final Logger log = LoggerFactory.getLogger(Property.class);

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
  //      we cannot have nested unions in this case as every new member is incorporated one by one,
  //      or by the above method, component by component
  // B) [U] x V
  //   - is [U] a component of V? -> return V: [str | num] x (str | num) -> str | num | [str | num]
  //   - else return the union: (V1 | V2 | ... | [U])
  // C) [U] x [V]
  //   - return the raw union as arity one: [str | num] x [str | bool] -> [ [str|num] | [str|bool] ]
  //     as the above example demonstrates, it might be possible to simplify the yielded union, but
  //     that is for a later release...
  public Property merge(final PropertyType other) {
    if (type.equals(other)) {
      return this;
    }

    if (type instanceof PropertyType.EmptyArray ea) {
      return mergeWithEmptyArray(ea, other);
    }

    if (other instanceof PropertyType.EmptyArray ea) {
      return mergeWithEmptyArray(ea, type);
    }

    if (type instanceof PropertyType.Primitive p) {
      return mergeWithPrimitive(p, other);
    }

    if (other instanceof PropertyType.Primitive p) {
      return mergeWithPrimitive(p, type);
    }

    if (type instanceof PropertyType.Ref r) {
      return mergeWithRef(r, other);
    }

    if (other instanceof PropertyType.Ref r) {
      return mergeWithRef(r, type);
    }

    return switch (type) {
      case PropertyType.Complex lhs when other instanceof PropertyType.Complex rhs ->
          mergeComplexes(lhs, rhs);
      case PropertyType.Complex c when other instanceof PropertyType.Union u ->
          mergeComplexWithUnion(c, u);
      case PropertyType.Union u when other instanceof PropertyType.Complex c ->
          mergeComplexWithUnion(c, u);
      case PropertyType.Union lhs when other instanceof PropertyType.Union rhs ->
          mergeUnions(lhs, rhs);
      default -> {
        // log.warn("Uncovered merge case: {} + {}", type, other);
        throw new NotImplementedException("Uncovered merge case: " + type + " + " + other);
      }
    };
  }

  // I. EMPTY ARR handling
  // if lhs && rhs == EMPTY_ARR -> EMPTY_ARR
  // if one side is EMPTY_ARR and the other is any ARR, we the other
  // if one side is EMPTY_ARR, and rhs is a union containing the EMPTY_ARR, we rhs
  // if one side is EMPTY_ARR, and rhs is a union containing any ARR, we rhs,
  // if one side is EMPTY_ARR, and rhs is a union without any ARR, we rhs added EMPTY_ARR
  private Property mergeWithEmptyArray(PropertyType.EmptyArray lhs, PropertyType rhs) {
    if (rhs instanceof PropertyType.EmptyArray) {
      return new Property(key, rhs);
    }

    if (rhs.arity() == PropertyType.Arity.MANY) {
      return new Property(key, rhs);
    }

    if (rhs instanceof PropertyType.Union u && u.types().stream()
        .anyMatch(it -> PropertyType.Arity.MANY == it.arity())) {
      // this covers when rhs has the EMPTY_ARR or any other arr as a component:
      return new Property(key, u);
    }

    if (rhs instanceof PropertyType.Union u) {
      final var components = new ArrayList<>(u.types());
      components.add(lhs);
      return new Property(key, new PropertyType.Union(components, PropertyType.Arity.ONE));
    }

    return new Property(key, new PropertyType.Union(List.of(lhs, rhs), PropertyType.Arity.ONE));
  }

  private Property mergeWithPrimitive(PropertyType.Primitive lhs, PropertyType rhs) {
    return mergeWithPrimitiveOrRef(lhs, rhs);
  }

  private Property mergeWithRef(PropertyType.Ref lhs, PropertyType rhs) {
    return mergeWithPrimitiveOrRef(lhs, rhs);
  }

  // II. Any side is primitive or ref
  // for primitive x primitive, ref x ref and primitive x ref we check if they are equal, or we
  //     return their union.
  // for primitive x complex and ref x complex, we always return the union
  // for primitive x union and ref x union, we check if lhs is a component in the union, and add it
  //     if missing

  // if one side is any non-empty ARR, and rhs is a union containing EMPTY_ARR, we return rhs with
  //     EMPTY_ARR removed
  private Property mergeWithPrimitiveOrRef(PropertyType lhs, PropertyType rhs) {
    assert lhs instanceof PropertyType.Primitive || lhs instanceof PropertyType.Ref;

    if (rhs instanceof PropertyType.Primitive || rhs instanceof PropertyType.Ref) {
      if (lhs.equals(rhs)) {
        return this;
      } else {
        return new Property(key, new PropertyType.Union(List.of(lhs, rhs), PropertyType.Arity.ONE));
      }
    }

    if (rhs instanceof PropertyType.EmptyArray && PropertyType.Arity.MANY == lhs.arity()) {
      return this;
    }

    if (rhs instanceof PropertyType.Complex c) {
      return new Property(key, new PropertyType.Union(List.of(lhs, c), PropertyType.Arity.ONE));
    }

    if (rhs instanceof PropertyType.Union u) {
      if (u.types().stream().anyMatch(it -> it.equals(lhs))) {
        return new Property(key, u);
      }

      final var components = new ArrayList<>(u.types());
      if (lhs.arity() == PropertyType.Arity.MANY && components.stream().anyMatch(
          PropertyType.EmptyArray.class::isInstance)) {
        components.removeIf(PropertyType.EmptyArray.class::isInstance);
      }

      components.add(lhs);
      return new Property(key, new PropertyType.Union(components, PropertyType.Arity.ONE));
    }

    throw new AssertionError("Uncovered merge case: " + lhs + " + " + rhs);
  }

  // alpha: for complex x complex
  // A) Both are ARITY ONE
  //   1. check if they share at least 1 property key. if they do, then
  //     - return a new complex, where the shared properties' types are merged
  //     - the rest of the properties are merged with NULL
  //   2. If no property keys are shared, return a union of (lhs | rhs)
  // B) Both are ARITY MANY
  //    we actually do the same: if at least one key is shared, we shall SUPPOSE that these lists
  //      are actually of the same type, but one list was consistently filled out with only a subset
  //      of properties, and another with a different subset. If no property keys are shared, we
  //      must assume they are distinct types.
  // C) Their arity is different:
  //   return a union of (lhs | rhs)
  private Property mergeComplexes(PropertyType.Complex lhs, PropertyType.Complex rhs) {
    final boolean arityLhs = lhs.isArityOne();
    final boolean arityRhs = rhs.isArityOne();
    if ((arityLhs && arityRhs) || (!arityLhs && !arityRhs)) {
      final var lhsProps = new LinkedHashMap<String, PropertyType>();
      lhs.properties().forEach(it -> lhsProps.put(it.key(), it.type()));
      final var rhsProps = new LinkedHashMap<String, PropertyType>();
      rhs.properties().forEach(it -> rhsProps.put(it.key(), it.type()));
      final var sharedKeys = lhsProps.keySet().stream()
          .filter(rhsProps::containsKey)
          .collect(Collectors.toSet());
      if (sharedKeys.isEmpty()) {
        return new Property(key, new PropertyType.Union(List.of(lhs, rhs), PropertyType.Arity.ONE));
      }

      final var newProps = EntityType
          .mergePropList(lhsProps, rhsProps, sharedKeys)
          .toArray(Property[]::new);
      return new Property(key, arityLhs
          ? PropertyType.ofComplex(newProps)
          : PropertyType.ofComplexArray(newProps));

    } else {
      return new Property(key, new PropertyType.Union(List.of(lhs, rhs), PropertyType.Arity.ONE));
    }
  }

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
  private Property mergeComplexWithUnion(PropertyType.Complex lhs, PropertyType.Union rhs) {
    final var complexOne = lhs.isArityOne();
    final var unionOne = lhs.isArityOne();
    if (!complexOne || unionOne) {
      final var candidateArity = (complexOne) || !unionOne
          ? PropertyType.Arity.ONE
          : PropertyType.Arity.MANY;
      final var components = new ArrayList<>(rhs.types());
      final List<PropertyType.Complex> candidates = rhs.types().stream()
          .filter(PropertyType.Complex.class::isInstance)
          .map(PropertyType.Complex.class::cast)
          .filter(it -> candidateArity == it.arity())
          .collect(Collectors.toList());
      components.removeAll(candidates);
      candidates.add(lhs);

      final var reduced = reduce(candidates);
      components.addAll(reduced);
      if (components.size() == 1) {
        return new Property(key, components.getFirst());
      } else {
        return new Property(key, new PropertyType.Union(components, rhs.arity()));
      }
    } else {
      // TODO: This is iffy, we'd love to avoid nested unions:
      return new Property(key, new PropertyType.Union(List.of(lhs, rhs), PropertyType.Arity.ONE));
    }
  }

  private List<PropertyType.Complex> reduce(List<PropertyType.Complex> cs) {
    if (cs.size() < 2) {
      return cs;
    }

    for (int i = 0; i < cs.size(); i++) {
      for (int j = i + 1; j < cs.size(); j++) {
        final var ci = cs.get(i);
        final var cj = cs.get(j);
        final var temp = new Property("_", ci);
        final var merged = temp.merge(cj);
        if (!(merged.type instanceof PropertyType.Complex c)) {
          // they could not be merged, skip:
          continue;
        }

        // they were merged!
        final var nextRound = new ArrayList<>(cs);
        nextRound.remove(j);
        nextRound.remove(i);
        nextRound.add(c);
        return reduce(nextRound);
      }
    }

    // we couldn't do anything, return:
    return cs;
  }

  // gamma: for union x union
  // A) U x V
  //   - extract all primitives and refs Up1, Up2... from U and Vp1, Vp2... from V regardless
  //       of arity
  //   - every distinct element is a member of the result
  //   - extract all arity ONE complex components and merge them as written above
  //   - extract all arity MANY complex components and merge...
  //   - nested unions:
  //      we cannot have nested unions in this case as every new member is incorporated one by one,
  //      or by the above method, component by component
  // B) [U] x V
  //   - is [U] a component of V? -> return V: [str | num] x (str | num) -> str | num | [str | num]
  //   - else return the union: (V1 | V2 | ... | [U])
  // C) [U] x [V]
  //   - return the raw union as arity one: [str | num] x [str | bool] -> [ [str|num] | [str|bool] ]
  //     as the above example demonstrates, it might be possible to simplify the yielded union, but
  //     that is for a later release...
  private Property mergeUnions(PropertyType.Union lhs, PropertyType.Union rhs) {
    final var lhsOne = lhs.isArityOne();
    final var rhsOne = rhs.isArityOne();

    if (lhsOne && rhsOne) {
      final var result = new LinkedHashSet<PropertyType>();
      final var candidates = new ArrayList<>(lhs.types());
      candidates.addAll(rhs.types());
      if (candidates.stream().anyMatch(PropertyType.EmptyArray.class::isInstance) && candidates.stream().anyMatch(it -> !it.isArityOne() && !(it instanceof PropertyType.EmptyArray))) {
        candidates.removeIf(PropertyType.EmptyArray.class::isInstance);
      }

      final var complexOnes = new ArrayList<PropertyType.Complex>();
      final var complexMany = new ArrayList<PropertyType.Complex>();
      for (final var it : candidates) {
        if (it instanceof PropertyType.Complex c) {
          if (c.isArityOne()) {
            complexOnes.add(c);
          } else {
            complexMany.add(c);
          }
        } else {
          result.add(it);
        }
      }

      result.addAll(reduce(complexOnes));
      result.addAll(reduce(complexMany));
      if (result.size() == 1) {
        return new Property(key, result.getFirst());
      } else {
        return new Property(key, new PropertyType.Union(new ArrayList<>(result), rhs.arity()));
      }

    } else if (!(lhsOne || rhsOne)) {
      return new Property(key, new PropertyType.Union(List.of(lhs, rhs), PropertyType.Arity.ONE));
    } else if (lhsOne) {
      if (rhs.types().contains(lhs)) {
        return new Property(key, rhs);
      } else {
        return new Property(key, PropertyType.union(lhs, rhs));
      }
    } else {
      if (lhs.types().contains(rhs)) {
        return new Property(key, lhs);
      } else {
        return new Property(key, PropertyType.union(rhs, lhs));
      }
    }

  }

  @Override
  public String toString() {
    return key + ": " + type;
  }
}

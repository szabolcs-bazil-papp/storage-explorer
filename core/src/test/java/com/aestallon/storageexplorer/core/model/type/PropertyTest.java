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

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static com.aestallon.storageexplorer.core.model.type.PropertyType.union;

class PropertyTest {

  private static Property prop(final String name, final PropertyType type) {
    return new Property(name, type);
  }

  private static PropertyType complex(final Property... properties) {
    return PropertyType.ofComplex(properties);
  }


  @Test
  @DisplayName("str + str -> str")
  void strPlusStr() {
    final var a = prop("foo", PropertyType.Primitive.STR);
    final var res = a.merge(PropertyType.Primitive.STR);
    assertThat(res.type())
        .describedAs(() -> "str + str -> str")
        .isEqualTo(PropertyType.Primitive.STR);
  }

  @Test
  @DisplayName("num + num -> num")
  void numPlusNum() {
    var a = prop("foo", PropertyType.Primitive.NUM);
    var res = a.merge(PropertyType.Primitive.NUM);
    assertThat(res.type()).isEqualTo(PropertyType.Primitive.NUM);
  }

  @Test
  @DisplayName("null + null -> null")
  void nullPlusNull() {
    var a = prop("foo", PropertyType.Primitive.NULL);
    var res = a.merge(PropertyType.Primitive.NULL);
    assertThat(res.type()).isEqualTo(PropertyType.Primitive.NULL);
  }

  @Test
  @DisplayName("[str] + [str] -> [str]")
  void strArrayPlusStrArray() {
    var a = prop("foo", PropertyType.Primitive.STR_ARRAY);
    var res = a.merge(PropertyType.Primitive.STR_ARRAY);
    assertThat(res.type()).isEqualTo(PropertyType.Primitive.STR_ARRAY);
  }

  @Test
  @DisplayName("str + num -> str | num")
  void strPlusNum() {
    var a = prop("foo", PropertyType.Primitive.STR);
    var res = a.merge(PropertyType.Primitive.NUM);
    assertThat(res.type()).isEqualTo(union(PropertyType.STR, PropertyType.NUM));
  }

  @Test
  @DisplayName("str + null -> str | null")
  void strPlusNull() {
    var a = prop("foo", PropertyType.Primitive.STR);
    var res = a.merge(PropertyType.Primitive.NULL);
    assertThat(res.type()).isEqualTo(union(PropertyType.STR, PropertyType.NULL));
  }

  @Test
  @DisplayName("num + null -> num | null")
  void numPlusNull() {
    var a = prop("foo", PropertyType.Primitive.NUM);
    var res = a.merge(PropertyType.Primitive.NULL);
    assertThat(res.type()).isEqualTo(union(PropertyType.NUM, PropertyType.NULL));
  }

  @Test
  @DisplayName("str + [str] -> str | [str]")
  void strPlusStrArray() {
    var a = prop("foo", PropertyType.Primitive.STR);
    var res = a.merge(PropertyType.Primitive.STR_ARRAY);
    assertThat(res.type()).isEqualTo(union(PropertyType.STR, PropertyType.STR_ARRAY));
  }

  @Test
  @DisplayName("[str] + str -> [str] | str  (order is preserved)")
  void strArrayPlusStr() {
    var a = prop("foo", PropertyType.Primitive.STR_ARRAY);
    var res = a.merge(PropertyType.Primitive.STR);
    assertThat(res.type()).isEqualTo(union(PropertyType.STR_ARRAY, PropertyType.STR));
  }

  @Test
  @DisplayName("num + [str] -> num | [str]")
  void numPlusStrArray() {
    var a = prop("foo", PropertyType.Primitive.NUM);
    var res = a.merge(PropertyType.Primitive.STR_ARRAY);
    assertThat(res.type()).isEqualTo(union(PropertyType.NUM, PropertyType.STR_ARRAY));
  }

  @Test
  @DisplayName("str + (str | null) -> str | null  [left already member]")
  void strAlreadyInUnion() {
    var a = prop("foo", PropertyType.Primitive.STR);
    var rhs = union(PropertyType.STR, PropertyType.NULL);
    var res = a.merge(rhs);
    assertThat(res.type()).isEqualTo(union(PropertyType.STR, PropertyType.NULL));
  }

  @Test
  @DisplayName("str + (num | null) -> str | num | null  [left is new]")
  void strNewToUnion() {
    var a = prop("foo", PropertyType.Primitive.STR);
    var rhs = union(PropertyType.NUM, PropertyType.NULL);
    var res = a.merge(rhs);
    assertThat(res.type())
        .isEqualTo(union(PropertyType.STR, PropertyType.NUM, PropertyType.NULL));
  }

  @Test
  @DisplayName("(str | null) + str -> str | null  [right already member]")
  void unionAbsorbsExistingPrimitive() {
    var a = prop("foo", union(PropertyType.STR, PropertyType.NULL));
    var res = a.merge(PropertyType.Primitive.STR);
    assertThat(res.type()).isEqualTo(union(PropertyType.STR, PropertyType.NULL));
  }

  @Test
  @DisplayName("(str | null) + num -> str | null | num  [right is new]")
  void unionGrowsWithNewPrimitive() {
    var a = prop("foo", union(PropertyType.STR, PropertyType.NULL));
    var res = a.merge(PropertyType.Primitive.NUM);
    assertThat(res.type())
        .isEqualTo(union(PropertyType.STR, PropertyType.NULL, PropertyType.NUM));
  }

  @Test
  @DisplayName("(str | [str]) + [str] -> str | [str]  [array already member]")
  void foo4_unionAbsorbsExistingArray() {
    var a = prop("foo", union(PropertyType.STR, PropertyType.STR_ARRAY));
    var res = a.merge(PropertyType.Primitive.STR_ARRAY);
    assertThat(res.type()).isEqualTo(union(PropertyType.STR, PropertyType.STR_ARRAY));
  }

  @Test
  @DisplayName("(str | null) + (str | null) -> str | null  [identical]")
  void identicalUnions() {
    var a = prop("foo", union(PropertyType.STR, PropertyType.NULL));
    var rhs = union(PropertyType.STR, PropertyType.NULL);
    var res = a.merge(rhs);
    assertThat(res.type()).isEqualTo(union(PropertyType.STR, PropertyType.NULL));
  }

  @Test
  @DisplayName("(str | null) + (num | null) -> str | null | num  [partial overlap]")
  void partialOverlapUnions() {
    var a = prop("foo", union(PropertyType.STR, PropertyType.NULL));
    var rhs = union(PropertyType.NUM, PropertyType.NULL);
    var res = a.merge(rhs);
    assertThat(res.type())
        .isEqualTo(union(PropertyType.STR, PropertyType.NULL, PropertyType.NUM));
  }

  @Test
  @DisplayName("(str | num) + (null | [str]) -> str | num | null | [str]  [disjoint]")
  void disjointUnions() {
    var a = prop("foo", union(PropertyType.STR, PropertyType.NUM));
    var rhs = union(PropertyType.NULL, PropertyType.STR_ARRAY);
    var res = a.merge(rhs);
    assertThat(res.type())
        .isEqualTo(union(PropertyType.STR, PropertyType.NUM, PropertyType.NULL,
            PropertyType.STR_ARRAY));
  }

  @Test
  @DisplayName("(str | null) + (str | num | null) -> str | null | num  [left is subset]")
  void leftSubsetOfRight() {
    var a = prop("foo", union(PropertyType.STR, PropertyType.NULL));
    var rhs = union(PropertyType.STR, PropertyType.NUM, PropertyType.NULL);
    var res = a.merge(rhs);
    assertThat(res.type())
        .isEqualTo(union(PropertyType.STR, PropertyType.NULL, PropertyType.NUM));
  }

  @Test
  @DisplayName("(str | [str]) + null -> str | [str] | null  [null is new]")
  void unionGrowsWithNull() {
    var a = prop("foo", union(PropertyType.STR, PropertyType.STR_ARRAY));
    var res = a.merge(PropertyType.Primitive.NULL);
    assertThat(res.type())
        .isEqualTo(union(PropertyType.STR, PropertyType.STR_ARRAY, PropertyType.NULL));
  }

  @Test
  @DisplayName("str + { a: str } -> str | { a: str }")
  void foo5_strPlusComplex() {
    var left = prop("foo", PropertyType.Primitive.STR);
    var right = PropertyType.ofComplex(prop("a", PropertyType.Primitive.STR));
    var res = left.merge(right);
    assertThat(res.type()).isEqualTo(union(PropertyType.STR, right));
  }

  @Test
  @DisplayName("null + { a: str } -> null | { a: str }")
  void nullPlusComplex() {
    var left = prop("foo", PropertyType.Primitive.NULL);
    var right = PropertyType.ofComplex(prop("a", PropertyType.Primitive.STR));
    var res = left.merge(right);
    assertThat(res.type()).isEqualTo(union(PropertyType.NULL, right));
  }


  @Test
  @DisplayName("[str] + { a: str } -> [str] | { a: str }")
  void arrayPlusComplex() {
    var left = prop("foo", PropertyType.Primitive.STR_ARRAY);
    var right = PropertyType.ofComplex(prop("a", PropertyType.Primitive.STR));
    var res = left.merge(right);
    assertThat(res.type()).isEqualTo(union(PropertyType.STR_ARRAY, right));
  }

  @Test
  @DisplayName("{ a: str } + str -> { a: str } | str")
  void complexPlusPrimitive() {
    var left = prop("foo", PropertyType.ofComplex(prop("a", PropertyType.Primitive.STR)));
    var res = left.merge(PropertyType.Primitive.STR);
    assertThat(res.type())
        .isEqualTo(
            union(PropertyType.ofComplex(prop("a", PropertyType.Primitive.STR)), PropertyType.STR));
  }

  // -----------------------------------------------------------------------------------------------
  // Complex + Complex

  @Test
  @DisplayName("{ a: str } + { a: str } -> { a: str }  [identical]")
  void identicalComplexes() {
    var left = prop("foo", complex(prop("a", PropertyType.Primitive.STR)));
    var right = complex(prop("a", PropertyType.Primitive.STR));
    var res = left.merge(right);
    assertThat(res.type()).isEqualTo(complex(prop("a", PropertyType.Primitive.STR)));
  }

  @Test
  @DisplayName("{ a: str } + { a: num } -> { a: str | num }")
  void foo6_sameKeyDifferentType() {
    var left = prop("foo", complex(prop("a", PropertyType.Primitive.STR)));
    var right = complex(prop("a", PropertyType.Primitive.NUM));
    var res = left.merge(right);
    assertThat(res.type()).isEqualTo(
        complex(prop("a", union(PropertyType.STR, PropertyType.NUM))));
  }

  @Test
  @DisplayName("{ a: str } + { b: num } -> { a: str } | { b: num }  [disjoint keys]")
  void foo7_disjointKeys() {
    var left = prop("foo", complex(prop("a", PropertyType.Primitive.STR)));
    var right = complex(prop("b", PropertyType.Primitive.NUM));
    var res = left.merge(right);
    assertThat(res.type()).isEqualTo(union(
        complex(prop("a", PropertyType.Primitive.STR)),
        complex(prop("b", PropertyType.Primitive.NUM))));
  }

  @Test
  @DisplayName("{ a: str, b: num } + { b: num } -> { a: str | null, b: num }  [right is subset]")
  void foo8_rightIsSubset() {
    var left = prop("foo",
        complex(prop("a", PropertyType.Primitive.STR), prop("b", PropertyType.Primitive.NUM)));
    var right = complex(prop("b", PropertyType.Primitive.NUM));
    var res = left.merge(right);
    assertThat(res.type()).isEqualTo(complex(
        prop("a", union(PropertyType.Primitive.STR, PropertyType.NULL)),
        prop("b", PropertyType.Primitive.NUM)));
  }

  @Test
  @DisplayName("{ b: num } + { a: str, b: num } -> { a: str | null, b: num }  [left is subset]")
  void leftIsSubset() {
    var left = prop("foo", complex(prop("b", PropertyType.Primitive.NUM)));
    var right = complex(
        prop("a", PropertyType.Primitive.STR),
        prop("b", PropertyType.Primitive.NUM));
    var res = left.merge(right);
    assertThat(res.type()).isEqualTo(complex(
        prop("a", union(PropertyType.Primitive.STR, PropertyType.NULL)),
        prop("b", PropertyType.Primitive.NUM)));
  }

  @Test
  @DisplayName(
      "{ a: str, b: num } + { b: str, c: null } -> { a: str | null, b: num | str, c: null }  [partial overlap]")
  void partialOverlapKeys() {
    var left = prop("foo",
        complex(prop("a", PropertyType.Primitive.STR), prop("b", PropertyType.Primitive.NUM)));
    var right =
        complex(prop("b", PropertyType.Primitive.STR), prop("c", PropertyType.Primitive.NULL));
    var res = left.merge(right);
    assertThat(res.type()).isEqualTo(complex(
        prop("a", union(PropertyType.Primitive.STR, PropertyType.NULL)),
        prop("b", union(PropertyType.NUM, PropertyType.STR)),
        prop("c", PropertyType.Primitive.NULL)));
  }

  @Test
  @DisplayName("{ a: { b: str } } + { a: { b: num } } -> { a: { b: str | num } }  [nested complex]")
  void nestedComplexMerge() {
    var left = prop("foo",
        complex(prop("a", complex(prop("b", PropertyType.Primitive.STR)))));
    var right = complex(prop("a", complex(prop("b", PropertyType.Primitive.NUM))));
    var res = left.merge(right);
    assertThat(res.type()).isEqualTo(
        complex(prop("a", complex(prop("b", union(PropertyType.STR, PropertyType.NUM))))));
  }

  @Test
  @DisplayName(
      "{ a: { b: str } } + { a: num } -> { a: { b: str } | num }  [nested complex vs primitive]")
  void nestedComplexVsPrimitive() {
    var innerComplex = complex(prop("b", PropertyType.Primitive.STR));
    var left = prop("foo", complex(prop("a", innerComplex)));
    var right = complex(prop("a", PropertyType.Primitive.NUM));
    var res = left.merge(right);
    assertThat(res.type()).isEqualTo(
        complex(prop("a", union(complex(prop("b", PropertyType.Primitive.STR)), PropertyType.NUM))));
  }

  // -----------------------------------------------------------------------------------------------
  // Union * Complex

  @Test
  @DisplayName("(str | num) + { a: str } -> str | num | { a: str }  [complex is new to union]")
  void unionGrowsWithNewComplex() {
    var a = prop("foo", union(PropertyType.STR, PropertyType.NUM));
    var rhs = complex(prop("a", PropertyType.Primitive.STR));
    var res = a.merge(rhs);
    assertThat(res.type())
        .isEqualTo(union(PropertyType.STR, PropertyType.NUM, rhs));
  }

  @Test
  @DisplayName(
      "(str | { a: str }) + { a: num } -> str | { a: str | num }  [complex in union gets merged]")
  void complexMemberInUnionGetsDeepMerge() {
    var innerComplex = complex(prop("a", PropertyType.Primitive.STR));
    var a = prop("foo", union(PropertyType.STR, innerComplex));
    var rhs = complex(prop("a", PropertyType.Primitive.NUM));
    var res = a.merge(rhs);
    assertThat(res.type()).isEqualTo(union(
        PropertyType.STR,
        complex(prop("a", union(PropertyType.STR, PropertyType.NUM)))));
  }

  @Test
  @DisplayName(
      "(str | { a: str }) + { b: num } -> str | { a: str } | { b: num }  [complex member is disjoint]")
  void complexMemberAndRhsAreDisjoint() {
    var innerComplex = complex(prop("a", PropertyType.Primitive.STR));
    var a = prop("foo", union(PropertyType.STR, innerComplex));
    var rhs = complex(prop("b", PropertyType.Primitive.NUM));
    var res = a.merge(rhs);
    assertThat(res.type()).isEqualTo(union(PropertyType.STR, innerComplex, rhs));
  }
}

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

import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PropertyTest {

  @Test
  @DisplayName("str + str -> str")
  void foo() {
    final var a = new Property("foo", PropertyType.Primitive.STR);
    final var b = PropertyType.Primitive.STR;

    final var res = a.merge(b);
    assertThat(res.type())
        .describedAs(() -> "str + str -> str")
        .isEqualTo(PropertyType.Primitive.STR);
  }

  @Test
  @DisplayName("str + null -> str?")
  void foo2() {
    final var a = new Property("foo", PropertyType.Primitive.STR);
    final var b = PropertyType.Primitive.NULL;

    final var res = a.merge(b);
    assertThat(res.type())
        .describedAs(() -> "str + null -> str | null")
        .isEqualTo(new PropertyType.Union(
            List.of(PropertyType.STR, PropertyType.NULL),
            PropertyType.Arity.ONE));
  }

  @Test
  @DisplayName("str + [str] -> str | [str]")
  void foo3() {
    final var a = new Property("foo", PropertyType.Primitive.STR);
    final var b = PropertyType.Primitive.STR_ARRAY;

    final var res = a.merge(b);
    assertThat(res.type())
        .describedAs(() -> "str + [str] -> str | [str]")
        .isEqualTo(new PropertyType.Union(
            List.of(PropertyType.STR, PropertyType.STR_ARRAY),
            PropertyType.Arity.ONE));
  }

  @Test
  @DisplayName("( str | [str] ) + [str] -> str | [str]")
  void foo4() {
    final var a = new Property("foo", new PropertyType.Union(
        List.of(PropertyType.Primitive.STR, PropertyType.Primitive.STR_ARRAY),
        PropertyType.Arity.ONE));
    final var b = PropertyType.Primitive.STR_ARRAY;

    final var res = a.merge(b);
    assertThat(res.type())
        .describedAs(() -> "( str | [str] ) + [str] -> str | [str]")
        .isEqualTo(new PropertyType.Union(
            List.of(PropertyType.STR, PropertyType.STR_ARRAY),
            PropertyType.Arity.ONE));
  }


}

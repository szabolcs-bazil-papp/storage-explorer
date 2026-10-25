/*
 * Copyright (C) 2026 Szabolcs Bazil Papp
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
import com.aestallon.storageexplorer.core.model.type.dto.PropertyTypeDto;

class PropertyTypeRecordTest {

  private static Property prop(final String key, final PropertyType type) {
    return new Property(key, type);
  }

  private static PropertyType.Record recordOf(final PropertyType valueType) {
    return new PropertyType.Record(valueType, PropertyType.Arity.ONE);
  }

  @Test
  @DisplayName("Complex with uniform property value types satisfies the matching Record")
  void uniformComplexSatisfiesRecord() {
    final var complex = PropertyType.ofComplex(
        prop("a", PropertyType.STR),
        prop("b", PropertyType.STR),
        prop("c", PropertyType.STR));

    assertThat(complex.satisfies(recordOf(PropertyType.STR))).isTrue();
  }

  @Test
  @DisplayName("Complex with a mismatching property value type does not satisfy the Record")
  void mismatchingComplexDoesNotSatisfyRecord() {
    final var complex = PropertyType.ofComplex(
        prop("a", PropertyType.STR),
        prop("b", PropertyType.NUM));

    assertThat(complex.satisfies(recordOf(PropertyType.STR))).isFalse();
  }

  @Test
  @DisplayName("Keys are irrelevant to the Record match - only the uniform value type matters")
  void keysDoNotMatterForRecordMatch() {
    final var complex = PropertyType.ofComplex(
        prop("whatever", PropertyType.NUM),
        prop("anything", PropertyType.NUM));

    assertThat(complex.satisfies(recordOf(PropertyType.NUM))).isTrue();
  }

  @Test
  @DisplayName("Empty Complex satisfies any Record vacuously")
  void emptyComplexSatisfiesAnyRecord() {
    final PropertyType.Complex empty = new PropertyType.Complex(List.of(), PropertyType.Arity.ONE);
    assertThat(empty.satisfies(recordOf(PropertyType.STR))).isTrue();
    assertThat(empty.satisfies(recordOf(PropertyType.NUM))).isTrue();
  }

  @Test
  @DisplayName("A Union of uniform Complexes satisfies a matching Record (via generic Union.satisfies)")
  void unionOfUniformComplexesSatisfiesRecord() {
    final var complexA = PropertyType.ofComplex(prop("x", PropertyType.STR));
    final var complexB = PropertyType.ofComplex(prop("y", PropertyType.STR), prop("z", PropertyType.STR));
    final var union = PropertyType.union(complexA, complexB);

    assertThat(union.satisfies(recordOf(PropertyType.STR))).isTrue();
  }

  @Test
  @DisplayName("A Union is rejected if any member Complex does not match the Record's valueType")
  void unionRejectedIfAnyMemberMismatches() {
    final var complexA = PropertyType.ofComplex(prop("x", PropertyType.STR));
    final var complexB = PropertyType.ofComplex(prop("y", PropertyType.NUM));
    final var union = PropertyType.union(complexA, complexB);

    assertThat(union.satisfies(recordOf(PropertyType.STR))).isFalse();
  }

  @Test
  @DisplayName("Record satisfies Unknown")
  void recordSatisfiesUnknown() {
    assertThat(recordOf(PropertyType.STR).satisfies(PropertyType.UNKNOWN)).isTrue();
  }

  @Test
  @DisplayName("Record satisfies a Record with a matching valueType, not a mismatching one")
  void recordSatisfiesRecord() {
    assertThat(recordOf(PropertyType.STR).satisfies(recordOf(PropertyType.STR))).isTrue();
    assertThat(recordOf(PropertyType.STR).satisfies(recordOf(PropertyType.NUM))).isFalse();
  }

  @Test
  @DisplayName("Primitives and Refs never satisfy a Record")
  void primitivesAndRefsNeverSatisfyRecord() {
    assertThat(PropertyType.STR.satisfies(recordOf(PropertyType.STR))).isFalse();
    assertThat(new PropertyType.Ref("Foo", PropertyType.Arity.ONE).satisfies(recordOf(PropertyType.STR)))
        .isFalse();
  }

  @Test
  @DisplayName("EntityType.associations recurses into a Record's valueType")
  void entityAssociationsRecurseIntoRecordValueType() {
    final var entity = new EntityType("Host", List.of(
        new Property("refs", recordOf(new PropertyType.Ref("Target", PropertyType.Arity.ONE)))));

    final var associations = entity.associations();
    assertThat(associations)
        .extracting(Association::to)
        .containsExactly("Target");
  }

  @Test
  @DisplayName("PropertyTypeDto round-trips a Record")
  void propertyTypeDtoRoundTripsRecord() {
    final var record = recordOf(new PropertyType.Ref("Target", PropertyType.Arity.ONE));
    final var dto = PropertyTypeDto.of(record);
    final var roundTripped = dto.toDomainObject();
    assertThat(roundTripped).isEqualTo(record);
  }

}

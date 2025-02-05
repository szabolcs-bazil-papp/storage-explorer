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

package com.aestallon.storageexplorer.arcscript.internal.query;

import org.junit.jupiter.api.Test;
import com.aestallon.storageexplorer.core.service.StorageInstanceExaminer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class AssertionOperationTest {

  // -----------------------------------------------------------------------------------------------
  // STRING OPERATIONS
  // -----------------------------------------------------------------------------------------------

  // ~~EQUALITY~~ ----------------------------------------------------------------------------------

  @Test
  void stringEqualityChecksOutWhenStringsAreEqual() {
    // given
    final var assertion = new Assertion();
    assertion.str("name").is("Foo");

    // when
    final var discovery = new StorageInstanceExaminer.StringFound("Foo", null, null);

    //then
    assertThat(assertion).matches(it -> it.check(discovery), "String found: \"Foo\"");
  }

  @Test
  void stringEqualityDoesNotCheckOutWhenStringsAreNotEqual() {
    // given
    final var assertion = new Assertion();
    assertion.str("name").is("Foo");

    // when
    final var discovery = new StorageInstanceExaminer.StringFound("Baz", null, null);

    // then
    assertThat(assertion).matches(it -> !it.check(discovery), discovery.toString());
  }

  @Test
  void stringEqualityChecksOutForNullValue_whenDiscoveryFindsNull() {
    // given
    final var assertion = new Assertion();
    assertion.str("name").is(null);

    // when
    final var discovery = new StorageInstanceExaminer.NoValue();

    // then
    assertThat(assertion).matches(it -> it.check(discovery), discovery.toString());
  }

  @Test
  void stringEqualityDoesNotCheckOutForNullValue_whenDiscoveryFindsNonNullValue() {
    // given
    final var assertion = new Assertion();
    assertion.str("name").is(null);

    // when
    final var discovery = new StorageInstanceExaminer.StringFound("Foo", null, null);

    // then
    assertThat(assertion).matches(it -> !it.check(discovery), discovery.toString());
  }

  @Test
  void stringEqualityDoesNotCheckOutForNullValue_whenDiscoveryCannotReachTargetProperty() {
    // given
    final var assertion = new Assertion();
    assertion.str("name").is(null);

    // when
    final var discovery = new StorageInstanceExaminer.NotFound(null);

    // then
    assertThat(assertion).matches(it -> !it.check(discovery), discovery.toString());
  }

  @Test
  void stringEqualityDoesNotCheckOut_whenDiscoveredTypeIsDifferent() {
    // given
    final var assertion = new Assertion();
    assertion.str("age").is("9");

    // when
    final var discovery = new StorageInstanceExaminer.NumberFound(9, null, null);

    // then
    assertThat(assertion).matches(it -> !it.check(discovery), discovery.toString());
  }
  
  @Test
  void stringInMatches_IfValueIsFound() {
    // given
    final var assertion = new Assertion();
    assertion.str("name").in("Foo", "Bar", "Baz");

    // when
    final var discovery = new StorageInstanceExaminer.StringFound("Bar", null, null);

    // then
    assertThat(assertion).matches(it -> it.check(discovery), discovery.toString());
  }

  @Test
  void stringInMatches_IfLookingForNull_andDiscoveryFindsNull() {
    // given
    final var assertion = new Assertion();
    assertion.str("name").in("Foo", "Bar", null);

    // when
    final var discovery = new StorageInstanceExaminer.NoValue();

    // then
    assertThat(assertion).matches(it -> it.check(discovery), discovery.toString());
  }

  // ~~CONTAINS~~ ----------------------------------------------------------------------------------

  @Test
  void stringContainsChecksOut_whenDiscoveredStringIsEqual() {
    // given
    final var assertion = new Assertion();
    assertion.str("name").contains("John");

    // when
    final var discovery = new StorageInstanceExaminer.StringFound("John", null, null);

    // then
    assertThat(assertion).matches(it -> it.check(discovery), discovery.toString());
  }

  @Test
  void stringContainsChecksOut_whenDiscoveredStringContainsQueryString() {
    // given
    final var assertion = new Assertion();
    assertion.str("name").contains("John");

    // when
    final var discovery = new StorageInstanceExaminer.StringFound("Johnny Silverhand", null, null);

    // then
    assertThat(assertion).matches(it -> it.check(discovery), discovery.toString());
  }

  @Test
  void stringContainsFails_whenDiscoveredStringDoesNotContainQueryString() {
    // given
    final var assertion = new Assertion();
    assertion.str("name").contains("John");

    // when
    final var discovery = new StorageInstanceExaminer.StringFound("Silverhand", null, null);

    // then
    assertThat(assertion).matches(it -> !it.check(discovery), discovery.toString());
  }

  @Test
  void stringContainsDoesNotAcceptNullValues() {
    // given
    final var assertion = new Assertion();
    final var op = assertion.str("name");
    // when
    assertThatCode(() -> op.contains(null))
        // then
        .as("Invoking str contains with null value")
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Cannot call str contains with null value");
  }

  // ~~STARTS_WITH~~ -------------------------------------------------------------------------------

  @Test
  void stringStartsWithChecksOut_whenDiscoveredStringIsEqual() {
    // given
    final var assertion = new Assertion();
    assertion.str("name").starts_with("John");

    // when
    final var discovery = new StorageInstanceExaminer.StringFound("John", null, null);

    // then
    assertThat(assertion).matches(it -> it.check(discovery), discovery.toString());
  }

  @Test
  void stringStartsWithChecksOut_whenDiscoveredStringStartsWithQueryString() {
    // given
    final var assertion = new Assertion();
    assertion.str("name").starts_with("John");

    // when
    final var discovery = new StorageInstanceExaminer.StringFound("Johnny Silverhand", null, null);

    // then
    assertThat(assertion).matches(it -> it.check(discovery), discovery.toString());
  }

  @Test
  void stringStartsWithFails_whenDiscoveredStringDoesNotStartWithQueryString() {
    // given
    final var assertion = new Assertion();
    assertion.str("name").starts_with("John");

    // when
    final var discovery = new StorageInstanceExaminer.StringFound("BJohn", null, null);

    // then
    assertThat(assertion).matches(it -> !it.check(discovery), discovery.toString());
  }

  @Test
  void stringStartsWithDoesNotAcceptNullValues() {
    // given
    final var assertion = new Assertion();
    final var op = assertion.str("name");
    // when
    assertThatCode(() -> op.starts_with(null))
        // then
        .as("Invoking str starts_with with null value")
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Cannot call str starts_with with null value");
  }

  // ~~ENDS_WITH~~ ---------------------------------------------------------------------------------

  @Test
  void stringEndsWithChecksOut_whenDiscoveredStringIsEqual() {
    // given
    final var assertion = new Assertion();
    assertion.str("name").ends_with("John");

    // when
    final var discovery = new StorageInstanceExaminer.StringFound("John", null, null);

    // then
    assertThat(assertion).matches(it -> it.check(discovery), discovery.toString());
  }

  @Test
  void stringEndsWithChecksOut_whenDiscoveredStringEndsWithQueryString() {
    // given
    final var assertion = new Assertion();
    assertion.str("name").ends_with("John");

    // when
    final var discovery = new StorageInstanceExaminer.StringFound("Not John", null, null);

    // then
    assertThat(assertion).matches(it -> it.check(discovery), discovery.toString());
  }

  @Test
  void stringEndsWithFails_whenDiscoveredStringDoesNotEndWithQueryString() {
    // given
    final var assertion = new Assertion();
    assertion.str("name").ends_with("John");

    // when
    final var discovery = new StorageInstanceExaminer.StringFound("Silverhand", null, null);

    // then
    assertThat(assertion).matches(it -> !it.check(discovery), discovery.toString());
  }

  @Test
  void stringEndsWithDoesNotAcceptNullValues() {
    // given
    final var assertion = new Assertion();
    final var op = assertion.str("name");
    // when
    assertThatCode(() -> op.ends_with(null))
        // then
        .as("Invoking str ends_with with null value")
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Cannot call str ends_with with null value");
  }

  // -----------------------------------------------------------------------------------------------
  // NUMERIC OPERATIONS
  // -----------------------------------------------------------------------------------------------

  // ~~EQUALITY~~ ----------------------------------------------------------------------------------

  @Test
  void numEqualityChecksOutWhenNumbersAreEqual() {
    // given
    final var assertion = new Assertion();
    assertion.num("age").is(69);

    // when
    final var discovery = new StorageInstanceExaminer.NumberFound(69, null, null);

    //then
    assertThat(assertion).matches(it -> it.check(discovery), "Number found: 69");
  }

  @Test
  void numEqualityDoesNotCheckOutWhenNumbersAreNotEqual() {
    // given
    final var assertion = new Assertion();
    assertion.num("age").is(69);

    // when
    final var discovery = new StorageInstanceExaminer.NumberFound(70, null, null);

    // then
    assertThat(assertion).matches(it -> !it.check(discovery), discovery.toString());
  }

  @Test
  void numEqualityChecksOutForNullValue_whenDiscoveryFindsNull() {
    // given
    final var assertion = new Assertion();
    assertion.num("age").is(null);

    // when
    final var discovery = new StorageInstanceExaminer.NoValue();

    // then
    assertThat(assertion).matches(it -> it.check(discovery), discovery.toString());
  }

  @Test
  void numEqualityDoesNotCheckOutForNullValue_whenDiscoveryFindsNonNullValue() {
    // given
    final var assertion = new Assertion();
    assertion.num("age").is(null);

    // when
    final var discovery = new StorageInstanceExaminer.StringFound("Foo", null, null);

    // then
    assertThat(assertion).matches(it -> !it.check(discovery), discovery.toString());
  }

  @Test
  void numEqualityDoesNotCheckOutForNullValue_whenDiscoveryCannotReachTargetProperty() {
    // given
    final var assertion = new Assertion();
    assertion.num("age").is(null);

    // when
    final var discovery = new StorageInstanceExaminer.NotFound(null);

    // then
    assertThat(assertion).matches(it -> !it.check(discovery), discovery.toString());
  }

  @Test
  void numEqualityDoesNotCheckOut_whenDiscoveredTypeIsDifferent() {
    // given
    final var assertion = new Assertion();
    assertion.num("age").is(69);

    // when
    final var discovery = new StorageInstanceExaminer.StringFound("69", null, null);

    // then
    assertThat(assertion).matches(it -> !it.check(discovery), discovery.toString());
  }
  
  @Test
  void numEqualityChecksOut_whenLookingForLongValue_andDiscoveringEquivalentDouble() {
    // given
    final var assertion = new Assertion();
    assertion.num("age").is(69L);

    // when
    final var discovery = new StorageInstanceExaminer.NumberFound(69.0d, null, null);

    // then
    assertThat(assertion).matches(it -> it.check(discovery), discovery.toString());
  }

  @Test
  void numEqualityChecksOut_whenLookingForDoubleValue_andDiscoveringEquivalentInteger() {
    // given
    final var assertion = new Assertion();
    assertion.num("age").is(69.0d);

    // when
    final var discovery = new StorageInstanceExaminer.NumberFound(69, null, null);

    // then
    assertThat(assertion).matches(it -> it.check(discovery), discovery.toString());
  }

  @Test
  void numEqualityChecksOut_whenLookingForFloatValue_andDiscoveringEquivalentDouble() {
    // given
    final var assertion = new Assertion();
    assertion.num("age").is(69.0f);

    // when
    final var discovery = new StorageInstanceExaminer.NumberFound(69.0d, null, null);

    // then
    assertThat(assertion).matches(it -> it.check(discovery), discovery.toString());
  }
}

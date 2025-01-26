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


  @Test
  void stringEqualityChecksOutWhenStringsAreEqual() {
    // given
    final var assertion = new Assertion();
    assertion.str("name").is("Foo");

    // when
    final var discovery = new StorageInstanceExaminer.StringFound("Foo", null);

    //then
    assertThat(assertion).matches(it -> it.check(discovery), "String found: \"Foo\"");
  }

  @Test
  void stringEqualityDoesNotCheckOutWhenStringsAreNotEqual() {
    // given
    final var assertion = new Assertion();
    assertion.str("name").is("Foo");

    // when
    final var discovery = new StorageInstanceExaminer.StringFound("Baz", null);

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
    final var discovery = new StorageInstanceExaminer.StringFound("Foo", null);

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
    final var discovery = new StorageInstanceExaminer.NumberFound(9, null);

    // then
    assertThat(assertion).matches(it -> !it.check(discovery), discovery.toString());
  }

  @Test
  void stringContainsChecksOut_whenDiscoveredStringIsEqual() {
    // given
    final var assertion = new Assertion();
    assertion.str("name").contains("John");

    // when
    final var discovery = new StorageInstanceExaminer.StringFound("John", null);

    // then
    assertThat(assertion).matches(it -> it.check(discovery), discovery.toString());
  }

  @Test
  void stringContainsChecksOut_whenDiscoveredStringContainsQueryString() {
    // given
    final var assertion = new Assertion();
    assertion.str("name").contains("John");

    // when
    final var discovery = new StorageInstanceExaminer.StringFound("Johnny Silverhand", null);

    // then
    assertThat(assertion).matches(it -> it.check(discovery), discovery.toString());
  }

  @Test
  void stringContainsFails_whenDiscoveredStringDoesNotContainQueryString() {
    // given
    final var assertion = new Assertion();
    assertion.str("name").contains("John");

    // when
    final var discovery = new StorageInstanceExaminer.StringFound("Silverhand", null);

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

}

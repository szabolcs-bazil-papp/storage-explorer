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

  // -----------------------------------------------------------------------------------------------
  // TIME OPERATIONS
  // -----------------------------------------------------------------------------------------------

  // ~~LITERAL VALIDATION~~ -------------------------------------------------------------------------

  @Test
  void timeLiteralIsAcceptedWhenItIsAnIsoLocalDate() {
    // given
    final var assertion = new Assertion();

    // when & then
    assertThatCode(() -> assertion.time("createdAt").is("2024-01-01"))
        .doesNotThrowAnyException();
  }

  @Test
  void timeLiteralIsAcceptedWhenItIsAnIsoLocalDateTime() {
    // given
    final var assertion = new Assertion();

    // when & then
    assertThatCode(() -> assertion.time("createdAt").is("2024-01-01T10:15:30"))
        .doesNotThrowAnyException();
  }

  @Test
  void timeLiteralIsAcceptedWhenItIsAnIsoOffsetDateTime() {
    // given
    final var assertion = new Assertion();

    // when & then
    assertThatCode(() -> assertion.time("createdAt").is("2024-01-01T10:15:30+02:00"))
        .doesNotThrowAnyException();
  }

  @Test
  void timeLiteralIsAcceptedWhenItIsAnIsoZonedDateTime() {
    // given
    final var assertion = new Assertion();

    // when & then
    assertThatCode(() -> assertion.time("createdAt").is("2024-01-01T10:15:30+02:00[Europe/Budapest]"))
        .doesNotThrowAnyException();
  }

  @Test
  void timeLiteralIsRejectedImmediately_whenItIsNotIsoCompliant() {
    // given
    final var assertion = new Assertion();
    final var op = assertion.time("createdAt");

    // when
    assertThatCode(() -> op.is("2024/01/01"))
        // then
        .as("Invoking time is with a non-ISO literal")
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("2024/01/01");
  }

  @Test
  void timeLiteralIsRejectedImmediately_forLesserThan() {
    // given
    final var assertion = new Assertion();
    final var op = assertion.time("createdAt");

    // when
    assertThatCode(() -> op.is_lesser_than("not-a-date"))
        // then
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void timeLiteralIsRejectedImmediately_forBetweenStart() {
    // given
    final var assertion = new Assertion();
    final var op = assertion.time("createdAt");

    // when
    assertThatCode(() -> op.between("not-a-date"))
        // then
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void timeLiteralIsRejectedImmediately_forBetweenEnd() {
    // given
    final var assertion = new Assertion();
    final var op = assertion.time("createdAt");
    final var start = op.between("2024-01-01");

    // when
    assertThatCode(() -> start.and("not-a-date"))
        // then
        .isInstanceOf(IllegalArgumentException.class);
  }

  // ~~EQUALITY~~ ------------------------------------------------------------------------------------

  @Test
  void timeEqualityChecksOut_whenDiscoveredLocalDateMatchesLiteral() {
    // given
    final var assertion = new Assertion();
    assertion.time("createdAt").is("2024-01-01");

    // when
    final var discovery = new StorageInstanceExaminer.StringFound("2024-01-01", null, null);

    // then
    assertThat(assertion).matches(it -> it.check(discovery), discovery.toString());
  }

  @Test
  void timeEqualityDoesNotCheckOut_whenDiscoveredLocalDateDiffersFromLiteral() {
    // given
    final var assertion = new Assertion();
    assertion.time("createdAt").is("2024-01-01");

    // when
    final var discovery = new StorageInstanceExaminer.StringFound("2024-01-02", null, null);

    // then
    assertThat(assertion).matches(it -> !it.check(discovery), discovery.toString());
  }

  @Test
  void timeEqualityChecksOut_whenDiscoveredOffsetDateTimeRepresentsSameInstantAsLiteral() {
    // given
    final var assertion = new Assertion();
    assertion.time("createdAt").is("2024-01-01T12:00:00+02:00");

    // when
    final var discovery =
        new StorageInstanceExaminer.StringFound("2024-01-01T10:00:00+00:00", null, null);

    // then
    assertThat(assertion).matches(it -> it.check(discovery), discovery.toString());
  }

  @Test
  void timeEqualityIsLenientAboutSpaceSeparatedDateTime() {
    // given
    final var assertion = new Assertion();
    assertion.time("createdAt").is("2024-01-01T10:15:30");

    // when
    final var discovery =
        new StorageInstanceExaminer.StringFound("2024-01-01 10:15:30", null, null);

    // then
    assertThat(assertion).matches(it -> it.check(discovery), discovery.toString());
  }

  @Test
  void timeEqualityDoesNotCheckOut_whenDiscoveryIsNotStringFound() {
    // given
    final var assertion = new Assertion();
    assertion.time("createdAt").is("2024-01-01");

    // when
    final var discovery = new StorageInstanceExaminer.NumberFound(1, null, null);

    // then
    assertThat(assertion).matches(it -> !it.check(discovery), discovery.toString());
  }

  @Test
  void timeEqualityDoesNotCheckOut_whenDiscoveredStringIsUnparseable() {
    // given
    final var assertion = new Assertion();
    assertion.time("createdAt").is("2024-01-01");

    // when
    final var discovery = new StorageInstanceExaminer.StringFound("not a date", null, null);

    // then
    assertThat(assertion).matches(it -> !it.check(discovery), discovery.toString());
  }

  @Test
  void timeEqualityDoesNotCheckOut_whenDiscoveryIsNotFound() {
    // given
    final var assertion = new Assertion();
    assertion.time("createdAt").is("2024-01-01");

    // when
    final var discovery = new StorageInstanceExaminer.NotFound(null);

    // then
    assertThat(assertion).matches(it -> !it.check(discovery), discovery.toString());
  }

  @Test
  void timeInMatches_whenAnyLiteralMatchesTheDiscoveredValue() {
    // given
    final var assertion = new Assertion();
    assertion.time("createdAt").in("2024-01-01", "2024-02-01", "2024-03-01");

    // when
    final var discovery = new StorageInstanceExaminer.StringFound("2024-02-01", null, null);

    // then
    assertThat(assertion).matches(it -> it.check(discovery), discovery.toString());
  }

  @Test
  void timeNotNegatesEquality() {
    // given
    final var assertion = new Assertion();
    assertion.time("createdAt").not("2024-01-01");

    // when
    final var discovery = new StorageInstanceExaminer.StringFound("2024-01-02", null, null);

    // then
    assertThat(assertion).matches(it -> it.check(discovery), discovery.toString());
  }

  // ~~COMPARISON~~ -----------------------------------------------------------------------------------

  @Test
  void timeIsGreaterThanChecksOut_whenDiscoveredDateIsAfterLiteral() {
    // given
    final var assertion = new Assertion();
    assertion.time("createdAt").is_greater_than("2024-01-01");

    // when
    final var discovery = new StorageInstanceExaminer.StringFound("2024-06-15", null, null);

    // then
    assertThat(assertion).matches(it -> it.check(discovery), discovery.toString());
  }

  @Test
  void timeIsGreaterThanFails_whenDiscoveredDateIsBeforeLiteral() {
    // given
    final var assertion = new Assertion();
    assertion.time("createdAt").is_greater_than("2024-06-15");

    // when
    final var discovery = new StorageInstanceExaminer.StringFound("2024-01-01", null, null);

    // then
    assertThat(assertion).matches(it -> !it.check(discovery), discovery.toString());
  }

  @Test
  void timeIsLesserThanChecksOut_whenDiscoveredDateIsBeforeLiteral() {
    // given
    final var assertion = new Assertion();
    assertion.time("createdAt").is_lesser_than("2024-06-15");

    // when
    final var discovery = new StorageInstanceExaminer.StringFound("2024-01-01", null, null);

    // then
    assertThat(assertion).matches(it -> it.check(discovery), discovery.toString());
  }

  // ~~BETWEEN ... AND~~ -------------------------------------------------------------------------------

  @Test
  void timeBetweenChecksOut_whenDiscoveredDateFallsWithinBounds_inclusive() {
    // given
    final var assertion = new Assertion();
    assertion.time("createdAt").between("2024-01-01").and("2024-12-31");

    // when
    final var lowerBound = new StorageInstanceExaminer.StringFound("2024-01-01", null, null);
    final var upperBound = new StorageInstanceExaminer.StringFound("2024-12-31", null, null);
    final var middle = new StorageInstanceExaminer.StringFound("2024-06-15", null, null);

    // then
    assertThat(assertion).matches(it -> it.check(lowerBound), lowerBound.toString());
    assertThat(assertion).matches(it -> it.check(upperBound), upperBound.toString());
    assertThat(assertion).matches(it -> it.check(middle), middle.toString());
  }

  @Test
  void timeBetweenFails_whenDiscoveredDateFallsOutsideBounds() {
    // given
    final var assertion = new Assertion();
    assertion.time("createdAt").between("2024-01-01").and("2024-12-31");

    // when
    final var discovery = new StorageInstanceExaminer.StringFound("2025-01-01", null, null);

    // then
    assertThat(assertion).matches(it -> !it.check(discovery), discovery.toString());
  }

  // ~~IN_THE_DAY_OF~~ ---------------------------------------------------------------------------------

  @Test
  void timeInTheDayOfChecksOut_whenDiscoveredDateTimeFallsOnTheSameCalendarDay() {
    // given
    final var assertion = new Assertion();
    assertion.time("createdAt").in_the_day_of("2024-01-01");

    // when
    final var discovery =
        new StorageInstanceExaminer.StringFound("2024-01-01T23:59:59", null, null);

    // then
    assertThat(assertion).matches(it -> it.check(discovery), discovery.toString());
  }

  @Test
  void timeInTheDayOfFails_whenDiscoveredDateTimeFallsOnADifferentCalendarDay() {
    // given
    final var assertion = new Assertion();
    assertion.time("createdAt").in_the_day_of("2024-01-01");

    // when
    final var discovery =
        new StorageInstanceExaminer.StringFound("2024-01-02T00:00:00", null, null);

    // then
    assertThat(assertion).matches(it -> !it.check(discovery), discovery.toString());
  }

  // ~~IN_THE_HOUR_OF~~ --------------------------------------------------------------------------------

  @Test
  void timeInTheHourOfChecksOut_whenDiscoveredDateTimeFallsWithinTheSameHour() {
    // given
    final var assertion = new Assertion();
    assertion.time("createdAt").in_the_hour_of("2024-01-01T10:00:00");

    // when
    final var discovery =
        new StorageInstanceExaminer.StringFound("2024-01-01T10:59:59", null, null);

    // then
    assertThat(assertion).matches(it -> it.check(discovery), discovery.toString());
  }

  @Test
  void timeInTheHourOfFails_whenDiscoveredDateTimeFallsInADifferentHour() {
    // given
    final var assertion = new Assertion();
    assertion.time("createdAt").in_the_hour_of("2024-01-01T10:00:00");

    // when
    final var discovery =
        new StorageInstanceExaminer.StringFound("2024-01-01T11:00:00", null, null);

    // then
    assertThat(assertion).matches(it -> !it.check(discovery), discovery.toString());
  }

  // ~~IN_THE_MONTH_OF / IN_THE_YEAR_OF~~ ---------------------------------------------------------------

  @Test
  void timeInTheMonthOfChecksOut_whenDiscoveredDateFallsInTheSameMonthAndYear() {
    // given
    final var assertion = new Assertion();
    assertion.time("createdAt").in_the_month_of("2024-03-01");

    // when
    final var discovery = new StorageInstanceExaminer.StringFound("2024-03-27", null, null);

    // then
    assertThat(assertion).matches(it -> it.check(discovery), discovery.toString());
  }

  @Test
  void timeInTheMonthOfFails_whenDiscoveredDateFallsInADifferentYear_sameMonth() {
    // given
    final var assertion = new Assertion();
    assertion.time("createdAt").in_the_month_of("2024-03-01");

    // when
    final var discovery = new StorageInstanceExaminer.StringFound("2025-03-27", null, null);

    // then
    assertThat(assertion).matches(it -> !it.check(discovery), discovery.toString());
  }

  @Test
  void timeInTheYearOfChecksOut_whenDiscoveredDateFallsInTheSameYear() {
    // given
    final var assertion = new Assertion();
    assertion.time("createdAt").in_the_year_of("2024-01-01");

    // when
    final var discovery = new StorageInstanceExaminer.StringFound("2024-11-05", null, null);

    // then
    assertThat(assertion).matches(it -> it.check(discovery), discovery.toString());
  }

  @Test
  void timeInTheYearOfFails_whenDiscoveredDateFallsInADifferentYear() {
    // given
    final var assertion = new Assertion();
    assertion.time("createdAt").in_the_year_of("2024-01-01");

    // when
    final var discovery = new StorageInstanceExaminer.StringFound("2025-11-05", null, null);

    // then
    assertThat(assertion).matches(it -> !it.check(discovery), discovery.toString());
  }
}

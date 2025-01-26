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

import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import com.aestallon.storageexplorer.core.service.StorageInstanceExaminer;
import groovy.json.JsonBuilder;
import groovy.lang.Closure;

public abstract sealed class AssertionOperation<T> permits
    AssertionOperation.AssertionOperationStr,
    AssertionOperation.AssertionOperationBool,
    AssertionOperation.AssertionOperationJson,
    AssertionOperation.AssertionOperationNum/*,
    AssertionOperation.AssertionOperationTime*/ {

  protected final Assertion assertion;

  protected AssertionOperation(Assertion assertion) {
    this.assertion = assertion;
  }

  public final void is(final T value) {
    assertion.set("is", value, equalityPredicate(value));
  }

  public final void not(final T value) {
    assertion.set("not", value, equalityPredicate(value).negate());
  }

  private Predicate<StorageInstanceExaminer.PropertyDiscoveryResult> equalityPredicate(
      final T value) {
    return (value == null)
        ? it -> it instanceof StorageInstanceExaminer.NoValue
        : equality(value);
  }

  protected abstract Predicate<StorageInstanceExaminer.PropertyDiscoveryResult> equality(
      final T value);

  public static final class AssertionOperationStr extends AssertionOperation<String> {
    public AssertionOperationStr(Assertion assertion) {
      super(assertion);
    }

    @Override
    protected Predicate<StorageInstanceExaminer.PropertyDiscoveryResult> equality(
        String value) {
      return it -> it instanceof StorageInstanceExaminer.StringFound str
                   && str.string().equals(value);
    }

    public void contains(final String value) {
      if (value == null) {
        throw new IllegalArgumentException("Cannot call str contains with null value!");
      }
      
      assertion.set("contains", value, it -> it instanceof StorageInstanceExaminer.StringFound str
                                             && str.string().contains(value));
    }
  }


  public static final class AssertionOperationBool extends AssertionOperation<Boolean> {
    public AssertionOperationBool(Assertion assertion) {
      super(assertion);
    }

    @Override
    protected Predicate<StorageInstanceExaminer.PropertyDiscoveryResult> equality(
        Boolean value) {
      return it -> it instanceof StorageInstanceExaminer.BooleanFound bool
                   && bool.bool() == value;
    }
  }


  public static final class AssertionOperationJson extends AssertionOperation<Map<String, Object>> {
    public AssertionOperationJson(Assertion assertion) {
      super(assertion);
    }

    @Override
    protected Predicate<StorageInstanceExaminer.PropertyDiscoveryResult> equality(
        Map<String, Object> value) {
      return it -> {
        if (!(it instanceof StorageInstanceExaminer.ComplexFound complex)) {
          return false;
        }

        final var actual = complex.value();
        return actual.size() == value.size()
               && actual.keySet().containsAll(value.keySet())
               && actual.entrySet().stream().allMatch((e) -> {
          final var k = e.getKey();
          final var actualV = e.getValue();
          final var expectedV = value.get(k);
          return (actualV == null && expectedV == null) || Objects.equals(actualV, expectedV);
        });
      };
    }

    public void overlaps(Closure closure) {
      JsonBuilder b = new JsonBuilder();
      Object json = b.call(closure);

      final var op = "overlaps";
      final var strVal = b.toString();
      final Predicate<StorageInstanceExaminer.PropertyDiscoveryResult> p;
      if (json instanceof Map) {
        final var expected = (Map<String, Object>) json;
        p = it -> {
          if (!(it instanceof StorageInstanceExaminer.ComplexFound complex)) {
            return false;
          }

          final var actual = complex.value();
          final Set<String> union = new HashSet<>(expected.keySet());
          union.addAll(actual.keySet());

          return union.stream().anyMatch(k -> Objects.equals(actual.get(k), expected.get(k)));
        };
      } else {
        p = it -> false;
      }
      
      assertion.set(op, strVal, p);
    }
  }


  public static final class AssertionOperationNum extends AssertionOperation<Number> {
    public AssertionOperationNum(Assertion assertion) {
      super(assertion);
    }

    @Override
    protected Predicate<StorageInstanceExaminer.PropertyDiscoveryResult> equality(
        Number value) {
      return it -> {
        if (!(it instanceof StorageInstanceExaminer.NumberFound n)) {
          return false;
        }

        final Number actual = n.number();
        if (actual instanceof Float || actual instanceof Double) {
          final double actualDouble = actual.doubleValue();
          final double expectedDouble = value.doubleValue();
          return Double.compare(actualDouble, expectedDouble) == 0;
        } else {
          final long actualLong = actual.longValue();
          final long expectedLong = value.longValue();
          return actualLong == expectedLong;
        }
      };
    }
  }


  //  public static final class AssertionOperationTime extends AssertionOperation<Temporal> {
  //    public AssertionOperationTime(Assertion assertion) {
  //      super(assertion);
  //    }
  //  }

}

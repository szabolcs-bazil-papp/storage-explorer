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

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import static java.util.stream.Collectors.joining;
import com.aestallon.storageexplorer.arcscript.api.QueryCondition;
import com.aestallon.storageexplorer.core.service.StorageInstanceExaminer;
import groovy.json.JsonBuilder;
import groovy.lang.Closure;

public abstract sealed class AssertionOperation<T> permits
    AssertionOperation.AssertionOperationStr,
    AssertionOperation.AssertionOperationBool,
    AssertionOperation.AssertionOperationJson,
    AssertionOperation.AssertionOperationNum,
    AssertionOperation.AssertionOperationList/*,
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

  @SafeVarargs
  public final void in(final T... values) {
    if (values == null) {
      throw new IllegalArgumentException(
          "Null argument is not acceptable for IN clause! Try `is null` or `in (null)` instead.");
    }

    if (values.length == 0) {
      assertion.set("in", "{{ EMPTY SET }}", it -> false);
      return;
    }

    Arrays.stream(values)
        .map(this::equalityPredicate)
        .reduce(PropertyPredicate::or)
        .ifPresentOrElse(
            p -> assertion.set(
                "in",
                Arrays.stream(values).map(String::valueOf).collect(joining(", ", "( ", " )")),
                p),
            () -> assertion.set(
                "in",
                "{{ EMPTY SET }}",
                it -> false));
  }

  public final void is_empty() {
    assertion.set("is", "empty", it -> it instanceof StorageInstanceExaminer.None);
  }

  public final void is_present() {
    assertion.set("is", "present", it -> it instanceof StorageInstanceExaminer.Some);
  }

  private PropertyPredicate equalityPredicate(final T value) {
    return (value == null)
        ? it -> it instanceof StorageInstanceExaminer.NoValue
        : equality(value);
  }

  protected abstract PropertyPredicate equality(final T value);

  public static final class AssertionOperationStr extends AssertionOperation<String> {
    public AssertionOperationStr(Assertion assertion) {
      super(assertion);
    }

    @Override
    protected PropertyPredicate equality(final String value) {
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

    public void starts_with(final String value) {
      if (value == null) {
        throw new IllegalArgumentException("Cannot call str starts_with with null value!");
      }

      assertion.set(
          "starts_with",
          value,
          it -> it instanceof StorageInstanceExaminer.StringFound str
                && str.string().startsWith(value));
    }

    public void ends_with(final String value) {
      if (value == null) {
        throw new IllegalArgumentException("Cannot call str ends_with with null value!");
      }

      assertion.set(
          "starts_with",
          value,
          it -> it instanceof StorageInstanceExaminer.StringFound str
                && str.string().endsWith(value));
    }
  }


  public static final class AssertionOperationBool extends AssertionOperation<Boolean> {
    public AssertionOperationBool(Assertion assertion) {
      super(assertion);
    }

    @Override
    protected PropertyPredicate equality(final Boolean value) {
      return it -> it instanceof StorageInstanceExaminer.BooleanFound bool
                   && bool.bool() == value;
    }
  }


  public static final class AssertionOperationJson extends AssertionOperation<Map<String, Object>> {
    public AssertionOperationJson(Assertion assertion) {
      super(assertion);
    }

    @Override
    protected PropertyPredicate equality(Map<String, Object> value) {
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
      final PropertyPredicate p;
      if (json instanceof Map) {
        final var expected = (Map<String, Object>) json;
        p = it -> {
          if (!(it instanceof StorageInstanceExaminer.ComplexFound complex)) {
            return false;
          }

          final var actual = complex.value();
          final Set<String> overlap = new HashSet<>(expected.keySet());
          overlap.retainAll(actual.keySet());

          return overlap.stream().anyMatch(k -> Objects.equals(actual.get(k), expected.get(k)));
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
    protected PropertyPredicate equality(final Number value) {
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


  public static final class AssertionOperationList extends AssertionOperation<List<Object>> {
    public AssertionOperationList(Assertion assertion) {
      super(assertion);
    }

    @Override
    protected PropertyPredicate equality(List<Object> value) {
      throw new IllegalArgumentException("Q!");
    }

    public void has_size(final int expected) {
      assertion.set("has_size", expected, it -> it instanceof StorageInstanceExaminer.ListFound list
                                                && list.value().size() == expected);
    }

    public void contains(Object... expected) {
      if (expected == null) {
        throw new IllegalArgumentException("Cannot call list contains with null value!");
      }

      assertion.set(
          "contains",
          Arrays.stream(expected).map(String::valueOf).collect(joining(", ", "( ", " )")),
          listContainsCheck(expected, false));
    }

    private static PropertyPredicate listContainsCheck(Object[] expected, final boolean exact) {
      return it -> {
        if (!(it instanceof StorageInstanceExaminer.ListFound list)) {
          return false;
        }

        final var es = list.value();
        if (exact && es.size() != expected.length) {
          return false;
        }

        for (final Object o : expected) {
          if (es.stream().noneMatch(
              e -> e instanceof StorageInstanceExaminer.Some some && some.val().equals(o))) {
            return false;
          }
        }
        return true;
      };
    }

    public void contains_exactly(Object... expected) {
      if (expected == null) {
        throw new IllegalArgumentException("Cannot call list contains with null value!");
      }

      assertion.set(
          "contains_exactly",
          Arrays.stream(expected).map(String::valueOf).collect(joining(", ", "( ", " )")),
          it -> {
            if (!(it instanceof StorageInstanceExaminer.ListFound list)) {
              return false;
            }

            final var es = list.value();
            if (es.size() != expected.length) {
              return false;
            }

            for (int i = 0; i < es.size(); i++) {
              final var e = es.get(i);
              final var o = expected[i];
              if (!(e instanceof StorageInstanceExaminer.Some some && some.val().equals(o))) {
                return false;
              }
            }
            return true;
          });
    }

    public void contains_exactly_in_any_order(Object... expected) {
      if (expected == null) {
        throw new IllegalArgumentException("Cannot call list contains with null value!");
      }

      assertion.set(
          "contains_exactly_in_any_order",
          Arrays.stream(expected).map(String::valueOf).collect(joining(", ", "( ", " )")),
          listContainsCheck(expected, true));
    }

    public QueryCondition all_match(Closure closure) {
      return match(closure, Assertion.MatchOp.ALL);
    }

    public QueryCondition all_match(QueryConditionImpl condition) {
      return match(condition, Assertion.MatchOp.ALL);
    }

    public QueryCondition any_match(Closure closure) {
      return match(closure, Assertion.MatchOp.ANY);
    }

    public QueryCondition any_match(QueryConditionImpl condition) {
      return match(condition, Assertion.MatchOp.ANY);
    }

    public QueryCondition none_match(Closure closure) {
      return match(closure, Assertion.MatchOp.NONE);
    }

    public QueryCondition none_match(QueryConditionImpl condition) {
      return match(condition, Assertion.MatchOp.NONE);
    }

    private QueryCondition match(Closure closure, Assertion.MatchOp matchOp) {
      final var condition = new QueryConditionImpl();
      assertion.set(matchOp, condition.createClause(closure));
      return condition;
    }

    private QueryCondition match(QueryConditionImpl condition, Assertion.MatchOp matchOp) {
      final var c = new QueryConditionImpl().or(condition);
      assertion.set(matchOp, (QueryConditionImpl) c);
      return c;
    }

  }


  //  public static final class AssertionOperationTime extends AssertionOperation<Temporal> {
  //    public AssertionOperationTime(Assertion assertion) {
  //      super(assertion);
  //    }
  //  }

}

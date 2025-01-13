package com.aestallon.storageexplorer.arcscript.api.internal.query;

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
    assertion.op = "is";
    assertion.value = String.valueOf(value);
    assertion._predicate = equalityPredicate(value);
  }

  public final void not(final T value) {
    assertion.op = "not";
    assertion.value = String.valueOf(value);
    assertion._predicate = equalityPredicate(value).negate();
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
      assertion.op = "contains";
      assertion.value = String.valueOf(value);
      assertion._predicate = it -> it instanceof StorageInstanceExaminer.StringFound str
                                   && str.string().contains(value);
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
      
      assertion.op = "overlaps";
      assertion.value = String.valueOf(b.toString());
      
      if (json instanceof Map) {
        final var expected = (Map<String, Object>) json;
        assertion._predicate = it -> {
          if (!(it instanceof StorageInstanceExaminer.ComplexFound complex)) {
            return false;
          }
          
          final var actual = complex.value();
          final Set<String> union = new HashSet<>(expected.keySet());
          union.addAll(actual.keySet());
          
          return union.stream().anyMatch(k -> Objects.equals(actual.get(k), expected.get(k)));
        };
      } else {
        assertion._predicate = it -> false;
      }
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

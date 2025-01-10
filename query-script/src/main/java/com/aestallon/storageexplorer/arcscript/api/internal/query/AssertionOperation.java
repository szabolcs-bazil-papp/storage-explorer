package com.aestallon.storageexplorer.arcscript.api.internal.query;

import java.time.temporal.Temporal;
import java.util.Map;

public abstract sealed class AssertionOperation<T> permits
    AssertionOperation.AssertionOperationStr,
    AssertionOperation.AssertionOperationBool,
    AssertionOperation.AssertionOperationJson,
    AssertionOperation.AssertionOperationNum,
    AssertionOperation.AssertionOperationTime {
  
  protected final Assertion assertion;

  protected AssertionOperation(Assertion assertion) {
    this.assertion = assertion;
  }

  public final void is(final T value) {
    assertion.op = "is";
    assertion.value = String.valueOf(value);
  }
  
  public static final class AssertionOperationStr extends AssertionOperation<String> {
    public AssertionOperationStr(Assertion assertion) {
      super(assertion);
    }

    public void contains(final String value) {
      assertion.op = "contains";
      assertion.value = String.valueOf(value);
    }
  }
  public static final class AssertionOperationBool extends AssertionOperation<Boolean> {
    public AssertionOperationBool(Assertion assertion) {
      super(assertion);
    }
  }
  public static final class AssertionOperationJson extends AssertionOperation<Map<String, Object>> {
    public AssertionOperationJson(Assertion assertion) {
      super(assertion);
    }
  }
  public static final class AssertionOperationNum extends AssertionOperation<Number> {
    public AssertionOperationNum(Assertion assertion) {
      super(assertion);
    }
  }
  public static final class AssertionOperationTime extends AssertionOperation<Temporal> {
    public AssertionOperationTime(Assertion assertion) {
      super(assertion);
    }
  }
  
}

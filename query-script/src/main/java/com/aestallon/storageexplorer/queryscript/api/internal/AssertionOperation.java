package com.aestallon.storageexplorer.queryscript.api.internal;

public class AssertionOperation {
  
  private final Assertion assertion;
  
  public AssertionOperation(Assertion assertion) {
    this.assertion = assertion;
  }
  
  public void contains(final Object value) {
    assertion.op = "contains";
    assertion.value = String.valueOf(value);
  }
  
  public void is(final Object value) {
    assertion.op = "is";
    assertion.value = String.valueOf(value);
  }
}

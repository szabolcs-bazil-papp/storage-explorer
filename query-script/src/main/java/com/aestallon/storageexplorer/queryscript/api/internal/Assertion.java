package com.aestallon.storageexplorer.queryscript.api.internal;

public class Assertion {
  
  private String prop;
  String op;
  String value;
  
  public AssertionOperation str(final String prop) {
    this.prop = prop;
    return new AssertionOperation(this);
  }
  
  public AssertionOperation bool(final String prop) {
    this.prop = prop;
    return new AssertionOperation(this);
  }

  @Override
  public String toString() {
    return "Assertion {" +
           "\n  prop: " + prop +
           ",\n  op: " + op +
           ",\n  value: " + value +
           "\n}";
  }
}

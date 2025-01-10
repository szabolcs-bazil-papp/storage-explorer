package com.aestallon.storageexplorer.arcscript.api.internal.query;

public class Assertion {
  
  private String prop;
  String op;
  String value;
  
  public AssertionOperation.AssertionOperationStr str(final String prop) {
    this.prop = prop;
    return new AssertionOperation.AssertionOperationStr(this);
  }
  
  public AssertionOperation.AssertionOperationBool bool(final String prop) {
    this.prop = prop;
    return new AssertionOperation.AssertionOperationBool(this);
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

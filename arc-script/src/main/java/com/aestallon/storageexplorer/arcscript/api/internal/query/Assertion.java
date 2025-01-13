package com.aestallon.storageexplorer.arcscript.api.internal.query;

import java.util.function.Predicate;
import com.aestallon.storageexplorer.core.service.StorageInstanceExaminer;

public class Assertion {
  
  public String prop;
  public String op;
  public String value;
  public Predicate<StorageInstanceExaminer.PropertyDiscoveryResult> _predicate;
  
  public AssertionOperation.AssertionOperationStr str(final String prop) {
    this.prop = prop;
    return new AssertionOperation.AssertionOperationStr(this);
  }
  
  public AssertionOperation.AssertionOperationBool bool(final String prop) {
    this.prop = prop;
    return new AssertionOperation.AssertionOperationBool(this);
  }
  
  public AssertionOperation.AssertionOperationNum num(final String prop) {
    this.prop = prop;
    return new AssertionOperation.AssertionOperationNum(this);
  }
  
  public AssertionOperation.AssertionOperationJson json(final String prop) {
    this.prop = prop;
    return new AssertionOperation.AssertionOperationJson(this);
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

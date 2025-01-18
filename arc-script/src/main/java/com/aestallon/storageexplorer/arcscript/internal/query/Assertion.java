package com.aestallon.storageexplorer.arcscript.internal.query;

import java.util.function.Predicate;
import com.aestallon.storageexplorer.core.service.StorageInstanceExaminer;

public final class Assertion implements QueryElement {

  private String prop;
  private String op;
  private String value;
  private Predicate<StorageInstanceExaminer.PropertyDiscoveryResult> _predicate;
  
  void set(String op, Object value, Predicate<StorageInstanceExaminer.PropertyDiscoveryResult> p) {
    this.op = op;
    this.value = String.valueOf(value);
    this._predicate = p;
  }
  
  public String prop() { return prop; }
  public String displayValue() { return value; }
  public boolean check(StorageInstanceExaminer.PropertyDiscoveryResult val) {
    return _predicate.test(val);
  }

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
    return prop + " " + op + " " + value;
  }

}

package com.aestallon.storageexplorer.arcscript.internal.query;

import com.aestallon.storageexplorer.core.service.StorageInstanceExaminer;

public final class Assertion implements QueryElement {

  private String prop;
  private String op;
  private String value;
  private PropertyPredicate _predicate;
  private QueryConditionImpl _listElementCondition;
  
  void set(String op, Object value, PropertyPredicate p) {
    this.op = op;
    this.value = String.valueOf(value);
    this._predicate = p;
  }
  
  void set(String op, QueryConditionImpl c) {
    this.op = op;
    this.value = c.toString();
    this._listElementCondition = c;
  }
  
  public String prop() { return prop; }
  public String displayValue() { return value; }
  public String op() { return op; }
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
  
  public AssertionOperation.AssertionOperationList list(final String prop) {
    this.prop = prop;
    return new AssertionOperation.AssertionOperationList(this);
  }
  
  public boolean isSingle() {
    return _predicate != null;
  }
  
  public QueryConditionImpl listElementCondition() {
    return _listElementCondition;
  }

  @Override
  public String toString() {
    return prop + " " + op + " " + value;
  }

}

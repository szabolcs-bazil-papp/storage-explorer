package com.aestallon.storageexplorer.arcscript.internal.query;

import com.aestallon.storageexplorer.arcscript.api.QueryCondition;
import com.aestallon.storageexplorer.core.service.StorageInstanceExaminer;
import groovy.lang.Closure;

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

  void set(MatchOp op, QueryConditionImpl c) {
    this.op = op.strVal;
    this.value = "subQuery";
    this._listElementCondition = c;
  }

  public String prop() {return prop;}

  public String displayValue() {
    return _listElementCondition != null ? _listElementCondition.toString() : value;
  }

  public String op() {return op;}

  public enum MatchOp {
    ANY("any_match"), ALL("all_match"), NONE("none_match"), UNKNOWN(null);

    private static MatchOp of(final String s) {
      for (final var matchOp : values()) {
        if (matchOp.strVal.equals(s)) {
          return matchOp;
        }
      }
      return UNKNOWN;
    }

    private final String strVal;

    MatchOp(String strVal) {
      this.strVal = strVal;
    }

  }

  public MatchOp matchOp() {return MatchOp.of(op);}

  public QueryCondition expr(Closure closure) {
    QueryConditionImpl condition = new QueryConditionImpl();
    return condition.createClause(closure);
  }

  public QueryCondition expr(QueryCondition condition) {
    return condition;
  }

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
    return prop + " " + op + " " + displayValue();
  }

}

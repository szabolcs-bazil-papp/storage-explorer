package com.aestallon.storageexplorer.queryscript.api.internal;

import com.aestallon.storageexplorer.queryscript.api.Query;
import com.aestallon.storageexplorer.queryscript.api.QueryCondition;
import groovy.lang.Closure;

public class QueryImpl implements Query {

  private String typeName;
  private String schema;
  private QueryCondition condition;

  @Override
  public void a(String typeName) {
    this.typeName = typeName;
  }

  @Override
  public void from(String schema) {
    this.schema = schema;
  }

  @Override
  public QueryCondition where(Closure closure) {
    QueryConditionImpl condition = new QueryConditionImpl();
    this.condition = condition;
    return condition.createClause(closure);
  }

  @Override
  public String toString() {
    return "QueryImpl {" +
           "\n  typeName: " + typeName +
           ",\n  schema: " + schema +
           ",\n  condition: " + condition +
           "\n}";
  }
}

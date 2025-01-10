package com.aestallon.storageexplorer.arcscript.api.internal.query;

import com.aestallon.storageexplorer.arcscript.api.QueryInstruction;
import groovy.lang.Closure;

public class QueryInstructionImpl implements QueryInstruction {

  private String typeName;
  private String schema;
  private QueryCondition condition;
  private long limit = -1L;

  @Override
  public void a(String typeName) {
    this.typeName = typeName;
    this.limit = 1L;
  }

  @Override
  public void every(String typeName) {
    this.typeName = typeName;
    this.limit = -1L;
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
  public void limit(long limit) {
    if (limit < 0) {
      throw new IllegalArgumentException("Query limit cannot be negative!");
    }
    
    if (this.limit == 1L) {
      return;
    }
    
    this.limit = limit;
  }

  @Override
  public String toString() {
    return "QueryImpl {" +
           "\n  typeName: " + typeName +
           ",\n  schema: " + schema +
           ",\n  condition: " + condition +
           ",\n  limit: " + limit +
           "\n}";
  }
}

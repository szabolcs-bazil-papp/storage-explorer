package com.aestallon.storageexplorer.arcscript.api.internal.query;

import java.util.HashSet;
import java.util.Set;
import com.aestallon.storageexplorer.arcscript.api.QueryInstruction;
import groovy.lang.Closure;

public class QueryInstructionImpl implements QueryInstruction {

  public Set<String> _types = new HashSet<>();
  public Set<String> _schemas = new HashSet<>();
  public QueryConditionImpl condition;
  public long _limit = -1L;

  @Override
  public void a(String typeName) {
    final String[] types = { typeName };
    every(types);
    this._limit = 1L;
  }

  @Override
  public void every(String... typeNames) {
    if (typeNames == null || typeNames.length == 0) {
      throw new IllegalArgumentException("typeNames cannot be null or empty");
    }

    for (String typeName : typeNames) {
      if (typeName == null) {
        throw new IllegalArgumentException("typeName cannot be null");
      }

      _types.add(typeName);
    }
    this._limit = -1L;
  }

  @Override
  public void from(String... schemas) {
    if (schemas == null || schemas.length == 0) {
      throw new IllegalArgumentException("schemas cannot be null or empty");
    }
    
    for (String schema : schemas) {
      if (schema == null) {
        throw new IllegalArgumentException("schema cannot be null");
      }
      
      _schemas.add(schema);
    }
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

    if (this._limit == 1L) {
      return;
    }

    this._limit = limit;
  }

  @Override
  public String toString() {
    return "QueryImpl {" +
           "\n  typeName: " + _types +
           ",\n  schema: " + _schemas +
           ",\n  condition: " + condition +
           ",\n  limit: " + _limit +
           "\n}";
  }
}

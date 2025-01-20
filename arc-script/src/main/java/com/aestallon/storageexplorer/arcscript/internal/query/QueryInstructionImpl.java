package com.aestallon.storageexplorer.arcscript.internal.query;

import java.util.HashSet;
import java.util.Set;
import com.aestallon.storageexplorer.arcscript.api.QueryCondition;
import com.aestallon.storageexplorer.arcscript.api.QueryInstruction;
import com.aestallon.storageexplorer.arcscript.internal.Instruction;
import groovy.lang.Closure;

public class QueryInstructionImpl implements QueryInstruction, Instruction {

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
    QueryConditionImpl condition = new QueryConditionImpl(this);
    this.condition = condition;
    return condition.createClause(closure);
  }

  @Override
  public QueryCondition where(QueryCondition condition) {
    this.condition = new QueryConditionImpl(this);
    this.condition.or(condition);
    return this.condition;
  }

  @Override
  public QueryCondition expr(Closure closure) {
    QueryConditionImpl condition = new QueryConditionImpl(this);
    return condition.createClause(closure);
  }

  @Override
  public QueryCondition expr(QueryCondition condition) {
    return condition;
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
    final StringBuilder sb = new StringBuilder("select ");
    if (_types.isEmpty()) {
      sb.append("every type ");
    } else if (_types.size() == 1) {
      sb.append("type ").append(_types.iterator().next()).append(" ");
    } else {
      sb.append("types ").append(_types).append(" ");
    }

    sb.append("from ");
    if (_schemas.isEmpty()) {
      sb.append("every schema ");
    } else if (_schemas.size() == 1) {
      sb.append("schema ").append(_schemas.iterator().next()).append(" ");
    } else {
      sb.append("schemas ").append(_schemas).append(" ");
    }

    final String conditionStr;
    if (this.condition == null) {
      conditionStr = "true";
    } else {
      String condStr = condition.toString();
      conditionStr = condStr.substring(1, condStr.length() - 1);
    }
    sb.append("where ").append(conditionStr);

    if (_limit > 0) {
      sb.append(" limit ").append(_limit);
    }

    return sb.toString();
  }

}

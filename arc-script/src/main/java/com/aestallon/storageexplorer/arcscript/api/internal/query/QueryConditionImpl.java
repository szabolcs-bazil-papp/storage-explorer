package com.aestallon.storageexplorer.arcscript.api.internal.query;

import java.util.ArrayList;
import java.util.List;
import groovy.lang.Closure;

public class QueryConditionImpl implements QueryCondition {

  public final List<List<Assertion>> assertions = new ArrayList<>();

  QueryConditionImpl createClause(final Closure closure) {
    return (QueryConditionImpl) or(closure);
  }

  @Override
  public QueryCondition and(Closure closure) {
    final Assertion assertion = new Assertion();
    final Closure code = closure.rehydrate(assertion, assertion, assertion);
    code.call();
    assertions.getLast().add(assertion);
    return this;
  }

  @Override
  public QueryCondition or(Closure closure) {
    final Assertion assertion = new Assertion();
    final Closure code = closure.rehydrate(assertion, assertion, assertion);
    code.call();
    assertions.add(new ArrayList<>(List.of(assertion)));
    return this;
  }

  @Override
  public String toString() {
    return "QueryConditionImpl {" +
           "\n  assertions: " + assertions +
           "\n}";
  }
  
}

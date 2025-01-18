package com.aestallon.storageexplorer.arcscript.internal.query;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import com.aestallon.storageexplorer.arcscript.api.QueryCondition;
import groovy.lang.Closure;

public final class QueryConditionImpl implements QueryCondition, QueryElement {

  public enum AssertionRelation { AND, OR }


  public record AssertionElement(AssertionRelation relation, QueryElement element) {}
  
  private final QueryInstructionImpl instruction;
  private final List<AssertionElement> elements = new ArrayList<>();
  
  QueryConditionImpl(QueryInstructionImpl instruction) {
    this.instruction = instruction;
  }

  QueryConditionImpl createClause(final Closure closure) {
    return (QueryConditionImpl) or(closure);
  }

  QueryConditionImpl createClause(final QueryCondition condition) {
    return (QueryConditionImpl) or(condition);
  }

  @Override
  public QueryCondition and(Closure closure) {
    final Assertion assertion = new Assertion();
    final Closure code = closure.rehydrate(assertion, assertion, assertion);
    code.call();
    elements.add(new AssertionElement(AssertionRelation.AND, assertion));
    return this;
  }

  @Override
  public QueryCondition or(Closure closure) {
    final Assertion assertion = new Assertion();
    final Closure code = closure.rehydrate(assertion, assertion, assertion);
    code.call();
    elements.add(new AssertionElement(AssertionRelation.OR, assertion));
    return this;
  }

  private QueryCondition or(Closure closure, QueryInstructionImpl instruction) {
    final Assertion assertion = new Assertion();
    final Closure code = closure.rehydrate(assertion, instruction, instruction);
    code.call();
    elements.add(new AssertionElement(AssertionRelation.OR, assertion));
    return this;
  }

  @Override
  public QueryCondition and(QueryCondition condition) {
    if (!(condition instanceof QueryConditionImpl c)) {
      throw new IllegalArgumentException("Unknown condition type: " + condition);
    }
    elements.add(new AssertionElement(AssertionRelation.AND, c));
    return this;
  }

  @Override
  public QueryCondition or(QueryCondition condition) {
    if (!(condition instanceof QueryConditionImpl c)) {
      throw new IllegalArgumentException("Unknown condition type: " + condition);
    }
    elements.add(new AssertionElement(AssertionRelation.OR, c));
    return this;
  }

  @Override
  public String toString() {
    final var sb = new StringBuilder("(");
    for (int i = 0; i < elements.size(); i++) {
      if (i > 0) {
        sb.append(" ").append(elements.get(i).relation).append(" ");
      }
      sb.append(elements.get(i).element);
    }
    return sb.append(")").toString();
  }
  
  public AssertionIterator assertionIterator() {
    return new AssertionIterator(this);
  }
  
  public static final class AssertionIterator implements Iterator<AssertionElement> {

    private final Deque<AssertionElement> stack;

    public AssertionIterator(QueryConditionImpl c) {
      this.stack = new ArrayDeque<>(c.elements);
    }

    @Override
    public boolean hasNext() {
      return !stack.isEmpty();
    }

    @Override
    public AssertionElement next() {
      return stack.pop();
    }
    
    public AssertionRelation peekRelation() {
      return stack.peek().relation;
    }
  }

}

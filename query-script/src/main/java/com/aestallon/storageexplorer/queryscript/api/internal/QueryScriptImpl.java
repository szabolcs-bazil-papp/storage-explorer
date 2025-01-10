package com.aestallon.storageexplorer.queryscript.api.internal;

import java.util.ArrayList;
import java.util.List;
import com.aestallon.storageexplorer.queryscript.api.Query;
import com.aestallon.storageexplorer.queryscript.api.QueryScript;
import groovy.lang.Closure;
import groovy.lang.Script;

public abstract class QueryScriptImpl extends Script implements QueryScript {
  
  private final List<Query> queries = new ArrayList<>();
  
  @Override
  public Query query(Closure closure) {
    final Query q = new QueryImpl();
    final Closure code = closure.rehydrate(q, this, this);
    code.setResolveStrategy(Closure.DELEGATE_ONLY);
    code.call();
    queries.add(q);
    return q;
  }

  @Override
  public String toString() {
    return "QueryScriptImpl {" +
           "\n  queries: " + queries +
           "\n}";
  }
}

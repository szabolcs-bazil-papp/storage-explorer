package com.aestallon.storageexplorer.arcscript.api.internal;

import java.util.ArrayList;
import java.util.List;
import com.aestallon.storageexplorer.arcscript.api.ArcScriptBody;
import com.aestallon.storageexplorer.arcscript.api.IndexInstruction;
import com.aestallon.storageexplorer.arcscript.api.QueryInstruction;
import com.aestallon.storageexplorer.arcscript.api.ArcScript;
import com.aestallon.storageexplorer.arcscript.api.UpdateInstruction;
import com.aestallon.storageexplorer.arcscript.api.internal.query.QueryInstructionImpl;
import groovy.lang.Closure;
import groovy.lang.Script;

public abstract class ArcScriptBodyImpl extends Script implements ArcScriptBody {
  
  private final List<QueryInstruction> queries = new ArrayList<>();
  private final List<IndexInstruction> indices = new ArrayList<>();
  private final List<UpdateInstruction> updates = new ArrayList<>();
  
  @Override
  public QueryInstruction query(Closure closure) {
    final QueryInstruction q = new QueryInstructionImpl();
    final Closure code = closure.rehydrate(q, this, this);
    code.setResolveStrategy(Closure.DELEGATE_ONLY);
    code.call();
    queries.add(q);
    return q;
  }

  @Override
  public String toString() {
    return "ArcScriptBodyImpl {" +
           "\n  queries: " + queries +
           "\n}";
  }
}

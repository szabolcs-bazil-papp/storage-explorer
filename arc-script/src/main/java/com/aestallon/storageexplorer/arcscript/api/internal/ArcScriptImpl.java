package com.aestallon.storageexplorer.arcscript.api.internal;

import java.util.ArrayList;
import java.util.List;
import com.aestallon.storageexplorer.arcscript.api.ArcScript;
import com.aestallon.storageexplorer.arcscript.api.IndexInstruction;
import com.aestallon.storageexplorer.arcscript.api.Instruction;
import com.aestallon.storageexplorer.arcscript.api.QueryInstruction;
import com.aestallon.storageexplorer.arcscript.api.UpdateInstruction;
import com.aestallon.storageexplorer.arcscript.api.internal.index.IndexInstructionImpl;
import com.aestallon.storageexplorer.arcscript.api.internal.query.QueryInstructionImpl;
import com.aestallon.storageexplorer.arcscript.api.internal.update.UpdateInstructionImpl;
import groovy.lang.Closure;
import groovy.lang.Script;

public abstract class ArcScriptImpl extends Script implements ArcScript {
  
  public final List<Instruction> instructions = new ArrayList<>();
  
  @Override
  public QueryInstruction query(Closure closure) {
    final QueryInstruction q = new QueryInstructionImpl();
    final Closure code = closure.rehydrate(q, this, this);
    code.setResolveStrategy(Closure.DELEGATE_ONLY);
    code.call();
    instructions.add(q);
    return q;
  }

  @Override
  public IndexInstruction index(Closure closure) {
    final IndexInstruction indexInstruction = new IndexInstructionImpl();
    final Closure code = closure.rehydrate(indexInstruction, this, this);
    code.setResolveStrategy(Closure.DELEGATE_ONLY);
    code.call();
    instructions.add(indexInstruction);
    return indexInstruction;
  }

  @Override
  public UpdateInstruction update(Closure closure) {
    final UpdateInstruction updateInstruction = new UpdateInstructionImpl();
    final Closure code = closure.rehydrate(updateInstruction, this, this);
    code.setResolveStrategy(Closure.DELEGATE_ONLY);
    code.call();
    instructions.add(updateInstruction);
    return updateInstruction;
  }
  

  @Override
  public String toString() {
    return "ArcScriptBodyImpl {" +
           "\n  instructions: " + instructions +
           "\n}";
  }
}

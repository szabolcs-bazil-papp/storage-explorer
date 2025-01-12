package com.aestallon.storageexplorer.arcscript.api;

import com.aestallon.storageexplorer.arcscript.api.internal.query.QueryCondition;
import groovy.lang.Closure;

public interface QueryInstruction extends Instruction { 
  
  void a(final String typeName);
  
  default void an(final String typeName) {
    a(typeName);
  }
  
  void every(final String typeName);
  
  void from(final String schema);
  
  QueryCondition where(Closure closure);
  
  void limit(final long limit);
  
}

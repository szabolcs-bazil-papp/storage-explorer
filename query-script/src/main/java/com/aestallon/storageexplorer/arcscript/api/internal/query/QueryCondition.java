package com.aestallon.storageexplorer.arcscript.api.internal.query;

import groovy.lang.Closure;

public interface QueryCondition {
  
  QueryCondition and(Closure closure);
  
  QueryCondition or(Closure closure);
  
}

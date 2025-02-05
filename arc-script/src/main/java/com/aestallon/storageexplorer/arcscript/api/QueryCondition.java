package com.aestallon.storageexplorer.arcscript.api;

import groovy.lang.Closure;

public interface QueryCondition {
  
  QueryCondition and(Closure closure);
  
  QueryCondition and(QueryCondition condition);
  
  QueryCondition or(Closure closure);
  
  QueryCondition or(QueryCondition condition);
  
}

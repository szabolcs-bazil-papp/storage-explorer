package com.aestallon.storageexplorer.queryscript.api;

import groovy.lang.Closure;

public interface QueryCondition {
  
  QueryCondition and(Closure closure);
  
  QueryCondition or(Closure closure);
  
}

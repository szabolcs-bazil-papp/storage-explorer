package com.aestallon.storageexplorer.queryscript.api;

import groovy.lang.Closure;

public interface Query {
  
  void a(final String typeName);
  
  default void an(final String typeName) {
    a(typeName);
  }
  
  void from(final String schema);
  
  QueryCondition where(Closure closure);
  
}

package com.aestallon.storageexplorer.arcscript.api;

import groovy.lang.Closure;

public interface QueryInstruction {

  /**
   * 
   * @param typeName
   */
  void a(final String typeName);
  
  default void an(final String typeName) {
    a(typeName);
  }
  
  void every(final String... typeNames);
  
  void from(final String... schema);
  
  QueryCondition where(Closure closure);
  
  QueryCondition where(QueryCondition condition);
  
  void limit(final long limit);
  
  QueryCondition expr(Closure closure);
  
  QueryCondition expr(QueryCondition condition);
  
  Column show(String property);
  
  void show(String property, String... properties);
  
  interface Column {
    
    void as(String displayName);
  
  }
  
}

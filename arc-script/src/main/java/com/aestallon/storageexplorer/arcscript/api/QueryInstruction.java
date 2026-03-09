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

  QueryCondition e(Closure closure);

  QueryCondition e(QueryCondition condition);

  @Deprecated(since = "0.6.0", forRemoval = true)
  Column show(String property);

  @Deprecated(since = "0.6.0", forRemoval = true)
  void show(String property, String... properties);

  YieldInstruction yield(Closure closure);

  SortInstruction order(Closure closure);

  interface Column {

    void as(String displayName);

  }

}

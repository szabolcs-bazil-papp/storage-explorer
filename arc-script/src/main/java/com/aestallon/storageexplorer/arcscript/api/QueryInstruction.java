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

  /**
   * Sources this query from the elements of the stored list bearing the provided name in the
   * schema designated by the {@code from} clause.
   *
   * <p>
   * Mutually exclusive with {@code a}/{@code an}/{@code every} (type-based sourcing) and
   * {@code map}: exactly one source specification is permitted per query instruction.
   *
   * @param name the name of the stored list to query the elements of, not null or blank
   */
  void list(final String name);

  /**
   * Sources this query from the values of the stored map bearing the provided name in the schema
   * designated by the {@code from} clause.
   *
   * <p>
   * Mutually exclusive with {@code a}/{@code an}/{@code every} (type-based sourcing) and
   * {@code list}: exactly one source specification is permitted per query instruction.
   *
   * @param name the name of the stored map to query the values of, not null or blank
   */
  void map(final String name);

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

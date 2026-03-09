package com.aestallon.storageexplorer.core.model.loading;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public record IndexingTarget(Set<String> schemas, Set<String> types) {

  private static final IndexingTarget ANY = new IndexingTarget(
      Collections.emptySet(),
      Collections.emptySet());

  public static IndexingTarget any() {
    return ANY;
  }

  public IndexingTarget {
    Objects.requireNonNull(schemas, "schemas cannot be null!");
    Objects.requireNonNull(types, "types cannot be null!");
  }

  public boolean isAny() {
    return schemas.isEmpty() && types.isEmpty();
  }

  @Override
  public String toString() {
    return "{ schemas: "
        + schemas.stream().collect(Collectors.joining(", ", "[ ", " ]"))
        + ", types: " + types.stream().collect(Collectors.joining(", ", "[ ", " ]"))
        + " }";
  }
}

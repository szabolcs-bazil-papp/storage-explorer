package com.aestallon.storageexplorer.core.model.instance.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum IndexingStrategyType {

  FULL("FULL"),
  INITIAL("INITIAL"),
  ON_DEMAND("ON_DEMAND");

  private final String value;

  IndexingStrategyType(String value) {
    this.value = value;
  }

  @JsonValue
  public String getValue() {
    return value;
  }

  @Override
  public String toString() {
    return String.valueOf(value);
  }

  @JsonCreator
  public static IndexingStrategyType fromValue(String value) {
    for (IndexingStrategyType b : IndexingStrategyType.values()) {
      if (b.value.equals(value)) {
        return b;
      }
    }
    throw new IllegalArgumentException("Unexpected value '" + value + "'");
  }
}

package com.aestallon.storageexplorer.core.model.instance.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum Availability {
  
  AVAILABLE("AVAILABLE"),
  
  UNAVAILABLE("UNAVAILABLE"),
  
  MISCONFIGURED("MISCONFIGURED");

  private final String value;

  Availability(String value) {
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
  public static Availability fromValue(String value) {
    for (Availability b : Availability.values()) {
      if (b.value.equals(value)) {
        return b;
      }
    }
    throw new IllegalArgumentException("Unexpected value '" + value + "'");
  }
}


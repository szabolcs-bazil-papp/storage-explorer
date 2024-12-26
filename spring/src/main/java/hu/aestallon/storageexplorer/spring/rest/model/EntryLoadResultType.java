package hu.aestallon.storageexplorer.spring.rest.model;

import com.fasterxml.jackson.annotation.JsonValue;


import jakarta.annotation.Generated;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * Gets or Sets EntryLoadResultType
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.8.0")
public enum EntryLoadResultType {
  
  FAILED("FAILED"),
  
  SINGLE("SINGLE"),
  
  MULTI("MULTI");

  private String value;

  EntryLoadResultType(String value) {
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
  public static EntryLoadResultType fromValue(String value) {
    for (EntryLoadResultType b : EntryLoadResultType.values()) {
      if (b.value.equals(value)) {
        return b;
      }
    }
    throw new IllegalArgumentException("Unexpected value '" + value + "'");
  }
}


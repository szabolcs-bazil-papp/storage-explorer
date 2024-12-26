package hu.aestallon.storageexplorer.spring.rest.model;

import com.fasterxml.jackson.annotation.JsonValue;


import jakarta.annotation.Generated;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * Gets or Sets StorageEntryType
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.8.0")
public enum StorageEntryType {
  
  LIST("LIST"),
  
  MAP("MAP"),
  
  SEQUENCE("SEQUENCE"),
  
  OBJECT("OBJECT");

  private String value;

  StorageEntryType(String value) {
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
  public static StorageEntryType fromValue(String value) {
    for (StorageEntryType b : StorageEntryType.values()) {
      if (b.value.equals(value)) {
        return b;
      }
    }
    throw new IllegalArgumentException("Unexpected value '" + value + "'");
  }
}


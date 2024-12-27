package hu.aestallon.storageexplorer.core.model.instance.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum StorageInstanceType {
  
  FS("FS"),
  
  DB("DB");

  private final String value;

  StorageInstanceType(String value) {
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
  public static StorageInstanceType fromValue(String value) {
    for (StorageInstanceType b : StorageInstanceType.values()) {
      if (b.value.equals(value)) {
        return b;
      }
    }
    throw new IllegalArgumentException("Unexpected value '" + value + "'");
  }
}


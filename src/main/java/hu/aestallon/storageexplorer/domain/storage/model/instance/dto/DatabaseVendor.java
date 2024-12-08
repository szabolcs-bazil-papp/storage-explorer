package hu.aestallon.storageexplorer.domain.storage.model.instance.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum DatabaseVendor {

  H2("H2"),

  PG("PG"),

  ORACLE("ORACLE"),

  SQLITE("SQLITE");

  private final String value;

  DatabaseVendor(String value) {
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
  public static DatabaseVendor fromValue(String value) {
    for (DatabaseVendor b : DatabaseVendor.values()) {
      if (b.value.equals(value)) {
        return b;
      }
    }
    throw new IllegalArgumentException("Unexpected value '" + value + "'");
  }

  public String driverClassName() {
    switch (this) {
      case H2:
        return "org.h2.Driver";
      case PG:
        return "org.postgresql.Driver";
      case ORACLE:
        return "oracle.jdbc.driver.OracleDriver";
      case SQLITE:
        return "org.sqlite.JDBC";
      default:
        throw new IllegalArgumentException("Unexpected value '" + this + "'");
    }
  }
}


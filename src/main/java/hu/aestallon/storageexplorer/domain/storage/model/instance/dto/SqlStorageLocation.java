package hu.aestallon.storageexplorer.domain.storage.model.instance.dto;

import java.util.Objects;
import jakarta.validation.Valid;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonTypeName;

@JsonPropertyOrder({
  SqlStorageLocation.VENDOR,
  SqlStorageLocation.DB_CONNECTION_DATA,
  SqlStorageLocation.SSH_CONNECTION_DATA
})
@JsonTypeName("SqlStorageLocation")
@jakarta.annotation.Generated(value = "org.openapitools.codegen.languages.JavaClientCodegen")
public final class SqlStorageLocation implements StorageLocation {
  public static final String VENDOR = "vendor";
  private DatabaseVendor vendor;

  public static final String DB_CONNECTION_DATA = "dbConnectionData";
  private DatabaseConnectionData dbConnectionData;

  public static final String SSH_CONNECTION_DATA = "sshConnectionData";
  private SshConnectionData sshConnectionData;

  public SqlStorageLocation() { 
  }

  public SqlStorageLocation vendor(DatabaseVendor vendor) {
    
    this.vendor = vendor;
    return this;
  }

  @jakarta.annotation.Nullable
  @Valid
  @JsonProperty(VENDOR)
  @JsonInclude(value = JsonInclude.Include.USE_DEFAULTS)
  public DatabaseVendor getVendor() {
    return vendor;
  }


  @JsonProperty(VENDOR)
  @JsonInclude(value = JsonInclude.Include.USE_DEFAULTS)
  public void setVendor(DatabaseVendor vendor) {
    this.vendor = vendor;
  }


  public SqlStorageLocation dbConnectionData(DatabaseConnectionData dbConnectionData) {
    this.dbConnectionData = dbConnectionData;
    return this;
  }

  @jakarta.annotation.Nullable
  @Valid
  @JsonProperty(DB_CONNECTION_DATA)
  @JsonInclude(value = JsonInclude.Include.USE_DEFAULTS)
  public DatabaseConnectionData getDbConnectionData() {
    return dbConnectionData;
  }

  @JsonProperty(DB_CONNECTION_DATA)
  @JsonInclude(value = JsonInclude.Include.USE_DEFAULTS)
  public void setDbConnectionData(DatabaseConnectionData dbConnectionData) {
    this.dbConnectionData = dbConnectionData;
  }

  public SqlStorageLocation sshConnectionData(SshConnectionData sshConnectionData) {
    
    this.sshConnectionData = sshConnectionData;
    return this;
  }

  @jakarta.annotation.Nullable
  @Valid
  @JsonProperty(SSH_CONNECTION_DATA)
  @JsonInclude(value = JsonInclude.Include.USE_DEFAULTS)
  public SshConnectionData getSshConnectionData() {
    return sshConnectionData;
  }


  @JsonProperty(SSH_CONNECTION_DATA)
  @JsonInclude(value = JsonInclude.Include.USE_DEFAULTS)
  public void setSshConnectionData(SshConnectionData sshConnectionData) {
    this.sshConnectionData = sshConnectionData;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    SqlStorageLocation sqlStorageLocation = (SqlStorageLocation) o;
    return Objects.equals(this.vendor, sqlStorageLocation.vendor) &&
        Objects.equals(this.dbConnectionData, sqlStorageLocation.dbConnectionData) &&
        Objects.equals(this.sshConnectionData, sqlStorageLocation.sshConnectionData);
  }

  @Override
  public int hashCode() {
    return Objects.hash(vendor, dbConnectionData, sshConnectionData);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class SqlStorageLocation {\n");
    sb.append("    vendor: ").append(toIndentedString(vendor)).append("\n");
    sb.append("    dbConnectionData: ").append(toIndentedString(dbConnectionData)).append("\n");
    sb.append("    sshConnectionData: ").append(toIndentedString(sshConnectionData)).append("\n");
    sb.append("}");
    return sb.toString();
  }

  private String toIndentedString(Object o) {
    if (o == null) {
      return "null";
    }
    return o.toString().replace("\n", "\n    ");
  }

}


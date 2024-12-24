package hu.aestallon.storageexplorer.domain.storage.model.instance.dto;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import jakarta.validation.constraints.*;
import org.springframework.core.env.MapPropertySource;

@JsonPropertyOrder({
    DatabaseConnectionData.URL,
    DatabaseConnectionData.USERNAME,
    DatabaseConnectionData.PASSWORD
})
@JsonTypeName("DatabaseConnectionData")
@jakarta.annotation.Generated(value = "org.openapitools.codegen.languages.JavaClientCodegen")
public class DatabaseConnectionData {
  public static final String URL = "url";
  private String url;

  public static final String USERNAME = "username";
  private String username;

  public static final String PASSWORD = "password";
  private String password;

  public DatabaseConnectionData() {
  }

  public DatabaseConnectionData url(String url) {

    this.url = url;
    return this;
  }

  @jakarta.annotation.Nonnull
  @NotNull
  @JsonProperty(URL)
  @JsonInclude(value = JsonInclude.Include.ALWAYS)
  public String getUrl() {
    return url;
  }


  @JsonProperty(URL)
  @JsonInclude(value = JsonInclude.Include.ALWAYS)
  public void setUrl(String url) {
    this.url = url;
  }


  public DatabaseConnectionData username(String username) {
    this.username = username;
    return this;
  }

  @jakarta.annotation.Nonnull
  @NotNull
  @JsonProperty(USERNAME)
  @JsonInclude(value = JsonInclude.Include.ALWAYS)
  public String getUsername() {
    return username;
  }


  @JsonProperty(USERNAME)
  @JsonInclude(value = JsonInclude.Include.ALWAYS)
  public void setUsername(String username) {
    this.username = username;
  }


  public DatabaseConnectionData password(String password) {

    this.password = password;
    return this;
  }

  @jakarta.annotation.Nonnull
  @NotNull
  @JsonProperty(PASSWORD)
  @JsonInclude(value = JsonInclude.Include.ALWAYS)
  public String getPassword() {
    return password;
  }


  @JsonProperty(PASSWORD)
  @JsonInclude(value = JsonInclude.Include.ALWAYS)
  public void setPassword(String password) {
    this.password = password;
  }


  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    DatabaseConnectionData databaseConnectionData = (DatabaseConnectionData) o;
    return Objects.equals(this.url, databaseConnectionData.url) &&
        Objects.equals(this.username, databaseConnectionData.username) &&
        Objects.equals(this.password, databaseConnectionData.password);
  }

  @Override
  public int hashCode() {
    return Objects.hash(url, username, password);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class DatabaseConnectionData {\n");
    sb.append("    url: ").append(toIndentedString(url)).append("\n");
    sb.append("    username: ").append(toIndentedString(username)).append("\n");
    sb.append("    password: ").append(toIndentedString(password)).append("\n");
    sb.append("}");
    return sb.toString();
  }

  private String toIndentedString(Object o) {
    if (o == null) {
      return "null";
    }
    return o.toString().replace("\n", "\n    ");
  }


  public Map<String, Object> asProperties() {
    return Map.of(
        "spring.datasource.url", url,
        "spring.datasource.username", username,
        "spring.datasource.password", password);
  }

}


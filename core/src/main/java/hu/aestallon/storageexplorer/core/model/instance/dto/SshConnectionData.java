package hu.aestallon.storageexplorer.core.model.instance.dto;

import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import java.math.BigDecimal;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import jakarta.validation.constraints.*;
import jakarta.validation.Valid;

@JsonPropertyOrder({
  SshConnectionData.DOMAIN,
  SshConnectionData.PORT,
  SshConnectionData.USERNAME,
  SshConnectionData.PASSWORD,
  SshConnectionData.PORT_MAPPING
})
@JsonTypeName("SshConnectionData")
@jakarta.annotation.Generated(value = "org.openapitools.codegen.languages.JavaClientCodegen")
public class SshConnectionData {
  public static final String DOMAIN = "domain";
  private String domain;

  public static final String PORT = "port";
  private BigDecimal port;

  public static final String USERNAME = "username";
  private String username;

  public static final String PASSWORD = "password";
  private String password;

  public static final String PORT_MAPPING = "portMapping";
  private PortMapping portMapping;

  public SshConnectionData() { 
  }

  public SshConnectionData domain(String domain) {
    this.domain = domain;
    return this;
  }

  @jakarta.annotation.Nonnull
  @NotNull
  @JsonProperty(DOMAIN)
  @JsonInclude(value = JsonInclude.Include.ALWAYS)
  public String getDomain() {
    return domain;
  }


  @JsonProperty(DOMAIN)
  @JsonInclude(value = JsonInclude.Include.ALWAYS)
  public void setDomain(String domain) {
    this.domain = domain;
  }

  public SshConnectionData port(BigDecimal port) {
    
    this.port = port;
    return this;
  }

  @jakarta.annotation.Nonnull
  @NotNull
  @Valid
  @JsonProperty(PORT)
  @JsonInclude(value = JsonInclude.Include.ALWAYS)
  public BigDecimal getPort() {
    return port;
  }

  @JsonProperty(PORT)
  @JsonInclude(value = JsonInclude.Include.ALWAYS)
  public void setPort(BigDecimal port) {
    this.port = port;
  }

  public SshConnectionData username(String username) {
    
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

  public SshConnectionData password(String password) {
    
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

  public SshConnectionData portMapping(PortMapping portMapping) {
    this.portMapping = portMapping;
    return this;
  }

  @jakarta.annotation.Nullable
  @Valid
  @JsonProperty(PORT_MAPPING)
  @JsonInclude(value = JsonInclude.Include.USE_DEFAULTS)

  public PortMapping getPortMapping() {
    return portMapping;
  }

  @JsonProperty(PORT_MAPPING)
  @JsonInclude(value = JsonInclude.Include.USE_DEFAULTS)
  public void setPortMapping(PortMapping portMapping) {
    this.portMapping = portMapping;
  }


  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    SshConnectionData sshConnectionData = (SshConnectionData) o;
    return Objects.equals(this.domain, sshConnectionData.domain) &&
        Objects.equals(this.port, sshConnectionData.port) &&
        Objects.equals(this.username, sshConnectionData.username) &&
        Objects.equals(this.password, sshConnectionData.password) &&
        Objects.equals(this.portMapping, sshConnectionData.portMapping);
  }

  @Override
  public int hashCode() {
    return Objects.hash(domain, port, username, password, portMapping);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class SshConnectionData {\n");
    sb.append("    domain: ").append(toIndentedString(domain)).append("\n");
    sb.append("    port: ").append(toIndentedString(port)).append("\n");
    sb.append("    username: ").append(toIndentedString(username)).append("\n");
    sb.append("    password: ").append(toIndentedString(password)).append("\n");
    sb.append("    portMapping: ").append(toIndentedString(portMapping)).append("\n");
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


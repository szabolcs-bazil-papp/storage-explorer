package com.aestallon.storageexplorer.spring.rest.model;

import java.net.URI;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import io.swagger.v3.oas.annotations.media.Schema;


import jakarta.annotation.Generated;

/**
 * Reference
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.8.0")
public class Reference {

  private String propName;

  private URI uri;

  private Integer pos;

  public Reference() {
    super();
  }

  /**
   * Constructor with only required parameters
   */
  public Reference(String propName, URI uri) {
    this.propName = propName;
    this.uri = uri;
  }

  public Reference propName(String propName) {
    this.propName = propName;
    return this;
  }

  /**
   * Get propName
   * @return propName
   */
  @NotNull 
  @Schema(name = "propName", requiredMode = Schema.RequiredMode.REQUIRED)
  @JsonProperty("propName")
  public String getPropName() {
    return propName;
  }

  public void setPropName(String propName) {
    this.propName = propName;
  }

  public Reference uri(URI uri) {
    this.uri = uri;
    return this;
  }

  /**
   * Get uri
   * @return uri
   */
  @NotNull @Valid 
  @Schema(name = "uri", requiredMode = Schema.RequiredMode.REQUIRED)
  @JsonProperty("uri")
  public URI getUri() {
    return uri;
  }

  public void setUri(URI uri) {
    this.uri = uri;
  }

  public Reference pos(Integer pos) {
    this.pos = pos;
    return this;
  }

  /**
   * Get pos
   * @return pos
   */
  
  @Schema(name = "pos", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("pos")
  public Integer getPos() {
    return pos;
  }

  public void setPos(Integer pos) {
    this.pos = pos;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Reference reference = (Reference) o;
    return Objects.equals(this.propName, reference.propName) &&
        Objects.equals(this.uri, reference.uri) &&
        Objects.equals(this.pos, reference.pos);
  }

  @Override
  public int hashCode() {
    return Objects.hash(propName, uri, pos);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class Reference {\n");
    sb.append("    propName: ").append(toIndentedString(propName)).append("\n");
    sb.append("    uri: ").append(toIndentedString(uri)).append("\n");
    sb.append("    pos: ").append(toIndentedString(pos)).append("\n");
    sb.append("}");
    return sb.toString();
  }

  /**
   * Convert the given object to string with each line indented by 4 spaces
   * (except the first line).
   */
  private String toIndentedString(Object o) {
    if (o == null) {
      return "null";
    }
    return o.toString().replace("\n", "\n    ");
  }
}


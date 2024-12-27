package com.aestallon.storageexplorer.spring.rest.model;

import java.net.URI;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import io.swagger.v3.oas.annotations.media.Schema;


import jakarta.annotation.Generated;

/**
 * StorageEntryDto
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.8.0")
public class StorageEntryDto {

  private StorageEntryType type;

  private URI uri;

  private String schema;

  private String name;

  private String typeName;

  private Long seqVal;

  private URI scopeHost;

  @Valid
  private List<@Valid Reference> references = new ArrayList<>();

  public StorageEntryDto() {
    super();
  }

  /**
   * Constructor with only required parameters
   */
  public StorageEntryDto(StorageEntryType type, URI uri, String schema, String name, List<@Valid Reference> references) {
    this.type = type;
    this.uri = uri;
    this.schema = schema;
    this.name = name;
    this.references = references;
  }

  public StorageEntryDto type(StorageEntryType type) {
    this.type = type;
    return this;
  }

  /**
   * Get type
   * @return type
   */
  @NotNull @Valid 
  @Schema(name = "type", requiredMode = Schema.RequiredMode.REQUIRED)
  @JsonProperty("type")
  public StorageEntryType getType() {
    return type;
  }

  public void setType(StorageEntryType type) {
    this.type = type;
  }

  public StorageEntryDto uri(URI uri) {
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

  public StorageEntryDto schema(String schema) {
    this.schema = schema;
    return this;
  }

  /**
   * Get schema
   * @return schema
   */
  @NotNull 
  @Schema(name = "schema", requiredMode = Schema.RequiredMode.REQUIRED)
  @JsonProperty("schema")
  public String getSchema() {
    return schema;
  }

  public void setSchema(String schema) {
    this.schema = schema;
  }

  public StorageEntryDto name(String name) {
    this.name = name;
    return this;
  }

  /**
   * Get name
   * @return name
   */
  @NotNull 
  @Schema(name = "name", requiredMode = Schema.RequiredMode.REQUIRED)
  @JsonProperty("name")
  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public StorageEntryDto typeName(String typeName) {
    this.typeName = typeName;
    return this;
  }

  /**
   * Type identifier returned for OBJECTs. 
   * @return typeName
   */
  
  @Schema(name = "typeName", description = "Type identifier returned for OBJECTs. ", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("typeName")
  public String getTypeName() {
    return typeName;
  }

  public void setTypeName(String typeName) {
    this.typeName = typeName;
  }

  public StorageEntryDto seqVal(Long seqVal) {
    this.seqVal = seqVal;
    return this;
  }

  /**
   * Current sequence value returned for SEQUENCEs. 
   * @return seqVal
   */
  
  @Schema(name = "seqVal", description = "Current sequence value returned for SEQUENCEs. ", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("seqVal")
  public Long getSeqVal() {
    return seqVal;
  }

  public void setSeqVal(Long seqVal) {
    this.seqVal = seqVal;
  }

  public StorageEntryDto scopeHost(URI scopeHost) {
    this.scopeHost = scopeHost;
    return this;
  }

  /**
   * Host entry URI returned for scoped entries. 
   * @return scopeHost
   */
  @Valid 
  @Schema(name = "scopeHost", description = "Host entry URI returned for scoped entries. ", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("scopeHost")
  public URI getScopeHost() {
    return scopeHost;
  }

  public void setScopeHost(URI scopeHost) {
    this.scopeHost = scopeHost;
  }

  public StorageEntryDto references(List<@Valid Reference> references) {
    this.references = references;
    return this;
  }

  public StorageEntryDto addReferencesItem(Reference referencesItem) {
    if (this.references == null) {
      this.references = new ArrayList<>();
    }
    this.references.add(referencesItem);
    return this;
  }

  /**
   * Get references
   * @return references
   */
  @NotNull @Valid 
  @Schema(name = "references", requiredMode = Schema.RequiredMode.REQUIRED)
  @JsonProperty("references")
  public List<@Valid Reference> getReferences() {
    return references;
  }

  public void setReferences(List<@Valid Reference> references) {
    this.references = references;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    StorageEntryDto storageEntryDto = (StorageEntryDto) o;
    return Objects.equals(this.type, storageEntryDto.type) &&
        Objects.equals(this.uri, storageEntryDto.uri) &&
        Objects.equals(this.schema, storageEntryDto.schema) &&
        Objects.equals(this.name, storageEntryDto.name) &&
        Objects.equals(this.typeName, storageEntryDto.typeName) &&
        Objects.equals(this.seqVal, storageEntryDto.seqVal) &&
        Objects.equals(this.scopeHost, storageEntryDto.scopeHost) &&
        Objects.equals(this.references, storageEntryDto.references);
  }

  @Override
  public int hashCode() {
    return Objects.hash(type, uri, schema, name, typeName, seqVal, scopeHost, references);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class StorageEntryDto {\n");
    sb.append("    type: ").append(toIndentedString(type)).append("\n");
    sb.append("    uri: ").append(toIndentedString(uri)).append("\n");
    sb.append("    schema: ").append(toIndentedString(schema)).append("\n");
    sb.append("    name: ").append(toIndentedString(name)).append("\n");
    sb.append("    typeName: ").append(toIndentedString(typeName)).append("\n");
    sb.append("    seqVal: ").append(toIndentedString(seqVal)).append("\n");
    sb.append("    scopeHost: ").append(toIndentedString(scopeHost)).append("\n");
    sb.append("    references: ").append(toIndentedString(references)).append("\n");
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


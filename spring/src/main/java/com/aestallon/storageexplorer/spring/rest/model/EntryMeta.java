/*
 * Copyright (C) 2025 Szabolcs Bazil Papp
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along with this program.
 * If not, see <http://www.gnu.org/licenses/>.
 */

package com.aestallon.storageexplorer.spring.rest.model;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.Objects;
import org.springframework.format.annotation.DateTimeFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Generated;
import jakarta.validation.Valid;

/**
 * EntryMeta
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.8.0")
public class EntryMeta {

  private URI uri;

  private String qualifiedName;

  private String storageSchema;

  private Long versionNr;

  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
  private OffsetDateTime createdAt;

  private Long lastModifiedAt;

  public EntryMeta uri(URI uri) {
    this.uri = uri;
    return this;
  }

  /**
   * Get uri
   * @return uri
   */
  @Valid 
  @Schema(name = "uri", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("uri")
  public URI getUri() {
    return uri;
  }

  public void setUri(URI uri) {
    this.uri = uri;
  }

  public EntryMeta qualifiedName(String qualifiedName) {
    this.qualifiedName = qualifiedName;
    return this;
  }

  /**
   * Get qualifiedName
   * @return qualifiedName
   */
  
  @Schema(name = "qualifiedName", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("qualifiedName")
  public String getQualifiedName() {
    return qualifiedName;
  }

  public void setQualifiedName(String qualifiedName) {
    this.qualifiedName = qualifiedName;
  }

  public EntryMeta storageSchema(String storageSchema) {
    this.storageSchema = storageSchema;
    return this;
  }

  /**
   * Get storageSchema
   * @return storageSchema
   */
  
  @Schema(name = "storageSchema", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("storageSchema")
  public String getStorageSchema() {
    return storageSchema;
  }

  public void setStorageSchema(String storageSchema) {
    this.storageSchema = storageSchema;
  }

  public EntryMeta versionNr(Long versionNr) {
    this.versionNr = versionNr;
    return this;
  }

  /**
   * Get versionNr
   * @return versionNr
   */
  
  @Schema(name = "versionNr", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("versionNr")
  public Long getVersionNr() {
    return versionNr;
  }

  public void setVersionNr(Long versionNr) {
    this.versionNr = versionNr;
  }

  public EntryMeta createdAt(OffsetDateTime createdAt) {
    this.createdAt = createdAt;
    return this;
  }

  /**
   * Get createdAt
   * @return createdAt
   */
  @Valid 
  @Schema(name = "createdAt", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("createdAt")
  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(OffsetDateTime createdAt) {
    this.createdAt = createdAt;
  }

  public EntryMeta lastModifiedAt(Long lastModifiedAt) {
    this.lastModifiedAt = lastModifiedAt;
    return this;
  }

  /**
   * Get lastModifiedAt
   * @return lastModifiedAt
   */
  
  @Schema(name = "lastModifiedAt", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("lastModifiedAt")
  public Long getLastModifiedAt() {
    return lastModifiedAt;
  }

  public void setLastModifiedAt(Long lastModifiedAt) {
    this.lastModifiedAt = lastModifiedAt;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    EntryMeta entryMeta = (EntryMeta) o;
    return Objects.equals(this.uri, entryMeta.uri) &&
        Objects.equals(this.qualifiedName, entryMeta.qualifiedName) &&
        Objects.equals(this.storageSchema, entryMeta.storageSchema) &&
        Objects.equals(this.versionNr, entryMeta.versionNr) &&
        Objects.equals(this.createdAt, entryMeta.createdAt) &&
        Objects.equals(this.lastModifiedAt, entryMeta.lastModifiedAt);
  }

  @Override
  public int hashCode() {
    return Objects.hash(uri, qualifiedName, storageSchema, versionNr, createdAt, lastModifiedAt);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class EntryMeta {\n");
    sb.append("    uri: ").append(toIndentedString(uri)).append("\n");
    sb.append("    qualifiedName: ").append(toIndentedString(qualifiedName)).append("\n");
    sb.append("    storageSchema: ").append(toIndentedString(storageSchema)).append("\n");
    sb.append("    versionNr: ").append(toIndentedString(versionNr)).append("\n");
    sb.append("    createdAt: ").append(toIndentedString(createdAt)).append("\n");
    sb.append("    lastModifiedAt: ").append(toIndentedString(lastModifiedAt)).append("\n");
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


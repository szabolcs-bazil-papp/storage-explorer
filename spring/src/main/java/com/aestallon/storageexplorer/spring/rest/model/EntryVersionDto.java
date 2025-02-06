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

import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.HashMap;
import java.util.Map;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import io.swagger.v3.oas.annotations.media.Schema;


import jakarta.annotation.Generated;

/**
 * EntryVersionDto
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.8.0")
public class EntryVersionDto {

  private EntryMeta meta;

  @Valid
  private Map<String, Object> objectAsMap = new HashMap<>();

  public EntryVersionDto() {
    super();
  }

  /**
   * Constructor with only required parameters
   */
  public EntryVersionDto(Map<String, Object> objectAsMap) {
    this.objectAsMap = objectAsMap;
  }

  public EntryVersionDto meta(EntryMeta meta) {
    this.meta = meta;
    return this;
  }

  /**
   * Get meta
   * @return meta
   */
  @Valid 
  @Schema(name = "meta", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("meta")
  public EntryMeta getMeta() {
    return meta;
  }

  public void setMeta(EntryMeta meta) {
    this.meta = meta;
  }

  public EntryVersionDto objectAsMap(Map<String, Object> objectAsMap) {
    this.objectAsMap = objectAsMap;
    return this;
  }

  public EntryVersionDto putObjectAsMapItem(String key, Object objectAsMapItem) {
    if (this.objectAsMap == null) {
      this.objectAsMap = new HashMap<>();
    }
    this.objectAsMap.put(key, objectAsMapItem);
    return this;
  }

  /**
   * Get objectAsMap
   * @return objectAsMap
   */
  @NotNull 
  @Schema(name = "objectAsMap", requiredMode = Schema.RequiredMode.REQUIRED)
  @JsonProperty("objectAsMap")
  public Map<String, Object> getObjectAsMap() {
    return objectAsMap;
  }

  public void setObjectAsMap(Map<String, Object> objectAsMap) {
    this.objectAsMap = objectAsMap;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    EntryVersionDto entryVersionDto = (EntryVersionDto) o;
    return Objects.equals(this.meta, entryVersionDto.meta) &&
        Objects.equals(this.objectAsMap, entryVersionDto.objectAsMap);
  }

  @Override
  public int hashCode() {
    return Objects.hash(meta, objectAsMap);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class EntryVersionDto {\n");
    sb.append("    meta: ").append(toIndentedString(meta)).append("\n");
    sb.append("    objectAsMap: ").append(toIndentedString(objectAsMap)).append("\n");
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


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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Generated;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * EntryLoadResult
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.8.0")
public class EntryLoadResult {

  private EntryLoadResultType type;

  private StorageEntryDto entry;

  @Valid
  private List<@Valid EntryVersionDto> versions = new ArrayList<>();

  public EntryLoadResult() {
    super();
  }

  /**
   * Constructor with only required parameters
   */
  public EntryLoadResult(List<@Valid EntryVersionDto> versions) {
    this.versions = versions;
  }

  public EntryLoadResult type(EntryLoadResultType type) {
    this.type = type;
    return this;
  }

  /**
   * Get type
   * @return type
   */
  @Valid 
  @Schema(name = "type", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("type")
  public EntryLoadResultType getType() {
    return type;
  }

  public void setType(EntryLoadResultType type) {
    this.type = type;
  }

  public EntryLoadResult entry(StorageEntryDto entry) {
    this.entry = entry;
    return this;
  }

  /**
   * Get entry
   * @return entry
   */
  @Valid 
  @Schema(name = "entry", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("entry")
  public StorageEntryDto getEntry() {
    return entry;
  }

  public void setEntry(StorageEntryDto entry) {
    this.entry = entry;
  }

  public EntryLoadResult versions(List<@Valid EntryVersionDto> versions) {
    this.versions = versions;
    return this;
  }

  public EntryLoadResult addVersionsItem(EntryVersionDto versionsItem) {
    if (this.versions == null) {
      this.versions = new ArrayList<>();
    }
    this.versions.add(versionsItem);
    return this;
  }

  /**
   * Get versions
   * @return versions
   */
  @NotNull @Valid 
  @Schema(name = "versions", requiredMode = Schema.RequiredMode.REQUIRED)
  @JsonProperty("versions")
  public List<@Valid EntryVersionDto> getVersions() {
    return versions;
  }

  public void setVersions(List<@Valid EntryVersionDto> versions) {
    this.versions = versions;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    EntryLoadResult entryLoadResult = (EntryLoadResult) o;
    return Objects.equals(this.type, entryLoadResult.type) &&
        Objects.equals(this.entry, entryLoadResult.entry) &&
        Objects.equals(this.versions, entryLoadResult.versions);
  }

  @Override
  public int hashCode() {
    return Objects.hash(type, entry, versions);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class EntryLoadResult {\n");
    sb.append("    type: ").append(toIndentedString(type)).append("\n");
    sb.append("    entry: ").append(toIndentedString(entry)).append("\n");
    sb.append("    versions: ").append(toIndentedString(versions)).append("\n");
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


/*
 * Copyright (C) 2024 it4all Hungary Kft.
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

package hu.aestallon.storageexplorer.domain.userconfig.model;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.validation.constraints.NotNull;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonTypeName;

@JsonPropertyOrder({
    "graphTraversalInboundLimit",
    "graphTraversalOutboundLimit",
    "importedStorageLocations"
})
@JsonTypeName("UserConfig")
public final class UserConfig {

  @JsonProperty("graphTraversalInboundLimit")
  private Integer graphTraversalInboundLimit = 0;

  @JsonProperty("graphTraversalOutboundLimit")
  private Integer graphTraversalOutboundLimit = 2;

  @JsonProperty("importedStorageLocations")
  private List<Path> importedStorageLocations = new ArrayList<>();

  public UserConfig() {}

  public UserConfig graphTraversalInboundLimit(Integer graphTraversalInboundLimit) {
    this.graphTraversalInboundLimit = graphTraversalInboundLimit;
    return this;
  }

  @NotNull
  @JsonProperty("graphTraversalInboundLimit")
  public Integer getGraphTraversalInboundLimit() {
    return graphTraversalInboundLimit;
  }

  public void setGraphTraversalInboundLimit(Integer graphTraversalInboundLimit) {
    this.graphTraversalInboundLimit = graphTraversalInboundLimit;
  }

  public UserConfig graphTraversalOutboundLimit(Integer graphTraversalOutboundLimit) {
    this.graphTraversalOutboundLimit = graphTraversalOutboundLimit;
    return this;
  }

  @JsonProperty("graphTraversalOutboundLimit")
  public Integer getGraphTraversalOutboundLimit() {
    return graphTraversalOutboundLimit;
  }

  @JsonProperty("graphTraversalOutboundLimit")
  public void setGraphTraversalOutboundLimit(Integer graphTraversalOutboundLimit) {
    this.graphTraversalOutboundLimit = graphTraversalOutboundLimit;
  }

  public UserConfig importedStorageLocations(List<Path> importedStorageLocations) {
    this.importedStorageLocations = importedStorageLocations;
    return this;
  }

  public UserConfig addImportedStorageLocationsItem(Path importedStorageLocationsItem) {
    this.importedStorageLocations.add(importedStorageLocationsItem);
    return this;
  }

  @NotNull
  @JsonProperty("importedStorageLocations")
  public List<Path> getImportedStorageLocations() {
    return importedStorageLocations;
  }

  @JsonProperty("importedStorageLocations")
  public void setImportedStorageLocations(List<Path> importedStorageLocations) {
    this.importedStorageLocations = importedStorageLocations;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    UserConfig userConfig = (UserConfig) o;
    return Objects.equals(this.graphTraversalInboundLimit, userConfig.graphTraversalInboundLimit) &&
        Objects.equals(this.graphTraversalOutboundLimit, userConfig.graphTraversalOutboundLimit) &&
        Objects.equals(this.importedStorageLocations, userConfig.importedStorageLocations);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        graphTraversalInboundLimit,
        graphTraversalOutboundLimit,
        importedStorageLocations);
  }

  @Override
  public String toString() {
    return "class UserConfig {\n"
        + "    graphTraversalInboundLimit: "
        + toIndentedString(graphTraversalInboundLimit) + "\n"
        + "    graphTraversalOutboundLimit: "
        + toIndentedString(graphTraversalOutboundLimit) + "\n"
        + "    importedStorageLocations: " + toIndentedString(importedStorageLocations)
        + "\n"
        + "}";
  }

  /**
   * Convert the given object to string with each line indented by 4 spaces (except the first
   * line).
   */
  private String toIndentedString(Object o) {
    if (o == null) {
      return "null";
    }
    return o.toString().replace("\n", "\n    ");
  }
}

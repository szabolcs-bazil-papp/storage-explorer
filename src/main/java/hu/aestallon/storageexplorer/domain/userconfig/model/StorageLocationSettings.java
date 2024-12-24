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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import jakarta.validation.constraints.NotNull;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonTypeName;
import hu.aestallon.storageexplorer.domain.storage.model.instance.dto.StorageInstanceDto;

@JsonPropertyOrder({
    "importedStorageLocations"
})
@JsonTypeName("StorageLocationSettings")
public class StorageLocationSettings {

  @JsonProperty("importedStorageLocations")
  private List<StorageInstanceDto> importedStorageLocations = new ArrayList<>();

  public StorageLocationSettings() {}

  public StorageLocationSettings importedStorageLocations(List<StorageInstanceDto> importedStorageLocations) {
    this.importedStorageLocations = importedStorageLocations;
    return this;
  }

  public StorageLocationSettings addImportedStorageLocationsItem(StorageInstanceDto importedStorageLocationsItem) {
    this.importedStorageLocations.add(importedStorageLocationsItem);
    return this;
  }

  @NotNull
  @JsonProperty("importedStorageLocations")
  public List<StorageInstanceDto> getImportedStorageLocations() {
    return importedStorageLocations;
  }

  @JsonProperty("importedStorageLocations")
  public void setImportedStorageLocations(List<StorageInstanceDto> importedStorageLocations) {
    this.importedStorageLocations = importedStorageLocations;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;
    StorageLocationSettings that = (StorageLocationSettings) o;
    return Objects.equals(importedStorageLocations, that.importedStorageLocations);
  }

  @Override
  public int hashCode() {
    return Objects.hash(importedStorageLocations);
  }

  @Override
  public String toString() {
    return "StorageLocationSettings {\n"
        + "    importedStorageLocations: "
        + toIndentedString(importedStorageLocations) + "\n"
        + "}";
  }

  private String toIndentedString(Object o) {
    if (o == null) {
      return "null";
    }
    return o.toString().replace("\n", "\n    ");
  }
}

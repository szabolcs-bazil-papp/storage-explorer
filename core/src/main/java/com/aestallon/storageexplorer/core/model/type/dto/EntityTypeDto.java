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

package com.aestallon.storageexplorer.core.model.type.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import com.aestallon.storageexplorer.core.model.type.EntityType;
import com.aestallon.storageexplorer.core.model.type.Property;
import com.fasterxml.jackson.annotation.JsonProperty;

public class EntityTypeDto {

  public static EntityTypeDto of(EntityType entityType) {
    final var dto = new EntityTypeDto();
    dto.setName(entityType.name());
    dto.setProperties(entityType.properties().stream()
        .map(PropertyDto::of)
        .toList());
    return dto;
  }


  private String name;
  private List<PropertyDto> properties = new ArrayList<>();

  public EntityTypeDto() {}

  public EntityType toDomainObject() {
    final List<Property> props = properties.stream()
        .map(PropertyDto::toDomainObject)
        .toList();
    return new EntityType(name, props);
  }

  @JsonProperty("name")
  public String getName() {
    return name;
  }

  @JsonProperty("name")
  public void setName(String name) {
    this.name = name;
  }

  @JsonProperty("properties")
  public List<PropertyDto> getProperties() {
    return properties;
  }

  @JsonProperty("properties")
  public void setProperties(
      List<PropertyDto> properties) {
    this.properties = properties;
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass())
      return false;
    EntityTypeDto that = (EntityTypeDto) o;
    return Objects.equals(name, that.name) && Objects.equals(properties,
        that.properties);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, properties);
  }

  @Override
  public String toString() {
    return "EntityTypeDto {" +
        "\n  name: " + name +
        ",\n  properties: " + properties +
        "\n}";
  }

}

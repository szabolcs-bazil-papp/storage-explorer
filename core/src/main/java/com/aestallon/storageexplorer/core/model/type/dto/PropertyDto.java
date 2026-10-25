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

import java.util.Objects;
import com.aestallon.storageexplorer.core.model.type.Property;
import com.fasterxml.jackson.annotation.JsonProperty;

public class PropertyDto {

  public static PropertyDto of(Property property) {
    final var dto = new PropertyDto();
    dto.setKey(property.key());
    dto.setType(PropertyTypeDto.of(property.type()));
    return dto;
  }

  private String key;
  private PropertyTypeDto type;

  public PropertyDto() {}

  public Property toDomainObject() {
    final var pType = type.toDomainObject();
    return new Property(key, pType);
  }

  @JsonProperty("key")
  public String getKey() {
    return key;
  }

  @JsonProperty("key")
  public void setKey(String key) {
    this.key = key;
  }

  @JsonProperty("type")
  public PropertyTypeDto getType() {
    return type;
  }

  @JsonProperty("type")
  public void setType(PropertyTypeDto type) {
    this.type = type;
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass())
      return false;
    PropertyDto that = (PropertyDto) o;
    return Objects.equals(key, that.key) && Objects.equals(type, that.type);
  }

  @Override
  public int hashCode() {
    return Objects.hash(key, type);
  }

  @Override
  public String toString() {
    return "PropertyDto {" +
        "\n  key: " + key +
        ",\n  type: " + type +
        "\n}";
  }

}

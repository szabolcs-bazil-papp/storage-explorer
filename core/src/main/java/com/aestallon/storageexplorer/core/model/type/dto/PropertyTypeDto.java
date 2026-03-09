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
import com.aestallon.storageexplorer.core.model.type.Property;
import com.aestallon.storageexplorer.core.model.type.PropertyType;
import com.fasterxml.jackson.annotation.JsonProperty;

public class PropertyTypeDto {

  public static PropertyTypeDto of(PropertyType propertyType) {
    final var dto = new PropertyTypeDto();
    dto.setArity(switch (propertyType.arity()) {
      case ONE -> ArityVariant.ONE;
      case MANY -> ArityVariant.MANY;
    });
    switch (propertyType) {
      case PropertyType.Primitive p -> {
        dto.setpVariant(switch (p.type()) {
          case STR -> PrimitiveVariant.STR;
          case NUM -> PrimitiveVariant.NUM;
          case BOOL -> PrimitiveVariant.BOOL;
          case TIME -> PrimitiveVariant.TIME;
          case NULL -> PrimitiveVariant.NULL;
        });
        dto.setTag(PropertyTypeTag.PRIMITIVE);
      }
      case PropertyType.Ref r -> {
        dto.setrType(r.entityName());
        dto.setTag(PropertyTypeTag.REFERENCE);
      }
      case PropertyType.Complex c -> {
        dto.setcProps(c.properties().stream()
            .map(PropertyDto::of)
            .toList());
        dto.setTag(PropertyTypeTag.COMPLEX);
      }
      case PropertyType.Union u -> {
        dto.setuMembers(u.types().stream()
            .map(PropertyTypeDto::of)
            .toList());
        dto.setTag(PropertyTypeTag.UNION);
      }
      case PropertyType.EmptyArray e -> dto.setTag(PropertyTypeTag.EMPTY_ARRAY);
      case PropertyType.Unknown u -> dto.setTag(PropertyTypeTag.UNKNOWN);
    }

    return dto;
  }

  private PropertyTypeTag tag;
  private ArityVariant arity;
  private PrimitiveVariant pVariant;
  private String rType;
  private List<PropertyTypeDto> uMembers = new ArrayList<>();
  private List<PropertyDto> cProps = new ArrayList<>();

  public PropertyTypeDto() {}

  public PropertyType toDomainObject() {
    final var pArity = switch (arity) {
      case ONE -> PropertyType.Arity.ONE;
      case MANY -> PropertyType.Arity.MANY;
    };

    return switch (tag) {
      case PRIMITIVE -> {
        final var primitiveType = switch (pVariant) {
          case STR -> PropertyType.PrimitiveType.STR;
          case NUM -> PropertyType.PrimitiveType.NUM;
          case BOOL -> PropertyType.PrimitiveType.BOOL;
          case TIME -> PropertyType.PrimitiveType.TIME;
          case NULL -> PropertyType.PrimitiveType.NULL;
        };
        yield new PropertyType.Primitive(primitiveType, pArity);
      }
      case REFERENCE -> new PropertyType.Ref(rType, pArity);
      case COMPLEX -> {
        final List<Property> props = cProps.stream()
            .map(PropertyDto::toDomainObject)
            .toList();
        yield new PropertyType.Complex(props, pArity);
      }
      case UNION -> {
        final List<PropertyType> members = uMembers.stream()
            .map(PropertyTypeDto::toDomainObject)
            .toList();
        yield new PropertyType.Union(members, pArity);
      }
      case EMPTY_ARRAY -> new PropertyType.EmptyArray();
      case UNKNOWN ->  PropertyType.UNKNOWN;
    };
  }

  @JsonProperty("tag")
  public PropertyTypeTag getTag() {
    return tag;
  }

  @JsonProperty("tag")
  public void setTag(PropertyTypeTag tag) {
    this.tag = tag;
  }

  @JsonProperty("arity")
  public ArityVariant getArity() {
    return arity;
  }

  @JsonProperty("arity")
  public void setArity(ArityVariant arity) {
    this.arity = arity;
  }

  @JsonProperty("pVariant")
  public PrimitiveVariant getpVariant() {
    return pVariant;
  }

  @JsonProperty("pVariant")
  public void setpVariant(PrimitiveVariant pVariant) {
    this.pVariant = pVariant;
  }

  @JsonProperty("rType")
  public String getrType() {
    return rType;
  }

  @JsonProperty("rType")
  public void setrType(String rType) {
    this.rType = rType;
  }

  @JsonProperty("uMembers")
  public List<PropertyTypeDto> getuMembers() {
    return uMembers;
  }

  @JsonProperty("uMembers")
  public void setuMembers(
      List<PropertyTypeDto> uMembers) {
    this.uMembers = uMembers;
  }

  @JsonProperty("cProps")
  public List<PropertyDto> getcProps() {
    return cProps;
  }

  @JsonProperty("cProps")
  public void setcProps(
      List<PropertyDto> cProps) {
    this.cProps = cProps;
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass())
      return false;
    PropertyTypeDto that = (PropertyTypeDto) o;
    return tag == that.tag && arity == that.arity && pVariant == that.pVariant
        && Objects.equals(rType, that.rType) && Objects.equals(uMembers,
        that.uMembers) && Objects.equals(cProps, that.cProps);
  }

  @Override
  public int hashCode() {
    return Objects.hash(tag, arity, pVariant, rType, uMembers, cProps);
  }

  @Override
  public String toString() {
    return "PropertyTypeDto {" +
        "\n  tag: " + tag +
        ",\n  arity: " + arity +
        ",\n  pVariant: " + pVariant +
        ",\n  rType: " + rType +
        ",\n  uMembers: " + uMembers +
        ",\n  cProps: " + cProps +
        "\n}";
  }
}

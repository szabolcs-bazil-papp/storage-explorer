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

package com.aestallon.storageexplorer.core.model.type;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public sealed interface PropertyType {

  static PropertyType.Complex ofComplex(Property... properties) {
    return new PropertyType.Complex(List.of(properties), Arity.ONE);
  }

  static PropertyType.Complex ofComplexArray(Property... properties) {
    return new PropertyType.Complex(List.of(properties), Arity.MANY);
  }

  static PropertyType.Union union(PropertyType... types) {
    return new PropertyType.Union(List.of(types), Arity.ONE);
  }


  enum Arity { ONE, MANY }

  Arity arity();

  default boolean isArityOne() {
    return Arity.ONE == arity();
  }

  PropertyType withArity(Arity arity);

  boolean satisfies(PropertyType other);

  enum PrimitiveType { STR, NUM, BOOL, TIME, NULL }


  record Primitive(PrimitiveType type, Arity arity) implements PropertyType {
    @Override
    public PropertyType withArity(Arity arity) {
      if (this.arity == arity) {
        return this;
      }

      return new Primitive(type, arity);
    }

    @Override
    public String toString() {
      final var typeName = type.name().toLowerCase();
      return arity == Arity.ONE ? typeName : "[" + typeName + "]";
    }

    @Override
    public boolean satisfies(PropertyType other) {
      return equals(other)
          || other instanceof Union u && u.types().stream().anyMatch(this::satisfies);
    }
  }


  record Complex(List<Property> properties, Arity arity) implements PropertyType, PropertyHolder {
    @Override
    public PropertyType withArity(Arity arity) {
      if (this.arity == arity) {
        return this;
      }

      return new Complex(properties, arity);
    }

    @Override
    public boolean equals(Object obj) {
      if (!(obj instanceof Complex(List<Property> thatProperties, Arity thatArity))) {
        return false;
      }

      if (arity != thatArity) {
        return false;
      }

      if (properties.size() != thatProperties.size()) {
        return false;
      }

      final var thisPropsByKey = properties.stream().collect(Collectors.toMap(
          Property::key,
          Property::type));
      final var thatPropsByKey = thatProperties.stream().collect(Collectors.toMap(
          Property::key,
          Property::type));
      return thisPropsByKey.equals(thatPropsByKey);
    }

    @Override
    public String toString() {
      final var sb = new StringBuilder();
      if (arity == Arity.MANY) {
        sb.append("[");
      }
      sb.append("{ ");
      /*for (int i = 0; i < properties.size(); i++) {
        sb.append(properties.get(i).toString());
        if (i < properties.size() - 1) {
          sb.append(", ");
        }
      }*/
      sb.append(" }");
      if (arity == Arity.MANY) {
        sb.append("]");
      }
      return sb.toString();
    }

    @Override
    public boolean satisfies(PropertyType other) {
      if (other instanceof Complex c && c.properties.isEmpty()) {
        // as a special rule, we ALWAYS satisfy the empty complex, as it has a special meaning: unknown shape:
        return true;
      }

      if (other instanceof Complex c) {
        final Map<String, PropertyType> thisProps = properties.stream()
            .collect(Collectors.toMap(Property::key, Property::type));
        final Map<String, PropertyType> thatProps = c.properties.stream()
            .collect(Collectors.toMap(Property::key, Property::type));
        final var sharedKeys = thisProps.keySet().stream()
            .filter(thatProps::containsKey)
            .collect(Collectors.toSet());
        final boolean sharedPropsAreSatisfied = sharedKeys.stream()
            .allMatch(key -> thisProps.get(key).satisfies(thatProps.get(key)));
        if (!sharedPropsAreSatisfied) {
          return false;
        }

        // the properties only in this instance are satisfactory if they satisfy NULL:
        final var ourPropertiesAreNotMandatory = thisProps.entrySet().stream()
            .filter(e -> !sharedKeys.contains(e.getKey()))
            .map(Map.Entry::getValue)
            .allMatch(it -> it.satisfies(PropertyType.NULL));
        if (!ourPropertiesAreNotMandatory) {
          return false;
        }

        // their properties are satisfied if NULL satisfies them (as we lack them):
        return thatProps.entrySet().stream()
            .filter(e -> !sharedKeys.contains(e.getKey()))
            .map(Map.Entry::getValue)
            .allMatch(PropertyType.NULL::satisfies);
      }

      if (other instanceof Union u) {
        return u.types().stream().anyMatch(this::satisfies);
      }

      return false;
    }
  }


  record Ref(String entityName, Arity arity) implements PropertyType {
    @Override
    public PropertyType withArity(Arity arity) {
      if (this.arity == arity) {
        return this;
      }

      return new Ref(entityName, arity);
    }

    @Override
    public String toString() {
      return (arity == Arity.ONE ? entityName : "[" + entityName + "]");
    }

    @Override
    public boolean satisfies(PropertyType other) {
      if (arity != other.arity()) {
        return false;
      }

      return switch (other) {
        case Ref(String otherEntityName, var otherArity) ->
            entityName.equals(otherEntityName) || "?".equals(otherEntityName);
        case Primitive p when p.type() == PrimitiveType.NULL -> true;
        // if this is a concrete type, we satisfy the union if we match even one variant:
        case Union u -> u.types().stream().anyMatch(this::satisfies);
        default -> false;
      };
    }
  }


  record Union(List<PropertyType> types, Arity arity) implements PropertyType {

    public boolean hasComplex() {
      return types.stream().anyMatch(PropertyType.Complex.class::isInstance);
    }

    public List<Complex> complexes() {
      return types.stream()
          .filter(PropertyType.Complex.class::isInstance)
          .map(PropertyType.Complex.class::cast)
          .toList();
    }

    @Override
    public PropertyType withArity(Arity arity) {
      if (this.arity == arity) {
        return this;
      }

      return new Union(types, arity);
    }

    @Override
    public boolean equals(Object obj) {
      if (!(obj instanceof Union(List<PropertyType> thatTypes, Arity thatArity))) {
        return false;
      }

      if (arity != thatArity) {
        return false;
      }

      if (types.size() != thatTypes.size()) {
        return false;
      }

      return new HashSet<>(thatTypes).containsAll(types);
    }

    @Override
    public String toString() {
      final var sb = new StringBuilder();
      if (arity == Arity.MANY) {
        sb.append("[");
      }

      for (int i = 0; i < types.size(); i++) {
        var p = types.get(i);
        sb.append(p.toString());
        if (i < types.size() - 1) {
          sb.append(" | ");
        }
      }

      if (arity == Arity.MANY) {
        sb.append("]");
      }

      return sb.toString();
    }

    @Override
    public boolean satisfies(PropertyType other) {
      return other instanceof Union u && types.stream().allMatch(our -> u.types().stream().anyMatch(our::satisfies))
          // we can only satisfy a non-union, if somehow all our variants satisfy it:
          || types.stream().allMatch(it -> it.satisfies(other));
    }
  }


  record EmptyArray() implements PropertyType {

    @Override
    public Arity arity() {
      return Arity.MANY;
    }

    @Override
    public PropertyType withArity(Arity arity) {
      return this;
    }

    @Override
    public String toString() {
      return "[?]";
    }

    @Override
    public boolean satisfies(PropertyType other) {
      if (other.arity() == Arity.MANY) {
        // empty array satisfies any array:
        return true;
      }

      if (other instanceof Union u) {
        return u.types().stream().anyMatch(this::satisfies);
      }

      return false;
    }
  }


  PropertyType NULL = new Primitive(PrimitiveType.NULL, Arity.ONE);
  PropertyType STR = new Primitive(PrimitiveType.STR, Arity.ONE);
  PropertyType NUM = new Primitive(PrimitiveType.NUM, Arity.ONE);
  PropertyType BOOL = new Primitive(PrimitiveType.BOOL, Arity.ONE);
  PropertyType TIME = new Primitive(PrimitiveType.TIME, Arity.ONE);

  PropertyType NULL_ARRAY = new Primitive(PrimitiveType.NULL, Arity.MANY);
  PropertyType STR_ARRAY = new Primitive(PrimitiveType.STR, Arity.MANY);
  PropertyType NUM_ARRAY = new Primitive(PrimitiveType.NUM, Arity.MANY);
  PropertyType BOOL_ARRAY = new Primitive(PrimitiveType.BOOL, Arity.MANY);
  PropertyType TIME_ARRAY = new Primitive(PrimitiveType.TIME, Arity.MANY);

}

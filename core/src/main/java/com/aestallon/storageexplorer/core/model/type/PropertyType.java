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

import java.util.List;

public sealed interface PropertyType {


  enum Arity {
    ONE,
    MANY,
  }

  Arity arity();

  PropertyType withArity(Arity arity);


  enum PrimitiveType {
    STR,
    NUM,
    BOOL,
    TIME,
    NULL,
  }


  record Primitive(PrimitiveType type, Arity arity) implements PropertyType {
    @Override
    public PropertyType withArity(Arity arity) {
      if (this.arity == arity) {
        return this;
      }

      return new Primitive(type, arity);
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
  }


  record Ref(String entityName, Arity arity) implements PropertyType {
    @Override
    public PropertyType withArity(Arity arity) {
      if (this.arity == arity) {
        return this;
      }

      return new Ref(entityName, arity);
    }
  }


  record Union(List<PropertyType> types, Arity arity) implements PropertyType {

    public boolean isWiderThan(PropertyType other) {
      // TODO: this is not enough, we must observe lists and stuff!
      return types.contains(other);
    }

    @Override
    public PropertyType withArity(Arity arity) {
      if (this.arity == arity) {
        return this;
      }

      return new Union(types, arity);
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

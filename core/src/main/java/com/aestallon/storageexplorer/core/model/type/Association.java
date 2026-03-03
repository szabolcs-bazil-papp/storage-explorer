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

public record Association(
    String from,
    String to,
    PropertyType.Arity arity,
    String propertyPath,
    boolean optional) {

  public Association(String from, String to, PropertyType.Arity arity, String propertyPath) {
    this(from, to, arity, propertyPath, false);
  }

  Association withOptional(boolean optional) {
    if (this.optional == optional) {
      return this;
    }

    return new Association(from, to, arity, propertyPath, optional);
  }

}

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

package com.aestallon.storageexplorer.swing.ui.graph;

import java.util.List;

public record Entity(String uniqueName, List<PropertyEntry> properties) {}

enum Arity { ONE, MANY }

record PropertyEntry(Property property, Arity arity ) {}

record Property(String key, PropertyType type) {}

sealed interface PropertyType {}

enum Inline implements PropertyType { STRING, NUMBER, BOOLEAN }

record Reference(String entityUniqueName) implements PropertyType {}

record Detail(List<PropertyEntry> properties) implements PropertyType {}

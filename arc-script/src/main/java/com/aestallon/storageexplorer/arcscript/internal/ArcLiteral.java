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

package com.aestallon.storageexplorer.arcscript.internal;

import java.util.ArrayList;
import java.util.List;

public final class ArcLiteral {

  private final List<String> segments;

  public ArcLiteral(String initialSegment) {
    segments = new ArrayList<>();
    segments.add(initialSegment);
  }

  public ArcLiteral propertyMissing(String name) {
    segments.add(name);
    return this;
  }

  @Override
  public String toString() {
    return String.join(".", segments);
  }

}

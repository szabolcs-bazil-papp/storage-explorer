/*
 * Copyright (C) 2026 Szabolcs Bazil Papp
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

package com.aestallon.storageexplorer.arcscript.engine;

import com.aestallon.storageexplorer.core.model.entry.UriProperty;
import com.aestallon.storageexplorer.core.service.index.PropertyPath;

/** Converts an ArcScript dotted property-path string (an {@code Assertion}/{@code
 *  ColumnDescriptor}/{@code SortKey}'s {@code prop()}/{@code target()}) into the {@link
 *  PropertyPath} a {@link com.aestallon.storageexplorer.core.service.index.Projection} column is
 *  keyed by - every stage sharing one {@code Projection} needs to mint the identical column for
 *  the identical path string. */
final class ArcPropertyPaths {

  private ArcPropertyPaths() {}

  static PropertyPath toPath(final String dotted) {
    return PropertyPath.of(UriProperty.Segment.parse(dotted));
  }

}

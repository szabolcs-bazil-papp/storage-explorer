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

package com.aestallon.storageexplorer.core.service.fs;

import java.util.Arrays;

/**
 * One entry of an object stream.
 *
 * @param start where the entry starts in the file (its first length field)
 * @param end   where it ends, i.e. where the next entry starts
 * @param data  the inflated content, normally a JSON document
 */
public record StreamEntry(long start, long end, byte[] data) {

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof StreamEntry other)) {
      return false;
    }
    return start == other.start && end == other.end && Arrays.equals(data, other.data);
  }

  @Override
  public int hashCode() {
    return java.util.Objects.hash(start, end, Arrays.hashCode(data));
  }
}

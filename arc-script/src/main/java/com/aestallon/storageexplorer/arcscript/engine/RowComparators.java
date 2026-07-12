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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.temporal.Temporal;
import java.util.Comparator;
import java.util.List;
import com.aestallon.storageexplorer.arcscript.api.SortInstruction;

/**
 * Comparison semantics of the {@code order} clause, shared by every {@link QueryEngine}
 * implementation.
 */
final class RowComparators {

  private RowComparators() {}

  static Comparator<ArcScriptResult.QueryResultRow> of(
      final List<SortInstruction.SortKey> sortKeys) {
    Comparator<ArcScriptResult.QueryResultRow> comparator = null;
    for (final var sortKey : sortKeys) {
      final var c = rowComparator(sortKey);
      comparator = comparator == null ? c : comparator.thenComparing(c);
    }
    // no sort keys means every row is equivalent - never hand out a null comparator:
    return comparator == null ? (a, b) -> 0 : comparator;
  }

  private static Comparator<ArcScriptResult.QueryResultRow> rowComparator(
      final SortInstruction.SortKey sortKey) {
    return (a, b) -> {
      final var prop = sortKey.target();
      final var asc = sortKey.asc();

      final Object aProp = a.cells().get(prop).value();
      final Object bProp = b.cells().get(prop).value();

      if (aProp == null && bProp == null) {
        // nulls are equivalent:
        return 0;
      }

      if (aProp == null) {
        // following Oracle, if the ordering is ascending, the default is NULLS LAST:
        return asc ? 1 : -1;
      }

      if (bProp == null) {
        // as per the same convention:
        return asc ? -1 : 1;
      }

      switch (aProp) {
        case Number aNum when bProp instanceof Number bNum -> {
          final var res = Double.compare(aNum.doubleValue(), bNum.doubleValue());
          return asc ? res : -res;
        }
        case String aStr when bProp instanceof String bStr -> {
          final var res = aStr.compareTo(bStr);
          return asc ? res : -res;
        }
        case Boolean aBool when bProp instanceof Boolean bBool -> {
          final var res = Boolean.compare(aBool, bBool);
          return asc ? res : -res;
        }
        case OffsetDateTime aDate when bProp instanceof OffsetDateTime bDate -> {
          final var res = aDate.compareTo(bDate);
          return asc ? res : -res;
        }
        case LocalDateTime aDate when bProp instanceof LocalDateTime bDate -> {
          final var res = aDate.compareTo(bDate);
          return asc ? res : -res;
        }
        case LocalDate aDate when bProp instanceof LocalDate bDate -> {
          final var res = aDate.compareTo(bDate);
          return asc ? res : -res;
        }
        default -> {}
      }

      // these were the comparison we support for same types. For diverging types, the following
      // ascending sort order is observed:
      // num -> str -> bool -> date -> any
      final var aIdx = getTypeIdx(aProp);
      final var bIdx = getTypeIdx(bProp);
      final var res = Integer.compare(aIdx, bIdx);
      return asc ? res : -res;
    };
  }

  private static final Class<?>[] TYPE_ORDER =
      { Number.class, String.class, Boolean.class, Temporal.class };

  private static int getTypeIdx(Object o) {
    for (int i = 0; i < TYPE_ORDER.length; i++) {
      if (TYPE_ORDER[i].isInstance(o)) {
        return i;
      }
    }

    return TYPE_ORDER.length;
  }

}

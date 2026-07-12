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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;
import com.aestallon.storageexplorer.arcscript.api.SortInstruction;
import static org.assertj.core.api.Assertions.assertThat;

class RowComparatorsTest {

  private static final String PROP = "p";

  private static ArcScriptResult.QueryResultRow row(final Object value) {
    return new ArcScriptResult.QueryResultRow(
        null,
        Map.of(PROP, new ArcScriptResult.DataCell(ArcScriptResult.CellType.SIMPLE, value)));
  }

  private static SortInstruction.SortKey key(final boolean asc) {
    return new SortInstruction.SortKey(PROP, asc);
  }

  @Test
  void ascendingNumbers_areSortedNumerically() {
    final var rows = new ArrayList<>(List.of(row(3), row(1L), row(2.5d)));
    rows.sort(RowComparators.of(List.of(key(true))));
    assertThat(rows).extracting(r -> r.cells().get(PROP).value())
        .containsExactly(1L, 2.5d, 3);
  }

  @Test
  void descendingStrings_areSortedLexicographicallyReversed() {
    final var rows = new ArrayList<>(List.of(row("apple"), row("cherry"), row("banana")));
    rows.sort(RowComparators.of(List.of(key(false))));
    assertThat(rows).extracting(r -> r.cells().get(PROP).value())
        .containsExactly("cherry", "banana", "apple");
  }

  @Test
  void ascendingSort_placesNullsLast() {
    final var rows = new ArrayList<>(List.of(row(null), row("a"), row(null), row("b")));
    rows.sort(RowComparators.of(List.of(key(true))));
    assertThat(rows).extracting(r -> r.cells().get(PROP).value())
        .containsExactly("a", "b", null, null);
  }

  @Test
  void descendingSort_placesNullsFirst() {
    final var rows = new ArrayList<>(List.of(row("a"), row(null), row("b")));
    rows.sort(RowComparators.of(List.of(key(false))));
    assertThat(rows).extracting(r -> r.cells().get(PROP).value())
        .containsExactly(null, "b", "a");
  }

  @Test
  void divergingTypes_observeNumberStringBooleanOrder() {
    final var rows = new ArrayList<>(List.of(row(true), row("str"), row(42)));
    rows.sort(RowComparators.of(List.of(key(true))));
    assertThat(rows).extracting(r -> r.cells().get(PROP).value())
        .containsExactly(42, "str", true);
  }

  @Test
  void topKSelection_isEquivalentToFullSortThenTruncate() {
    // property-based safety net for the ordering stage's bounded heap: maintaining the K best
    // elements incrementally must be equivalent to fully sorting and truncating.
    final var random = new Random(42L);
    final Comparator<ArcScriptResult.QueryResultRow> comparator =
        RowComparators.of(List.of(key(true)));
    for (int round = 0; round < 50; round++) {
      final int n = 1 + random.nextInt(500);
      final int k = 1 + random.nextInt(n);
      final List<ArcScriptResult.QueryResultRow> rows = new ArrayList<>();
      for (int i = 0; i < n; i++) {
        rows.add(row(random.nextInt(100)));
      }

      final var heap = new java.util.PriorityQueue<>(k, comparator.reversed());
      for (final var r : rows) {
        if (heap.size() < k) {
          heap.offer(r);
        } else if (comparator.compare(r, heap.peek()) < 0) {
          heap.poll();
          heap.offer(r);
        }
      }
      final var topK = new ArrayList<ArcScriptResult.QueryResultRow>();
      while (!heap.isEmpty()) {
        topK.add(heap.poll());
      }
      Collections.reverse(topK);

      final var expected = rows.stream().sorted(comparator).limit(k).toList();
      assertThat(topK).extracting(r -> r.cells().get(PROP).value())
          .containsExactlyElementsOf(
              expected.stream().map(r -> r.cells().get(PROP).value()).toList());
    }
  }

}

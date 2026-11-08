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

package com.aestallon.storageexplorer.core.service.index;

/**
 * The value at one {@link Projection} cell. Deliberately just a terminal scalar holder — per the
 * sketch's own "known-shaky parts" note, whether a cell also needs to self-report "this value is
 * itself a reference, keep walking" (as a sealed {@code Scalar}/{@code Reference} split) rather
 * than leaving that entirely to {@link Projection#provenanceOf} was left genuinely open. Resolved
 * pragmatically for this milestone's "simplest correct implementation" goal: a cell's value and
 * its provenance (which entry it was ultimately read from, after following however many
 * references the path crossed to get there) are reported by two separate calls, exactly as the
 * sketch's method signatures already have it — nothing here forces the harder question yet.
 */
public sealed interface CellValue {

  /** No value at this (row, column) — the path didn't resolve (a missing field, an index out of
   *  range, or a reference that couldn't be followed), distinct from a legitimate JSON
   *  {@code null} (carried as {@code Scalar(null)}). */
  CellValue MISSING = new Missing();

  record Scalar(Object value) implements CellValue {}

  record Missing() implements CellValue {}
}

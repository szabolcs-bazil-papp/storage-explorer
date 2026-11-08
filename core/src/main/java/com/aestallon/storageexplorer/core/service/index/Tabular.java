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

/** A {@link StorageIndex} capable of handing out {@link Projection}s — the ArcScript query
 *  engine's malleable tabular working set (Milestone 5 is the first real consumer). */
public interface Tabular extends StorageIndex {

  /** One projection per query execution (or per sub-phase, if CTEs end up wanting their own). */
  Projection newProjection();
}

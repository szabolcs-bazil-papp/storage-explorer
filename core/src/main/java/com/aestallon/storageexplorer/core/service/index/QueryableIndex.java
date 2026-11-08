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
 * The combined capability {@code StorageInstanceExaminer}'s property-discovery recursion needs
 * at every step (T4.4): loading an entry's content ({@link ContentLoadable}) and discovering its
 * outgoing references ({@link AdjacencyAware}) to continue a property path across entries. A
 * {@code StorageInstance}'s operation-scoped graph index already satisfies both.
 */
public interface QueryableIndex extends ContentLoadable, AdjacencyAware {
}

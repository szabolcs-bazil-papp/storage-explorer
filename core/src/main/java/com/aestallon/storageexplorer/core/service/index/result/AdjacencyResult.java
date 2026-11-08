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

package com.aestallon.storageexplorer.core.service.index.result;

import java.util.Set;
import com.aestallon.storageexplorer.core.model.entry.StorageEntry;
import com.aestallon.storageexplorer.core.model.entry.UriProperty;

/**
 * The outcome of {@code AdjacencyAware#outgoingReferences(StorageEntry)}.
 *
 * <p>Uses the existing (still {@code java.net.URI}-based) {@link UriProperty} as-is — per
 * Milestone 2's additive discipline, this task doesn't touch {@code UriProperty} itself; its own
 * migration to {@code EntryUri} happens in Milestone 4's breaking swap, alongside every other
 * signature the implementation plan's overview calls out.
 */
public sealed interface AdjacencyResult {

  record Ok(StorageEntry entry, Set<UriProperty> references) implements AdjacencyResult {}

  record Err(StorageEntry entry, String reason) implements AdjacencyResult {}
}

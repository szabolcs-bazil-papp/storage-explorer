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

import java.util.Optional;
import com.aestallon.storageexplorer.core.model.EntryUri;

/**
 * The head of an object.
 *
 * @param uri                 the object's URI
 * @param descriptor          the descriptor; always present for versioned objects, a
 *                             single-version object may have none (its descriptor part is empty)
 * @param pendingTransaction  true if a {@code .t} file sits next to the {@code .o}: a write is in
 *                             flight or crashed. The committed state is served regardless.
 */
public record Head(EntryUri uri, Optional<ObjectData> descriptor, boolean pendingTransaction) {}

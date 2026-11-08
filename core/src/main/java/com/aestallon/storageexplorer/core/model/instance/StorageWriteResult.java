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

package com.aestallon.storageexplorer.core.model.instance;

import java.util.OptionalLong;

/**
 * The outcome of {@link StorageInstance#newVersion}/{@link StorageInstance#amend} — a small,
 * {@code StorageInstance}-owned result type, not {@code StorageEngine}'s own {@code
 * EngineResult}/{@code StorageEngineError} returned directly: the whole point of this class's
 * command-shaped public API (T4.3) is that callers outside {@code core} never see the engine's
 * raw vocabulary.
 */
public sealed interface StorageWriteResult {

  /** {@code serial} is empty for a single-version object (it has none to report) and for a
   *  successful {@link StorageInstance#amend} (it never gets a new serial, only the existing
   *  one amended in place). */
  record Ok(OptionalLong serial) implements StorageWriteResult {}

  record Err(String reason) implements StorageWriteResult {}
}

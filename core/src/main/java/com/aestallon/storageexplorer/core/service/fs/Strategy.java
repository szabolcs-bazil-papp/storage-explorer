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
import com.aestallon.storageexplorer.core.service.engine.EngineResult;
import com.aestallon.storageexplorer.core.service.engine.StorageEngineError;

/**
 * The version file layout of an object, decided by its descriptor when the object is created.
 * An absent {@code strategy} field in the descriptor means {@link #CLASSIC}, matching the
 * platform's own default.
 */
public enum Strategy {
  /** One file per data version. */
  CLASSIC,
  /** {@code index_size} data versions share one {@code .iv} file. */
  INDEXED;

  /** The name as the descriptor writes it. */
  String asDescriptorString() {
    return (this == CLASSIC) ? "CLASSIC" : "INDEXED";
  }

  /** The strategy of a descriptor's {@code strategy} field; absent or null is {@link #CLASSIC}. */
  static EngineResult<Strategy> fromDescriptor(final Optional<String> value) {
    if (value.isEmpty() || "CLASSIC".equals(value.get())) {
      return new EngineResult.Ok<>(CLASSIC);
    }
    if ("INDEXED".equals(value.get())) {
      return new EngineResult.Ok<>(INDEXED);
    }
    return new EngineResult.Err<>(
        new StorageEngineError.Malformed("unknown storage strategy `" + value.get() + "`"));
  }
}

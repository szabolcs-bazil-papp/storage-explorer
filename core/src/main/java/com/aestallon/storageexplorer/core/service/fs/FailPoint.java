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

import com.aestallon.storageexplorer.core.service.engine.EngineResult;
import com.aestallon.storageexplorer.core.service.engine.StorageEngineError;

/**
 * Test hook: a write fails at the named step, to prove that a crash there leaves the object
 * consistent (see {@code FsStorageEngineWriteTest}'s crash-safety test).
 *
 * <p>Unlike the Rust library's {@code #[cfg(test)]}-gated version, this is always present rather
 * than compiled out of production builds — Java has no equivalent conditional compilation, and
 * the cost of a never-armed {@link ThreadLocal} check is negligible. {@link #arm}/{@link #disarm}
 * are only ever called from tests.
 */
final class FailPoint {

  private static final ThreadLocal<String> ARMED = new ThreadLocal<>();

  private FailPoint() {}

  static void arm(final String name) {
    ARMED.set(name);
  }

  static void disarm() {
    ARMED.remove();
  }

  static EngineResult<Void> check(final String name) {
    if (name.equals(ARMED.get())) {
      return new EngineResult.Err<>(new StorageEngineError.Unsupported("injected failure at " + name));
    }
    return new EngineResult.Ok<>(null);
  }
}

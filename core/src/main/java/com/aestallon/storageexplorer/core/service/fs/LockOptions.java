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

import java.time.Duration;

/**
 * @param force   take the lock even if the file names a foreign owner
 * @param timeout how long to wait before giving up: for the OS lock, and for the owner record of
 *                somebody else to go away (a platform node holds the logical lock only for the
 *                length of one save). Named {@code timeout}, not {@code wait} — the latter is
 *                reserved by {@code Object.wait()} and illegal as a record component name.
 */
public record LockOptions(boolean force, Duration timeout) {

  public static LockOptions defaults() {
    return new LockOptions(false, Duration.ofSeconds(10));
  }
}

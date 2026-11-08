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

import java.util.UUID;

/**
 * How writes behave.
 *
 * @param lock      the lock-acquisition policy
 * @param createdBy {@code createdBy} of the new versions
 * @param runtimeId the runtime id written to the lock file: one random id per
 *                  {@link FsStorageEngine}
 */
public record WriteOptions(LockOptions lock, String createdBy, String runtimeId) {

  /** The creator recorded in a new version when none is given (what the platform writes too). */
  public static final String DEFAULT_CREATED_BY = "N/A";

  public static WriteOptions defaults() {
    return new WriteOptions(LockOptions.defaults(), DEFAULT_CREATED_BY, UUID.randomUUID().toString());
  }
}

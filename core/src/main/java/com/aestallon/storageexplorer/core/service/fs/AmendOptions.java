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

/**
 * What an amend does besides replacing the payload.
 *
 * @param touchHead set the modification time of the {@code .o} to now. An amend does not touch
 *                  the descriptor, but platform caches that validate by the {@code .o} time would
 *                  never notice the changed content otherwise.
 */
public record AmendOptions(boolean touchHead) {

  public static AmendOptions defaults() {
    return new AmendOptions(true);
  }
}

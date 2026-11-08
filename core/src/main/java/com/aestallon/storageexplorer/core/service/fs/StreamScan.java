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

import java.util.List;

/**
 * The entries of an object stream and what is left of the file after them.
 *
 * @param entries    the decoded entries, in file order
 * @param end        the offset after the last entry that was read: the head position of the stream
 * @param fileLength the total length of the file
 */
public record StreamScan(List<StreamEntry> entries, long end, long fileLength) {

  /** Bytes of the file after the last entry: an unpublished write, or garbage. */
  public long trailingBytes() {
    return Math.max(0, fileLength - end);
  }
}

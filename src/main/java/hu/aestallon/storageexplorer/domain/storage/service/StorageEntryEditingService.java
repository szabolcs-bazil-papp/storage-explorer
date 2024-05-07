/*
 * Copyright (C) 2024 it4all Hungary Kft.
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

package hu.aestallon.storageexplorer.domain.storage.service;

import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import hu.aestallon.storageexplorer.domain.storage.model.StorageEntry;

@Service
public class StorageEntryEditingService {

  private static final Logger log = LoggerFactory.getLogger(StorageEntryEditingService.class);

  private final AtomicReference<StorageEntry> currentEntry = new AtomicReference<>();

  public boolean start(final StorageEntry storageEntry) {
    if (storageEntry == null) {
      return false;
    }

    synchronized (this) {
      final StorageEntry e = currentEntry.getAcquire();
      if (e != null) {
        return false;
      }

      currentEntry.set(storageEntry);
      return true;
    }
  }
}

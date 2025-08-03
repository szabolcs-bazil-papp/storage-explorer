/*
 * Copyright (C) 2025 Szabolcs Bazil Papp
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

package com.aestallon.storageexplorer.swing.ui.storagetree.model.node;

import com.aestallon.storageexplorer.client.userconfig.service.StorageEntryTrackingService;
import com.aestallon.storageexplorer.core.model.entry.SequenceEntry;
import com.aestallon.storageexplorer.core.model.entry.StorageEntry;

public final class StorageSequenceTreeNode extends ClickableTreeNode {

  private final transient StorageEntryTrackingService trackingService;

  public StorageSequenceTreeNode(SequenceEntry sequenceEntry,
                                 StorageEntryTrackingService trackingService) {
    super(sequenceEntry);
    this.trackingService = trackingService;
  }

  @Override
  public StorageEntry entity() {
    return (StorageEntry) userObject;
  }

  @Override
  public boolean getAllowsChildren() {
    return false;
  }

  @Override
  public boolean isLeaf() {
    return true;
  }

  @Override
  public String toString() {
    final var sequenceEntry = (SequenceEntry) userObject;
    return trackingService.getUserData(sequenceEntry)
        .map(StorageEntryTrackingService.StorageEntryUserData::name)
        .filter(it -> !it.isBlank())
        .orElseGet(sequenceEntry::displayName);
  }

}

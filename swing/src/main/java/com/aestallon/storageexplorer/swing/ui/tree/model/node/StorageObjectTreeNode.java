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

package com.aestallon.storageexplorer.swing.ui.tree.model.node;

import javax.swing.tree.DefaultMutableTreeNode;
import com.aestallon.storageexplorer.client.userconfig.service.StorageEntryTrackingService;
import com.aestallon.storageexplorer.core.model.entry.ObjectEntry;
import com.aestallon.storageexplorer.core.model.entry.ScopedListEntry;
import com.aestallon.storageexplorer.core.model.entry.ScopedMapEntry;
import com.aestallon.storageexplorer.core.model.entry.ScopedObjectEntry;
import com.aestallon.storageexplorer.core.model.entry.StorageEntry;

public final class StorageObjectTreeNode
    extends DefaultMutableTreeNode
    implements ClickableTreeNode {

  private boolean supportsChildren;

  private final transient StorageEntryTrackingService trackingService;

  public StorageObjectTreeNode(final ObjectEntry objectEntry,
                               final StorageEntryTrackingService trackingService) {
    super(objectEntry, true);
    this.trackingService = trackingService;

    objectEntry.scopedEntries().forEach(it -> add(switch (it) {
      case ScopedMapEntry m -> new StorageMapTreeNode(m, trackingService);
      case ScopedObjectEntry o -> new StorageObjectTreeNode(o, trackingService);
      case ScopedListEntry l -> new StorageListTreeNode(l, trackingService);
    }));
    supportsChildren = !objectEntry.scopedEntries().isEmpty();
  }

  @Override
  public boolean getAllowsChildren() {
    return supportsChildren;
  }

  @Override
  public StorageEntry storageEntry() {
    return (StorageEntry) userObject;
  }

  public void enableChildren(final boolean enable) {
    supportsChildren = enable;
  }

  @Override
  public String toString() {
    final var objectEntry = (ObjectEntry) userObject;
    return trackingService.getUserData(objectEntry)
        .map(StorageEntryTrackingService.StorageEntryUserData::name)
        .orElseGet(objectEntry::uuid);
  }

}

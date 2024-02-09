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

package hu.aestallon.storageexplorer.ui.tree.model;

import javax.swing.tree.DefaultMutableTreeNode;
import hu.aestallon.storageexplorer.domain.storage.model.ObjectEntry;
import hu.aestallon.storageexplorer.domain.storage.model.ScopedListEntry;
import hu.aestallon.storageexplorer.domain.storage.model.ScopedMapEntry;
import hu.aestallon.storageexplorer.domain.storage.model.ScopedObjectEntry;
import hu.aestallon.storageexplorer.domain.storage.model.StorageEntry;

public class StorageObjectTreeNode extends DefaultMutableTreeNode implements ClickableTreeNode {

  public StorageObjectTreeNode(ObjectEntry objectEntry) {
    super(objectEntry, true);

    objectEntry.scopedEntries().forEach(it -> {
      if (it instanceof ScopedMapEntry) {
        add(new StorageMapTreeNode((ScopedMapEntry) it));
      } else if (it instanceof ScopedListEntry) {
        add(new StorageListTreeNode((ScopedListEntry) it));
      } else if (it instanceof ScopedObjectEntry) {
        add(new StorageObjectTreeNode((ScopedObjectEntry) it));
      }
    });
  }

  @Override
  public StorageEntry storageEntry() {
    return (StorageEntry) userObject;
  }



  @Override
  public String toString() {
    return ((ObjectEntry) userObject).uuid();
  }

}

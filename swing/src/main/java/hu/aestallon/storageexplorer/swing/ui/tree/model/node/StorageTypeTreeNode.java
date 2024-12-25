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

package hu.aestallon.storageexplorer.swing.ui.tree.model.node;

import java.util.List;
import javax.swing.tree.DefaultMutableTreeNode;
import hu.aestallon.storageexplorer.storage.model.entry.ObjectEntry;

public final class StorageTypeTreeNode extends DefaultMutableTreeNode {

  public StorageTypeTreeNode(String name, List<ObjectEntry> objectEntries) {
    super(name, true);
    objectEntries.forEach(it -> add(new StorageObjectTreeNode(it)));
  }

}

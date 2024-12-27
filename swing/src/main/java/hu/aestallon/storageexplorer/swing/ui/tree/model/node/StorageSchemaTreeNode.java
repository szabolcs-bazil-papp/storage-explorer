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
import java.util.TreeMap;
import javax.swing.tree.DefaultMutableTreeNode;
import hu.aestallon.storageexplorer.core.model.entry.ObjectEntry;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

public final class StorageSchemaTreeNode extends DefaultMutableTreeNode {

  public StorageSchemaTreeNode(String name, List<ObjectEntry> objectEntries) {
    super(name, true);
    objectEntries.stream()
        .collect(groupingBy(ObjectEntry::typeName, TreeMap::new, toList()))
        .forEach((type, entries) -> add(new StorageTypeTreeNode(type, entries)));
  }

}

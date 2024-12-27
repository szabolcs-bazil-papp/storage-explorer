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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;
import javax.swing.tree.DefaultMutableTreeNode;
import hu.aestallon.storageexplorer.core.model.entry.ListEntry;
import hu.aestallon.storageexplorer.core.model.entry.MapEntry;
import hu.aestallon.storageexplorer.core.model.entry.ObjectEntry;
import hu.aestallon.storageexplorer.core.model.entry.ScopedEntry;
import hu.aestallon.storageexplorer.core.model.entry.SequenceEntry;
import hu.aestallon.storageexplorer.core.model.instance.StorageInstance;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

public final class StorageInstanceTreeNode extends DefaultMutableTreeNode {

  private final StorageInstance storageInstance;

  public StorageInstanceTreeNode(StorageInstance storageInstance) {
    super(storageInstance.name(), true);
    this.storageInstance = storageInstance;

    final var collections = storageInstance.entities()
        .filter(it -> !(it instanceof ScopedEntry))
        .filter(it -> it instanceof ListEntry
            || it instanceof MapEntry
            || it instanceof SequenceEntry)
        .map(it -> it instanceof ListEntry list
            ? new StorageListTreeNode(list)
            : it instanceof MapEntry map
                ? new StorageMapTreeNode(map)
                : new StorageSequenceTreeNode((SequenceEntry) it))
        .sorted((a, b) -> (a instanceof StorageListTreeNode)
            ? -1
            : (b instanceof StorageListTreeNode)
                ? 1
                : 0)
        .collect(groupingBy(Object::getClass));
    sortAndAdd(collections.getOrDefault(StorageListTreeNode.class, new ArrayList<>()));
    sortAndAdd(collections.getOrDefault(StorageMapTreeNode.class, new ArrayList<>()));
    sortAndAdd(collections.getOrDefault(StorageSequenceTreeNode.class, new ArrayList<>()));

    storageInstance.entities()
        .filter(ObjectEntry.class::isInstance)
        .filter(it -> !(it instanceof ScopedEntry))
        .map(ObjectEntry.class::cast)
        .collect(groupingBy(it -> it.uri().getScheme(), TreeMap::new, toList()))
        .forEach((schema, entries) -> add(new StorageSchemaTreeNode(schema, entries)));
  }

  private void sortAndAdd(List<? extends DefaultMutableTreeNode> collections) {
    collections.sort(Comparator.comparing(DefaultMutableTreeNode::toString));
    collections.forEach(this::add);
  }

  public StorageInstance storageInstance() {
    return storageInstance;
  }

}

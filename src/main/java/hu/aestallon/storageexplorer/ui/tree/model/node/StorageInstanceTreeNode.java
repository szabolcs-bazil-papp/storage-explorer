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

package hu.aestallon.storageexplorer.ui.tree.model.node;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;
import javax.swing.tree.DefaultMutableTreeNode;
import hu.aestallon.storageexplorer.domain.storage.model.ListEntry;
import hu.aestallon.storageexplorer.domain.storage.model.MapEntry;
import hu.aestallon.storageexplorer.domain.storage.model.ObjectEntry;
import hu.aestallon.storageexplorer.domain.storage.model.ScopedEntry;
import hu.aestallon.storageexplorer.domain.storage.model.SequenceEntry;
import hu.aestallon.storageexplorer.domain.storage.service.StorageIndex;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

public class StorageInstanceTreeNode extends DefaultMutableTreeNode {

  private final Path storagePath;

  public StorageInstanceTreeNode(StorageIndex index) {
    super(index.name(), true);
    storagePath = index.pathToStorage();

    final var collections = index.entities()
        .filter(it -> !(it instanceof ScopedEntry))
        .filter(it -> it instanceof ListEntry 
            || it instanceof MapEntry 
            || it instanceof SequenceEntry)
        .map(it -> it instanceof ListEntry
            ? new StorageListTreeNode((ListEntry) it)
            : it instanceof MapEntry 
                ? new StorageMapTreeNode((MapEntry) it)
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

    index.entities()
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

  public Path storagePath() {
    return storagePath;
  }

}

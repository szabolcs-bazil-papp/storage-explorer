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

package hu.aestallon.storageexplorer.model.tree;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.swing.tree.DefaultMutableTreeNode;
import hu.aestallon.storageexplorer.domain.storage.model.ListEntry;
import hu.aestallon.storageexplorer.domain.storage.model.MapEntry;
import hu.aestallon.storageexplorer.domain.storage.model.ObjectEntry;
import hu.aestallon.storageexplorer.domain.storage.service.StorageIndex;
import static java.util.stream.Collectors.groupingBy;

public class StorageInstance extends DefaultMutableTreeNode {

  public StorageInstance(StorageIndex index) {
    super(index.pathToStorage().getFileName().toString(), true);
    final var collections = index.entities()
        .filter(it -> it instanceof ListEntry || it instanceof MapEntry)
        .map(it -> it instanceof ListEntry
            ? new StorageList((ListEntry) it)
            : new StorageMap((MapEntry) it))
        .sorted((a, b) -> (a instanceof StorageList)
            ? -1
            : (b instanceof StorageList)
                ? 1
                : 0)
        .collect(groupingBy(Object::getClass));
    sortAndAdd(collections.getOrDefault(StorageList.class, new ArrayList<>()));
    sortAndAdd(collections.getOrDefault(StorageMap.class, new ArrayList<>()));

    index.entities()
        .filter(ObjectEntry.class::isInstance)
        .map(ObjectEntry.class::cast)
        .collect(groupingBy(it -> it.uri().getScheme()))
        .forEach((schema, entries) -> add(new StorageSchema(schema, entries)));
  }

  private void sortAndAdd(List<? extends DefaultMutableTreeNode> collections) {
    collections.sort(Comparator.comparing(DefaultMutableTreeNode::toString));
    collections.forEach(this::add);
  }

}

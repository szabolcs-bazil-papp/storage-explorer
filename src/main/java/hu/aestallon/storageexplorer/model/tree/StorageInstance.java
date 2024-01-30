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

import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;
import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.TreeNode;
import hu.aestallon.storageexplorer.service.internal.ListEntry;
import hu.aestallon.storageexplorer.service.internal.MapEntry;
import hu.aestallon.storageexplorer.service.internal.ObjectEntry;
import hu.aestallon.storageexplorer.service.internal.StorageEntry;
import hu.aestallon.storageexplorer.service.internal.StorageIndex;
import static java.util.stream.Collectors.groupingBy;

public class StorageInstance implements WrappableToMutable {

  private final Path path;

  private final List<WrappableToMutable> children;
  private final MutableTreeNode root;

  public StorageInstance(Path path, StorageIndex index, MutableTreeNode root) {
    this.path = path;
    this.root = root;
    this.children = index.entities()
        .filter(it -> it instanceof ListEntry || it instanceof MapEntry)
        .map(it -> it instanceof ListEntry
            ? new StorageList((ListEntry) it, this)
            : new StorageMap((MapEntry) it, this))
        .sorted((a, b) -> (a instanceof StorageList)
            ? -1
            : (b instanceof StorageList)
                ? 1
                : 0)
        .collect(Collectors.toCollection(ArrayList::new));
    children.addAll(index.entities()
        .filter(ObjectEntry.class::isInstance)
        .map(StorageEntry::uri)
        .collect(groupingBy(URI::getScheme))
        .entrySet().stream()
        .map(e -> new StorageSchema(e.getKey(), new HashSet<>(e.getValue()), this))
        .collect(Collectors.toList()));
  }

  @Override
  public TreeNode getChildAt(int childIndex) {
    return children.get(childIndex);
  }

  @Override
  public int getChildCount() {
    return children.size();
  }

  @Override
  public TreeNode getParent() {
    return root;
  }

  @Override
  public int getIndex(TreeNode node) {
    if (!(node instanceof StorageSchema)) {
      return -1;
    }

    return children.indexOf(node);
  }

  @Override
  public boolean getAllowsChildren() {
    return true;
  }

  @Override
  public boolean isLeaf() {
    return getChildCount() == 0;
  }

  @Override
  public Enumeration<? extends TreeNode> children() {
    final var iterator = children.iterator();
    return new Enumeration<>() {
      @Override
      public boolean hasMoreElements() {
        return iterator.hasNext();
      }

      @Override
      public TreeNode nextElement() {
        return iterator.next();
      }
    };
  }

  @Override
  public String toString() {
    return path.getFileName().toString();
  }

}

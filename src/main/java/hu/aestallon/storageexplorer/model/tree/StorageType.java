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
import java.util.Enumeration;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.swing.tree.TreeNode;

public class StorageType implements WrappableToMutable {

  private final String name;
  private final StorageSchema parent;

  private final List<StorageObject> children;

  public StorageType(String name, StorageSchema parent, Set<URI> uris) {
    this.name = name;
    this.parent = parent;
    this.children = uris.stream()
        .map(it -> {
          final var pathElements = it.getPath().split("/");
          final var uuid = pathElements[pathElements.length - 1];
          return new StorageObject(uuid, this, it);
        })
        .collect(Collectors.toList());
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
    return parent;
  }

  @Override
  public int getIndex(TreeNode node) {
    if (!(node instanceof StorageObject)) {
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
    return children.isEmpty();
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
    return name;
  }

}

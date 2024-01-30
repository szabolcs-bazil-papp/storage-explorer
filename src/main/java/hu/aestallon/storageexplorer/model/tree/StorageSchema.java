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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.swing.tree.TreeNode;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

public final class StorageSchema implements WrappableToMutable {

  private final StorageInstance parent;
  private final String name;
  private final List<StorageType> children;

  StorageSchema(String name, Set<URI> uris, StorageInstance parent) {
    this.name = name;
    this.parent = parent;
    this.children = uris.stream()
        .collect(groupingBy(it -> {
          final String[] typeElements = it.getPath().split("/")[1].split("_");
          return typeElements[typeElements.length - 1];
        }))
        .entrySet().stream()
        .map(e -> new StorageType(e.getKey(), this, new HashSet<>(e.getValue())))
        .collect(toList());
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
    if (!(node instanceof StorageType)) {
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

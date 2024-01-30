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
import javax.swing.tree.TreeNode;

public class StorageObject implements Clickable, WrappableToMutable {

  private final String name;
  private final StorageType parent;
  private final URI uri;

  public StorageObject(String name, StorageType parent, URI uri) {
    this.name = name;
    this.parent = parent;
    this.uri = uri;
  }

  public URI uri() {
    return uri;
  }

  @Override
  public TreeNode getChildAt(int childIndex) {
    return null;
  }

  @Override
  public int getChildCount() {
    return 0;
  }

  @Override
  public TreeNode getParent() {
    return parent;
  }

  @Override
  public int getIndex(TreeNode node) {
    return -1;
  }

  @Override
  public boolean getAllowsChildren() {
    return false;
  }

  @Override
  public boolean isLeaf() {
    return true;
  }

  @Override
  public Enumeration<? extends TreeNode> children() {
    return null;
  }

  @Override
  public String toString() {
    return name;
  }

}

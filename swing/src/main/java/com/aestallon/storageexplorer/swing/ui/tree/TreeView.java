/*
 * Copyright (C) 2025 Szabolcs Bazil Papp
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

package com.aestallon.storageexplorer.swing.ui.tree;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import com.aestallon.storageexplorer.core.model.instance.StorageInstance;

/**
 * Common interface for all "static" lifecycle GUI trees.
 *
 * @param <ENTITY> the entity represented by interactive nodes
 * @param <USER_DATA_CHANGE_EVENT> event to be fired when one or more represented node data
 *     changes
 */
public interface TreeView<ENTITY, USER_DATA_CHANGE_EVENT> {

  String name();
  
  ImageIcon icon();
  
  String tooltip();
  
  void incorporateNode(final ENTITY entity);

  void importStorage(final StorageInstance storageInstance);

  void reindexStorage(final StorageInstance storageInstance);

  void selectNode(final ENTITY entity);

  void selectNodeSoft(final ENTITY entity);
  
  void selectNodeSoft(DefaultMutableTreeNode node);

  void removeStorage(final StorageInstance storageInstance);

  void onUserDataChanged(final USER_DATA_CHANGE_EVENT userDataChangeEvent);

  void requestVisibility();
  
  JComponent asComponent();

}

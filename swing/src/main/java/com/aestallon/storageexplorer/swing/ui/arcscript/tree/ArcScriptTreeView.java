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

package com.aestallon.storageexplorer.swing.ui.arcscript.tree;

import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import com.aestallon.storageexplorer.client.storage.StorageInstanceProvider;
import com.aestallon.storageexplorer.client.userconfig.service.UserConfigService;
import com.aestallon.storageexplorer.common.util.Pair;
import com.aestallon.storageexplorer.core.model.instance.StorageInstance;
import com.aestallon.storageexplorer.swing.ui.controller.SideBarController;
import com.aestallon.storageexplorer.swing.ui.event.ArcScriptViewRenamed;
import com.aestallon.storageexplorer.swing.ui.misc.IconProvider;
import com.aestallon.storageexplorer.swing.ui.tree.AbstractTreeView;
import com.aestallon.storageexplorer.swing.ui.tree.TreeView;

@Service
public class ArcScriptTreeView
    extends AbstractTreeView
    <
        ArcScriptSelectorTree,
        ArcScriptSelectorTree.ArcScriptNodeLocator,
        ArcScriptViewRenamed,
        ArcScriptSelectorTree.StorageNode,
        ArcScriptSelectorTree.ScriptNode,
        ArcScriptTreeView>
    implements TreeView<ArcScriptSelectorTree.ArcScriptNodeLocator, ArcScriptViewRenamed> {

  protected ArcScriptTreeView(ApplicationEventPublisher eventPublisher,
                              StorageInstanceProvider storageInstanceProvider,
                              UserConfigService userConfigService,
                              SideBarController sideBarController) {
    super(
        eventPublisher, storageInstanceProvider, userConfigService, sideBarController,
        self -> {});
  }

  @Override
  public String name() {
    return "ArcScript Tree";
  }

  @Override
  public ImageIcon icon() {
    return IconProvider.ARC_SCRIPT;
  }

  @Override
  public String tooltip() {
    return "Show/hide scripts";
  }

  @Override
  protected ArcScriptSelectorTree initTree() {
    return ArcScriptSelectorTree.create();
  }

  @Override
  protected Class<? extends ArcScriptSelectorTree.ScriptNode> entityNodeType() {
    return ArcScriptSelectorTree.ScriptNode.class;
  }

  @Override
  public void incorporateNode(ArcScriptSelectorTree.ArcScriptNodeLocator arcScriptNodeLocator) {

  }

  @Override
  public void importStorage(StorageInstance storageInstance) {
    final List<String> loadableScripts = userConfigService
        .arcScriptFileService()
        .checkAllAvailable()
        .getOrDefault(storageInstance.id(), new ArrayList<>());
    tree.addStorage(storageInstance, loadableScripts);
    memoizeTreePaths();
  }
  
  private void memoizeTreePaths() {
    treePathsByLeaf.putAll(tree.treePaths().collect(Pair.toMap()));
  }

  @Override
  public void reindexStorage(StorageInstance storageInstance) {
    // NO-OP
  }

  @Override
  public void removeStorage(StorageInstance storageInstance) {
    tree.removeStorage(storageInstance.id());
  }

  @Override
  public void onUserDataChanged(ArcScriptViewRenamed arcScriptViewRenamed) {
    super.onUserDataChanged(arcScriptViewRenamed);
  }
  
  public void expandAll() {
    tree.expandAll();
  }

}

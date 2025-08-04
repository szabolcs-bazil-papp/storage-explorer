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

import java.awt.*;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;
import javax.accessibility.Accessible;
import javax.swing.*;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.DefaultTreeSelectionModel;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.aestallon.storageexplorer.common.util.Pair;
import com.aestallon.storageexplorer.common.util.Streams;
import com.aestallon.storageexplorer.core.model.instance.StorageInstance;
import com.aestallon.storageexplorer.core.model.instance.dto.StorageId;
import com.aestallon.storageexplorer.swing.ui.misc.IconProvider;
import com.aestallon.storageexplorer.swing.ui.tree.AbstractTreeView;

public final class ArcScriptSelectorTree extends JTree implements Scrollable, Accessible {

  private static final Logger log = LoggerFactory.getLogger(ArcScriptSelectorTree.class);


  public record ArcScriptNodeLocator(StorageId storageId, String path) {}

  public static ArcScriptSelectorTree create() {
    final var root = new DefaultMutableTreeNode("ArcScript Selector");
    root.setAllowsChildren(true);
    final var tree = new ArcScriptSelectorTree(root);

    final var selectionModel = new DefaultTreeSelectionModel();
    selectionModel.setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
    tree.setSelectionModel(selectionModel);

    tree.setCellRenderer(new Renderer());
    tree.setRootVisible(false);
    tree.setShowsRootHandles(true);
    return tree;
  }

  private SelectionChangeListener selectionChangeListener;
  private TreeSelectionListener treeSelectionListener;

  private ArcScriptSelectorTree(final TreeNode root) {
    super(root, true);
  }

  // -----------------------------------------------------------------------------------------------
  // Public API

  public void onSelectionChanged(SelectionChangeListener f) {
    if (treeSelectionListener != null) {
      removeTreeSelectionListener(treeSelectionListener);
    }

    selectionChangeListener = f;

    treeSelectionListener = e -> {
      TreePath p = e.getNewLeadSelectionPath();
      if (p == null) {
        selectionChangeListener.accept(new Selection.None());
      } else {
        final Object[] es = p.getPath();
        if (es.length < 2) {
          selectionChangeListener.accept(new Selection.None());
        } else if (es[es.length - 1] instanceof StorageNode storageNode) {
          selectionChangeListener.accept(new Selection.Storage(storageNode.storageInstance.id()));
        } else if (es[es.length - 1] instanceof ScriptNode scriptNode) {
          final var storageNode = (StorageNode) es[es.length - 2];
          selectionChangeListener.accept(new Selection.ScriptFile(
              storageNode.storageInstance.id(),
              scriptNode.getUserObject().toString()));
        }
      }
    };
    addTreeSelectionListener(treeSelectionListener);

  }

  public void addStorage(StorageInstance storageInstance, List<String> loadableScripts) {
    final var root = root();
    model().insertNodeInto(
        new StorageNode(storageInstance, loadableScripts),
        root,
        root.getChildCount());
  }

  public void removeStorage(StorageId storageId) {
    storageNodes().stream()
        .filter(matchesId(storageId))
        .findFirst()
        .ifPresent(it -> model().removeNodeFromParent(it));
  }

  public void addScript(StorageId storageId, String loadableScript) {
    storageNodes().stream()
        .filter(matchesId(storageId))
        .findFirst()
        .ifPresent(it -> model().insertNodeInto(
            new ScriptNode(new ArcScriptNodeLocator(storageId, loadableScript)),
            it,
            it.getChildCount()));
  }

  public void removeScript(StorageId storageId, String loadableScript) {
    scriptNodes(storageId).stream()
        .filter(matchesTitle(loadableScript))
        .findFirst()
        .ifPresent(it -> model().removeNodeFromParent(it));
  }

  public void storageRenamed(final StorageId storageId) {
    storageNodes().stream()
        .filter(matchesId(storageId))
        .findFirst()
        .ifPresent(it -> {
          it.setUserObject(it.storageInstance.name());
          model().nodeChanged(it);
        });
  }

  public void scriptRenamed(final StorageId storageId, final String oldName, final String newName) {
    scriptNodes(storageId).stream()
        .filter(matchesTitle(oldName))
        .findFirst()
        .ifPresent(it -> {
          it.setUserObject(newName);
          model().nodeChanged(it);
        });
  }

  public boolean isEmpty() {
    return storageNodes().isEmpty();
  }

  Stream<Pair<ArcScriptNodeLocator, TreePath>> treePaths() {
    return storageNodes().stream()
        .flatMap(it -> Streams.enumerationToStream(it.children()))
        .map(ScriptNode.class::cast)
        .map(it -> Pair.of(it.entity(), new TreePath(it.getPath())));
  }

  // -----------------------------------------------------------------------------------------------
  // internal

  private DefaultMutableTreeNode root() {
    return (DefaultMutableTreeNode) getModel().getRoot();
  }

  private DefaultTreeModel model() {
    return (DefaultTreeModel) getModel();
  }

  private List<StorageNode> storageNodes() {
    return Streams.enumerationToStream(root().children()).map(StorageNode.class::cast).toList();
  }

  private List<ScriptNode> scriptNodes(final StorageId storageId) {
    return storageNodes().stream()
        .filter(matchesId(storageId))
        .findFirst().stream()
        .flatMap(it -> Streams.enumerationToStream(it.children()))
        .map(ScriptNode.class::cast)
        .toList();
  }

  private Predicate<StorageNode> matchesId(final StorageId storageId) {
    return it -> storageId.equals(it.storageInstance.id());
  }

  private Predicate<ScriptNode> matchesTitle(final String title) {
    return it -> title.equals(it.getUserObject());
  }

  // -----------------------------------------------------------------------------------------------
  // nodes


  public static final class StorageNode
      extends DefaultMutableTreeNode
      implements AbstractTreeView.StorageInstanceNode {

    private final transient StorageInstance storageInstance;

    private StorageNode(final StorageInstance storageInstance, final List<String> loadableScripts) {
      super(storageInstance.name(), true);
      this.storageInstance = storageInstance;

      loadableScripts.stream()
          .map(it -> new ArcScriptNodeLocator(storageInstance.id(), it))
          .map(ScriptNode::new)
          .forEach(this::add);
    }

    @Override
    public StorageId storageId() {
      return storageInstance.id();
    }

  }


  public static final class ScriptNode
      extends DefaultMutableTreeNode
      implements AbstractTreeView.EntityNode<ArcScriptNodeLocator> {


    private ScriptNode(final ArcScriptNodeLocator locator) {
      super(locator, false);
    }

    @Override
    public ArcScriptNodeLocator entity() {
      return (ArcScriptNodeLocator) getUserObject();
    }

    @Override
    public String toString() {
      return entity().path();
    }

  }

  // ----------------------------------------------------------------------------------------------
  // Selection stuff...


  @FunctionalInterface
  public interface SelectionChangeListener extends Consumer<Selection> {}


  public sealed interface Selection {

    record None() implements Selection {}


    sealed interface HasStorage extends Selection {
      StorageId storageId();
    }


    record Storage(StorageId storageId) implements HasStorage {}


    record ScriptFile(StorageId storageId, String title) implements HasStorage {}

  }

  // ----------------------------------------------------------------------------------------------
  // Cell renderer


  private static final class Renderer extends DefaultTreeCellRenderer implements TreeCellRenderer {
    @Override
    public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel,
                                                  boolean expanded, boolean leaf, int row,
                                                  boolean hasFocus) {
      super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
      switch (value) {
        case StorageNode storageNode ->
            setIcon(IconProvider.getIconForStorageInstance(storageNode.storageInstance));
        case ScriptNode scriptNode -> setIcon(IconProvider.ARC_SCRIPT);
        default -> {
        }
      }

      return this;
    }
  }

}

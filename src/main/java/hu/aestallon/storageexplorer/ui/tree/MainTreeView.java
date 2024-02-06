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

package hu.aestallon.storageexplorer.ui.tree;

import java.awt.*;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.DefaultTreeSelectionModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import hu.aestallon.storageexplorer.domain.storage.model.StorageEntry;
import hu.aestallon.storageexplorer.domain.storage.service.StorageIndex;
import hu.aestallon.storageexplorer.domain.storage.service.StorageIndexProvider;
import hu.aestallon.storageexplorer.ui.GraphView;
import hu.aestallon.storageexplorer.ui.misc.IconProvider;
import hu.aestallon.storageexplorer.ui.tree.model.Clickable;
import hu.aestallon.storageexplorer.ui.tree.model.StorageInstance;
import hu.aestallon.storageexplorer.ui.tree.model.StorageList;
import hu.aestallon.storageexplorer.ui.tree.model.StorageMap;
import hu.aestallon.storageexplorer.ui.tree.model.StorageObject;
import hu.aestallon.storageexplorer.util.Pair;

@Component
public class MainTreeView extends JPanel {

  private static final Logger log = LoggerFactory.getLogger(MainTreeView.class);
  private JTree tree;
  private JScrollPane treePanel;

  private Map<StorageEntry, TreePath> treePathByEntry;

  private final AtomicBoolean propagate = new AtomicBoolean(true);
  private final ApplicationEventPublisher eventPublisher;
  private final StorageIndexProvider storageIndexProvider;

  public MainTreeView(StorageIndexProvider storageIndexProvider, GraphView graphView,
                      ApplicationEventPublisher eventPublisher) {
    super(new GridLayout(1, 1));

    this.storageIndexProvider = storageIndexProvider;
    this.eventPublisher = eventPublisher;

    setPreferredSize(new Dimension(300, 500));

    initTree();
    treePanel = new JScrollPane(tree);
    add(treePanel);
  }

  private void initTree() {
    final var root = new DefaultMutableTreeNode("Storage Explorer");
    tree = new JTree(root, true);

    final var selectionModel = new DefaultTreeSelectionModel();
    selectionModel.setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
    tree.setSelectionModel(selectionModel);

    tree.setCellRenderer(new TreeNodeRenderer());
    tree.addTreeSelectionListener(e -> {
      final var treeNode = (DefaultMutableTreeNode) e.getPath().getLastPathComponent();
      log.info("TREE SELECTION [ {} ]", treeNode);
      if (treeNode instanceof Clickable && propagate.get()) {
        final Clickable clickable = (Clickable) treeNode;
        eventPublisher.publishEvent(clickable);
      }
    });

    treePathByEntry = new HashMap<>();
  }

  public void importStorage(final StorageIndex storageIndex) {
    DefaultTreeModel model = (DefaultTreeModel) tree.getModel();
    final DefaultMutableTreeNode root = (DefaultMutableTreeNode) model.getRoot();
    final StorageInstance storageInstanceTreeNode = new StorageInstance(storageIndex);
    model.insertNodeInto(storageInstanceTreeNode, root, root.getChildCount());

    final var newTreePaths = Stream.of(storageInstanceTreeNode)
        .flatMap(MainTreeView::flatten)
        .filter(Clickable.class::isInstance)

        .map(it -> Pair.of(
            ((Clickable) it).storageEntry(),
            new TreePath(it.getPath())))
        .collect(Pair.toMap());
    treePathByEntry.putAll(newTreePaths);
  }

  private static <E> Stream<E> enumerationToStream(Enumeration<E> e) {
    final Iterable<E> iterable = e::asIterator;
    return StreamSupport.stream(iterable.spliterator(), false);
  }

  private static Stream<DefaultMutableTreeNode> flatten(DefaultMutableTreeNode node) {
    return Stream.concat(
        Stream.of(node),
        enumerationToStream(node.children())
            .filter(DefaultMutableTreeNode.class::isInstance)
            .map(DefaultMutableTreeNode.class::cast)
            .flatMap(MainTreeView::flatten));
  }


  public void selectEntry(StorageEntry storageEntry) {
    Optional
        .ofNullable(treePathByEntry.get(storageEntry))
        .ifPresent(path -> {
          tree.setSelectionPath(path);
          tree.scrollPathToVisible(path);
        });
  }

  public void softSelectEntry(final StorageEntry storageEntry) {
    propagate.set(false);  // FIXME: This is a freaking hack!
    selectEntry(storageEntry);
    propagate.set(true);
  }

  private static final class TreeNodeRenderer extends DefaultTreeCellRenderer {

    @Override
    public java.awt.Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel,
                                                           boolean expanded, boolean leaf, int row,
                                                           boolean hasFocus) {
      super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
      if (value instanceof StorageList) {
        setIcon(IconProvider.LIST);
      } else if (value instanceof StorageMap) {
        setIcon(IconProvider.MAP);
      } else if (value instanceof StorageObject) {
        setIcon(IconProvider.OBJ);
      }
      return this;
    }
  }

}

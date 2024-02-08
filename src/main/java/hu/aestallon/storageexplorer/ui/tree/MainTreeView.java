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
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.DefaultTreeSelectionModel;
import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import hu.aestallon.storageexplorer.domain.storage.model.StorageEntry;
import hu.aestallon.storageexplorer.domain.storage.service.StorageIndex;
import hu.aestallon.storageexplorer.domain.storage.service.StorageIndexProvider;
import hu.aestallon.storageexplorer.ui.misc.IconProvider;
import hu.aestallon.storageexplorer.ui.tree.model.ClickableTreeNode;
import hu.aestallon.storageexplorer.ui.tree.model.StorageInstanceTreeNode;
import hu.aestallon.storageexplorer.ui.tree.model.StorageListTreeNode;
import hu.aestallon.storageexplorer.ui.tree.model.StorageMapTreeNode;
import hu.aestallon.storageexplorer.ui.tree.model.StorageObjectTreeNode;
import hu.aestallon.storageexplorer.util.Pair;

@Component
public class MainTreeView extends JPanel {

  private static final Logger log = LoggerFactory.getLogger(MainTreeView.class);
  private JTree tree;
  private JScrollPane treePanel;
  private JProgressBar progressBar;

  private Map<StorageEntry, TreePath> treePathByEntry;

  private final AtomicBoolean propagate = new AtomicBoolean(true);
  private final ApplicationEventPublisher eventPublisher;
  private final StorageIndexProvider storageIndexProvider;

  public MainTreeView(ApplicationEventPublisher eventPublisher,
                      StorageIndexProvider storageIndexProvider) {
    this.eventPublisher = eventPublisher;
    this.storageIndexProvider = storageIndexProvider;

    setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
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
      if (treeNode instanceof ClickableTreeNode && propagate.get()) {
        eventPublisher.publishEvent(treeNode);
      }
    });
    tree.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        super.mouseClicked(e);
        if (SwingUtilities.isRightMouseButton(e)) {
          final var treePath = tree.getClosestPathForLocation(e.getX(), e.getY());
          if (treePath == null) {
            return;
          }
          final Object lastPathComponent = treePath.getLastPathComponent();
          if (!(lastPathComponent instanceof StorageInstanceTreeNode)) {
            return;
          }
          tree.setSelectionPath(treePath);
          final var storageInstanceTreeNode = (StorageInstanceTreeNode) lastPathComponent;
          final var popup = new StorageIndexNodePopupMenu(storageInstanceTreeNode);
          popup.show(e.getComponent(), e.getX(), e.getY());
        }
      }
    });

    treePathByEntry = new HashMap<>();
  }

  public void importStorage(final StorageIndex storageIndex) {
    DefaultTreeModel model = (DefaultTreeModel) tree.getModel();
    final DefaultMutableTreeNode root = (DefaultMutableTreeNode) model.getRoot();
    final StorageInstanceTreeNode storageInstanceTreeNode =
        new StorageInstanceTreeNode(storageIndex);
    model.insertNodeInto(storageInstanceTreeNode, root, root.getChildCount());

    memoizeTreePathsOf(storageInstanceTreeNode);
  }

  private void memoizeTreePathsOf(StorageInstanceTreeNode storageInstanceTreeNode) {
    final var newTreePaths = Stream.of(storageInstanceTreeNode)
        .flatMap(MainTreeView::flatten)
        .filter(ClickableTreeNode.class::isInstance)

        .map(it -> Pair.of(
            ((ClickableTreeNode) it).storageEntry(),
            new TreePath(it.getPath())))
        .collect(Pair.toMap());
    treePathByEntry.putAll(newTreePaths);
  }

  public void reindexStorage(final StorageIndex storageIndex) {
    DefaultTreeModel model = (DefaultTreeModel) tree.getModel();
    final DefaultMutableTreeNode root = (DefaultMutableTreeNode) model.getRoot();
    final int idx = indexOfStorageIndex(storageIndex, root);
    if (idx < 0) {
      return;
    }
    // Drop Storage:
    model.removeNodeFromParent((MutableTreeNode) root.getChildAt(idx));

    // Re-add Storage:
    final var storageInstanceTreeNode = new StorageInstanceTreeNode(storageIndex);
    model.insertNodeInto(storageInstanceTreeNode, root, idx);
    memoizeTreePathsOf(storageInstanceTreeNode);
  }

  private static int indexOfStorageIndex(StorageIndex storageIndex, DefaultMutableTreeNode root) {
    for (int i = 0; i < root.getChildCount(); i++) {
      final var storageInstanceTreeNode = (StorageInstanceTreeNode) root.getChildAt(i);
      if (Objects.equals(storageInstanceTreeNode.storagePath(), storageIndex.pathToStorage())) {
        return i;
      }
    }
    return -1;
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
      if (value instanceof StorageListTreeNode) {
        setIcon(IconProvider.LIST);
      } else if (value instanceof StorageMapTreeNode) {
        setIcon(IconProvider.MAP);
      } else if (value instanceof StorageObjectTreeNode) {
        setIcon(IconProvider.OBJ);
      } else if (value instanceof StorageInstanceTreeNode) {
        setIcon(IconProvider.DB);
      }
      return this;
    }
  }

  public void showProgressBar(final String displayName) {
    if (progressBar == null) {
      progressBar = new JProgressBar(JProgressBar.HORIZONTAL);
      progressBar.setPreferredSize(new Dimension(300, 20));
    }
    progressBar.setString(displayName);
    progressBar.setStringPainted(true);
    progressBar.setIndeterminate(true);
    add(progressBar);
    revalidate();
  }

  public void removeProgressBar() {
    if (progressBar == null) {
      return;
    }
    remove(progressBar);
    revalidate();
  }

  private final class StorageIndexNodePopupMenu extends JPopupMenu {
    private StorageIndexNodePopupMenu(StorageInstanceTreeNode sitn) {
      super(String.valueOf(sitn.getUserObject()));

      final var reindex = new JMenuItem("Reload", IconProvider.REFRESH);
      reindex.addActionListener(e -> {
        CompletableFuture.runAsync(() -> storageIndexProvider.reindex(sitn.storagePath()));
      });
      add(reindex);
    }
  }

}

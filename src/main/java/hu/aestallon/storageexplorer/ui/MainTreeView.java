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

package hu.aestallon.storageexplorer.ui;

import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import hu.aestallon.storageexplorer.model.tree.Clickable;
import hu.aestallon.storageexplorer.model.tree.StorageInstance;
import hu.aestallon.storageexplorer.model.tree.StorageList;
import hu.aestallon.storageexplorer.model.tree.StorageMap;
import hu.aestallon.storageexplorer.model.tree.StorageObject;
import hu.aestallon.storageexplorer.service.internal.StorageIndex;

@Component
public class MainTreeView extends JPanel {

  private static final Logger log = LoggerFactory.getLogger(MainTreeView.class);
  private JTree tree;
  private JScrollPane treePanel;
  private final StorageIndex storageIndex;

  public MainTreeView(StorageIndex storageIndex, GraphView graphView) {
    super(new GridLayout(1, 1));
    this.storageIndex = storageIndex;

    setPreferredSize(new Dimension(300, 500));

    tree = initTree();
    tree.setCellRenderer(new TreeNodeRenderer());
    tree.addTreeSelectionListener(e -> {
      final var treeNode = (DefaultMutableTreeNode) e.getPath().getLastPathComponent();
      log.info("TREE SELECTION [ {} ]", treeNode);
      if (treeNode == null) {
        return;
      }
      Object userObject = treeNode.getUserObject();
      log.info("TREE SELECTION - User Object [ {} ]", userObject);
      if (userObject instanceof Clickable) {
        graphView.initOnUri(((Clickable) userObject).uri());
      }
    });
    treePanel = new JScrollPane(tree);
    add(treePanel);
  }

  private JTree initTree() {
    final var root = new DefaultMutableTreeNode("Storage Explorer");
    root.add(new StorageInstance(
        storageIndex.fsBaseDirectory(),
        storageIndex,
        root).wrap());

    return new JTree(root, true);
  }

  private static final class TreeNodeRenderer extends DefaultTreeCellRenderer {


    private static byte[] foo(String loc) {
      InputStream resourceAsStream = TreeNodeRenderer.class.getResourceAsStream(loc);

      try {
        return StreamUtils.copyToByteArray(resourceAsStream);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
    private static final ImageIcon LIST =
        new ImageIcon(foo("/icons/list.png"));
    private static final ImageIcon MAP =
        new ImageIcon(foo("/icons/map.png"));
    private static final ImageIcon OBJ =
        new ImageIcon(foo("/icons/object.png"));

    @Override
    public java.awt.Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel,
                                                           boolean expanded, boolean leaf, int row,
                                                           boolean hasFocus) {
      super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
      if (value instanceof DefaultMutableTreeNode) {
        Object userObject = ((DefaultMutableTreeNode) value).getUserObject();
        if (userObject instanceof StorageList) {
          setIcon(LIST);
        } else if (userObject instanceof StorageMap) {
          setIcon(MAP);
        } else if (userObject instanceof StorageObject) {
          setIcon(OBJ);
        }
      }
      return this;
    }
  }
}

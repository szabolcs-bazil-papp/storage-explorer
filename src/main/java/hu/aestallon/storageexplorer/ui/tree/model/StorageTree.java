package hu.aestallon.storageexplorer.ui.tree.model;

import java.util.Objects;
import javax.accessibility.Accessible;
import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.DefaultTreeSelectionModel;
import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreeSelectionModel;
import hu.aestallon.storageexplorer.domain.storage.model.entry.ScopedEntry;
import hu.aestallon.storageexplorer.domain.storage.model.instance.StorageInstance;
import hu.aestallon.storageexplorer.domain.storage.model.instance.StorageLocation;
import hu.aestallon.storageexplorer.domain.storage.model.instance.dto.DatabaseVendor;
import hu.aestallon.storageexplorer.domain.storage.model.instance.dto.FsStorageLocation;
import hu.aestallon.storageexplorer.domain.storage.model.instance.dto.SqlStorageLocation;
import hu.aestallon.storageexplorer.ui.misc.IconProvider;
import hu.aestallon.storageexplorer.ui.tree.model.node.StorageInstanceTreeNode;
import hu.aestallon.storageexplorer.ui.tree.model.node.StorageListTreeNode;
import hu.aestallon.storageexplorer.ui.tree.model.node.StorageMapTreeNode;
import hu.aestallon.storageexplorer.ui.tree.model.node.StorageObjectTreeNode;
import hu.aestallon.storageexplorer.ui.tree.model.node.StorageSequenceTreeNode;

public class StorageTree extends JTree implements Scrollable, Accessible {
  
  public static StorageTree create() {
    final var root = new DefaultMutableTreeNode("Storage Explorer");
    final var tree = new StorageTree(root);
    final var selectionModel = new DefaultTreeSelectionModel();
    selectionModel.setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
    tree.setSelectionModel(selectionModel);
    tree.setCellRenderer(new StorageTreeNodeRenderer());
    return tree;
  }

  private static int indexOfStorageIndex(
      final StorageInstance storageInstance,
      final DefaultMutableTreeNode root) {
    for (int i = 0; i < root.getChildCount(); i++) {
      final var storageInstanceTreeNode = (StorageInstanceTreeNode) root.getChildAt(i);
      if (Objects.equals(storageInstanceTreeNode.storageInstance(), storageInstance)) {
        return i;
      }
    }
    return -1;
  }

  private StorageTree(final TreeNode root) {
    super(root, true);
  }
  
  public DefaultTreeModel model() {
    return (DefaultTreeModel) getModel();
  }
  
  public StorageInstanceTreeNode importStorage(final StorageInstance storageInstance) {
    final DefaultTreeModel model = model();
    final DefaultMutableTreeNode root = (DefaultMutableTreeNode) model.getRoot();
    final StorageInstanceTreeNode storageInstanceTreeNode =
        new StorageInstanceTreeNode(storageInstance);
    model.insertNodeInto(storageInstanceTreeNode, root, root.getChildCount());
    return storageInstanceTreeNode;
  }
  
  public StorageInstanceTreeNode reindexStorage(final StorageInstance storageInstance) {
    DefaultTreeModel model = model();
    final DefaultMutableTreeNode root = (DefaultMutableTreeNode) model.getRoot();
    final int idx = indexOfStorageIndex(storageInstance, root);
    final int pos;
    if (idx < 0) {
      pos = root.getChildCount();
    } else {
      pos = idx;
      // Drop Storage:
      model.removeNodeFromParent((MutableTreeNode) root.getChildAt(idx));
    }
    // Re-add Storage:
    final var storageInstanceTreeNode = new StorageInstanceTreeNode(storageInstance);
    model.insertNodeInto(storageInstanceTreeNode, root, pos);
    return storageInstanceTreeNode;
  }
  
  public void removeStorage(final StorageInstance storageInstance) {
    DefaultTreeModel model = model();
    final DefaultMutableTreeNode root = (DefaultMutableTreeNode) model.getRoot();
    final int idx = indexOfStorageIndex(storageInstance, root);
    if (idx < 0) {
      return;
    }

    model.removeNodeFromParent((MutableTreeNode) root.getChildAt(idx));
  }
  
  private static final class StorageTreeNodeRenderer extends DefaultTreeCellRenderer {

    @Override
    public java.awt.Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel,
                                                           boolean expanded, boolean leaf, int row,
                                                           boolean hasFocus) {
      super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
      if (value instanceof StorageListTreeNode) {
        if (((StorageListTreeNode) value).getUserObject() instanceof ScopedEntry) {
          setIcon(IconProvider.SCOPED_LIST);
        } else {
          setIcon(IconProvider.LIST);
        }

      } else if (value instanceof StorageMapTreeNode) {
        if (((StorageMapTreeNode) value).getUserObject() instanceof ScopedEntry) {
          setIcon(IconProvider.SCOPED_MAP);
        } else {
          setIcon(IconProvider.MAP);
        }

      } else if (value instanceof StorageObjectTreeNode) {
        if (((StorageObjectTreeNode) value).getUserObject() instanceof ScopedEntry) {
          setIcon(IconProvider.SCOPED_OBJ);
        } else {
          setIcon(IconProvider.OBJ);
        }

      } else if (value instanceof StorageInstanceTreeNode) {
        final StorageInstance storageInstance = ((StorageInstanceTreeNode) value).storageInstance();
        final StorageLocation location = storageInstance.location();
        if (location instanceof FsStorageLocation) {
          setIcon(IconProvider.DB_FS);
        } else if (location instanceof SqlStorageLocation) {
          final DatabaseVendor vendor = ((SqlStorageLocation) location).getVendor();
          if (DatabaseVendor.PG == vendor) {
            setIcon(IconProvider.DB_PG);
          } else if (DatabaseVendor.ORACLE == vendor) {
            setIcon(IconProvider.DB_ORA);
          } else if(DatabaseVendor.H2 == vendor) {
            setIcon(IconProvider.DB_H2);
          } else {
            setIcon(IconProvider.DB);
          }
          
        } else {
          setIcon(IconProvider.DB);
        }

      } else if (value instanceof StorageSequenceTreeNode) {
        setIcon(IconProvider.SEQUENCE);

      }

      return this;
    }
  }
  
}

package hu.aestallon.storageexplorer.ui.tree.model;

import java.util.List;
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
import org.apache.tomcat.util.modeler.modules.ModelerSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.datasource.ConnectionHolder;
import hu.aestallon.storageexplorer.domain.storage.model.entry.ListEntry;
import hu.aestallon.storageexplorer.domain.storage.model.entry.MapEntry;
import hu.aestallon.storageexplorer.domain.storage.model.entry.ObjectEntry;
import hu.aestallon.storageexplorer.domain.storage.model.entry.ScopedEntry;
import hu.aestallon.storageexplorer.domain.storage.model.entry.SequenceEntry;
import hu.aestallon.storageexplorer.domain.storage.model.instance.StorageInstance;
import hu.aestallon.storageexplorer.domain.storage.model.instance.StorageLocation;
import hu.aestallon.storageexplorer.domain.storage.model.instance.dto.DatabaseVendor;
import hu.aestallon.storageexplorer.domain.storage.model.instance.dto.FsStorageLocation;
import hu.aestallon.storageexplorer.domain.storage.model.instance.dto.SqlStorageLocation;
import hu.aestallon.storageexplorer.ui.misc.IconProvider;
import hu.aestallon.storageexplorer.ui.tree.model.node.ClickableTreeNode;
import hu.aestallon.storageexplorer.ui.tree.model.node.StorageInstanceTreeNode;
import hu.aestallon.storageexplorer.ui.tree.model.node.StorageListTreeNode;
import hu.aestallon.storageexplorer.ui.tree.model.node.StorageMapTreeNode;
import hu.aestallon.storageexplorer.ui.tree.model.node.StorageObjectTreeNode;
import hu.aestallon.storageexplorer.ui.tree.model.node.StorageSchemaTreeNode;
import hu.aestallon.storageexplorer.ui.tree.model.node.StorageSequenceTreeNode;
import hu.aestallon.storageexplorer.ui.tree.model.node.StorageTypeTreeNode;
import static hu.aestallon.storageexplorer.util.Streams.enumerationToStream;

public class StorageTree extends JTree implements Scrollable, Accessible {

  private static final Logger log = LoggerFactory.getLogger(StorageTree.class);

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

  public StorageInstanceTreeNode nodeOf(final StorageInstance storageInstance) {
    DefaultTreeModel model = model();
    final DefaultMutableTreeNode root = (DefaultMutableTreeNode) model.getRoot();
    final int idx = indexOfStorageIndex(storageInstance, root);
    if (idx < 0) {
      log.warn("Could not find storage instance node for {}", storageInstance);
      return null;
    }

    return (StorageInstanceTreeNode) root.getChildAt(idx);
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

  public ClickableTreeNode incorporateObjectEntry(final StorageInstance storageInstance,
                                                  final ObjectEntry objectEntry) {
    final var model = model();
    final StorageInstanceTreeNode sitn = nodeOf(storageInstance);
    if (sitn == null) {
      return null;
    }

    StorageSchemaTreeNode schemaNode = null;
    for (int i = 0; i < sitn.getChildCount(); i++) {
      final var child = (DefaultMutableTreeNode) sitn.getChildAt(i);
      if (child instanceof StorageSchemaTreeNode && Objects.equals(child.getUserObject(),
          objectEntry.uri().getScheme())) {
        schemaNode = (StorageSchemaTreeNode) child;
        break;
      }
    }

    if (schemaNode == null) {
      int schemaIdx = 0;
      for (int i = 0; i < sitn.getChildCount(); i++) {
        final var child = (DefaultMutableTreeNode) sitn.getChildAt(i);
        if (!(child instanceof StorageSchemaTreeNode)) {
          schemaIdx = i;
        }

        if (child instanceof StorageSchemaTreeNode
            && ((String) child.getUserObject()).compareTo(objectEntry.uri().getScheme()) > 0) {
          schemaIdx = i;
          break;
        }
      }
      model.insertNodeInto(
          new StorageSchemaTreeNode(objectEntry.uri().getScheme(), List.of(objectEntry)),
          sitn,
          schemaIdx);
      // new Schema Node: sitn->schema->type->object
      return (StorageObjectTreeNode) sitn
          .getChildAt(schemaIdx)
          .getChildAt(0)
          .getChildAt(0);
    }

    StorageTypeTreeNode typeNode = null;
    int typeIdx = 0;
    for (int i = 0; i < schemaNode.getChildCount(); i++) {
      final var child = (StorageTypeTreeNode) schemaNode.getChildAt(i);
      final String typeName = (String) child.getUserObject();
      if (Objects.equals(typeName, objectEntry.typeName())) {
        typeNode = child;
        break;
      }

      if (typeName.compareTo(objectEntry.typeName()) > 0) {
        typeIdx = i;
        break;
      }
    }

    if (typeNode == null) {
      model.insertNodeInto(
          new StorageTypeTreeNode(objectEntry.typeName(), List.of(objectEntry)),
          schemaNode,
          typeIdx);
      return (StorageObjectTreeNode) schemaNode.getChildAt(typeIdx).getChildAt(0);
    }

    final boolean alreadyPresent = enumerationToStream(typeNode.children()).anyMatch(
        it -> Objects.equals(it.toString(), objectEntry.uuid()));
    if (alreadyPresent) {
      return null;
    }

    final StorageObjectTreeNode objectTreeNode = new StorageObjectTreeNode(objectEntry);
    model.insertNodeInto(objectTreeNode, typeNode, typeNode.getChildCount());
    return objectTreeNode;
  }

  public ClickableTreeNode incorporateListEntry(final StorageInstance storageInstance,
                                                final ListEntry listEntry) {
    final var model = model();
    final StorageInstanceTreeNode sitn = nodeOf(storageInstance);
    if (sitn == null) {
      return null;
    }

    int idx = 0;
    for (int i = 0; i < sitn.getChildCount(); i++) {
      final var child = (DefaultMutableTreeNode) sitn.getChildAt(i);
      if (!(child instanceof StorageListTreeNode)) {
        break;
      }

      final var listNodeChild = (StorageListTreeNode) child;
      if (listNodeChild.toString().equals(listEntry.displayName())) {
        return null;
      }

      if (listNodeChild.toString().compareTo(listEntry.displayName()) > 0) {
        idx = i + 1;
        break;
      }

    }

    final StorageListTreeNode node = new StorageListTreeNode(listEntry);
    model.insertNodeInto(node, sitn, idx);
    return node;
  }

  public ClickableTreeNode incorporateMapEntry(final StorageInstance storageInstance,
                                               final MapEntry mapEntry) {
    final var model = model();
    final StorageInstanceTreeNode sitn = nodeOf(storageInstance);
    if (sitn == null) {
      return null;
    }

    int idx = 0;
    for (int i = 0; i < sitn.getChildCount(); i++) {
      final var child = (DefaultMutableTreeNode) sitn.getChildAt(i);
      if (child instanceof StorageListTreeNode) {
        idx++;
        continue;
      }

      if (!(child instanceof StorageMapTreeNode)) {
        break;
      }

      final var mapNodeChild = (StorageMapTreeNode) child;
      if (mapNodeChild.toString().equals(mapEntry.displayName())) {
        return null;
      }

      if (mapNodeChild.toString().compareTo(mapEntry.displayName()) > 0) {
        idx = i + 1;
        break;
      }

    }

    final var node = new StorageMapTreeNode(mapEntry);
    model.insertNodeInto(node, sitn, idx);
    return node;
  }

  public ClickableTreeNode incorporateSequenceEntry(final StorageInstance storageInstance,
                                                    final SequenceEntry sequenceEntry) {
    final var model = model();
    final StorageInstanceTreeNode sitn = nodeOf(storageInstance);
    if (sitn == null) {
      return null;
    }

    int idx = 0;
    for (int i = 0; i < sitn.getChildCount(); i++) {
      final var child = (DefaultMutableTreeNode) sitn.getChildAt(i);
      if (child instanceof StorageListTreeNode || child instanceof StorageMapTreeNode) {
        idx++;
        continue;
      }

      if (!(child instanceof StorageSequenceTreeNode)) {
        break;
      }

      final var seqNodeChild = (StorageSequenceTreeNode) child;
      if (seqNodeChild.toString().equals(sequenceEntry.displayName())) {
        return null;
      }

      if (seqNodeChild.toString().compareTo(sequenceEntry.displayName()) > 0) {
        idx = i + 1;
        break;
      }

    }

    final var node = new StorageSequenceTreeNode(sequenceEntry);
    model.insertNodeInto(node, sitn, idx);
    return node;
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
          } else if (DatabaseVendor.H2 == vendor) {
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

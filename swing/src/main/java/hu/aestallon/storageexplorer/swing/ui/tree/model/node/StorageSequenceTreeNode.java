package hu.aestallon.storageexplorer.swing.ui.tree.model.node;

import javax.swing.tree.DefaultMutableTreeNode;
import hu.aestallon.storageexplorer.storage.model.entry.SequenceEntry;
import hu.aestallon.storageexplorer.storage.model.entry.StorageEntry;

public final class StorageSequenceTreeNode extends DefaultMutableTreeNode implements ClickableTreeNode {
 
  public StorageSequenceTreeNode(SequenceEntry sequenceEntry) {
    super(sequenceEntry, false);
  }

  @Override
  public StorageEntry storageEntry() {
    return (StorageEntry) userObject;
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
  public String toString() {
    final var sequenceEntry = (SequenceEntry) userObject;
    return sequenceEntry.displayName();
  }
  
}

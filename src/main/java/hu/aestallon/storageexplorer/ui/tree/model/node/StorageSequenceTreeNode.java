package hu.aestallon.storageexplorer.ui.tree.model.node;

import javax.swing.tree.DefaultMutableTreeNode;
import hu.aestallon.storageexplorer.domain.storage.model.SequenceEntry;
import hu.aestallon.storageexplorer.domain.storage.model.StorageEntry;

public class StorageSequenceTreeNode extends DefaultMutableTreeNode implements ClickableTreeNode {
 
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
    return sequenceEntry.schema() + " / " + sequenceEntry.name();
  }
  
}

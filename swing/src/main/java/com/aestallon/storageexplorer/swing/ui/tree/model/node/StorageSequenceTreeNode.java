package com.aestallon.storageexplorer.swing.ui.tree.model.node;

import javax.swing.tree.DefaultMutableTreeNode;
import com.aestallon.storageexplorer.core.model.entry.SequenceEntry;
import com.aestallon.storageexplorer.core.model.entry.StorageEntry;

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

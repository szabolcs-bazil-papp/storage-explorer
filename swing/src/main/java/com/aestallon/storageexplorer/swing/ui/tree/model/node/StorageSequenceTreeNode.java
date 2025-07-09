package com.aestallon.storageexplorer.swing.ui.tree.model.node;

import javax.swing.tree.DefaultMutableTreeNode;
import com.aestallon.storageexplorer.client.userconfig.service.StorageEntryTrackingService;
import com.aestallon.storageexplorer.core.model.entry.SequenceEntry;
import com.aestallon.storageexplorer.core.model.entry.StorageEntry;

public final class StorageSequenceTreeNode extends DefaultMutableTreeNode implements ClickableTreeNode {
  
  private final transient StorageEntryTrackingService trackingService;
  public StorageSequenceTreeNode(SequenceEntry sequenceEntry,
                                 StorageEntryTrackingService trackingService) {
    super(sequenceEntry, false);
    this.trackingService = trackingService;
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
    return trackingService.getUserData(sequenceEntry)
        .map(StorageEntryTrackingService.StorageEntryUserData::name)
        .orElseGet(sequenceEntry::displayName);
  }
  
}

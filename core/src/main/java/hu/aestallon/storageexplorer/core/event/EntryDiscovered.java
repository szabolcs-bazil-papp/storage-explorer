package hu.aestallon.storageexplorer.core.event;

import hu.aestallon.storageexplorer.core.model.entry.StorageEntry;
import hu.aestallon.storageexplorer.core.model.instance.StorageInstance;

public record EntryDiscovered(StorageInstance storageInstance, StorageEntry storageEntry) {}

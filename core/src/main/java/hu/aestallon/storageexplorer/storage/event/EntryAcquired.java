package hu.aestallon.storageexplorer.storage.event;

import hu.aestallon.storageexplorer.storage.model.entry.StorageEntry;
import hu.aestallon.storageexplorer.storage.model.instance.StorageInstance;

public record EntryAcquired(StorageInstance storageInstance, StorageEntry storageEntry) {}

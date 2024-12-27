package com.aestallon.storageexplorer.core.event;

import com.aestallon.storageexplorer.core.model.entry.StorageEntry;
import com.aestallon.storageexplorer.core.model.instance.StorageInstance;

public record EntryDiscovered(StorageInstance storageInstance, StorageEntry storageEntry) {}

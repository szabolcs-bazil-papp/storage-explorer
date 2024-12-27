package hu.aestallon.storageexplorer.core.event;

import hu.aestallon.storageexplorer.core.model.entry.StorageEntry;

public record TreeTouchRequest(StorageEntry storageEntry) {}

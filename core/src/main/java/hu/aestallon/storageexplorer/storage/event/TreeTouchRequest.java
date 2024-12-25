package hu.aestallon.storageexplorer.storage.event;

import hu.aestallon.storageexplorer.storage.model.entry.StorageEntry;

public record TreeTouchRequest(StorageEntry storageEntry) {}

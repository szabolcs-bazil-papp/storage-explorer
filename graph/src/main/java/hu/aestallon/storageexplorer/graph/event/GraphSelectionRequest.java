package hu.aestallon.storageexplorer.graph.event;

import hu.aestallon.storageexplorer.storage.model.entry.StorageEntry;

public record GraphSelectionRequest(StorageEntry storageEntry) {}
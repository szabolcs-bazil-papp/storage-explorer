package hu.aestallon.storageexplorer.graph.event;

import hu.aestallon.storageexplorer.core.model.entry.StorageEntry;

public record GraphSelectionRequest(StorageEntry storageEntry) {}

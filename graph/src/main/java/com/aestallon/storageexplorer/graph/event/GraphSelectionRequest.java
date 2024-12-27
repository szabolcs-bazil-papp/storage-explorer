package com.aestallon.storageexplorer.graph.event;

import com.aestallon.storageexplorer.core.model.entry.StorageEntry;

public record GraphSelectionRequest(StorageEntry storageEntry) {}

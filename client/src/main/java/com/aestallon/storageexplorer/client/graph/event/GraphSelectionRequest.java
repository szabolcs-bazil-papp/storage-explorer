package com.aestallon.storageexplorer.client.graph.event;

import com.aestallon.storageexplorer.core.model.entry.StorageEntry;

public record GraphSelectionRequest(StorageEntry storageEntry) {}

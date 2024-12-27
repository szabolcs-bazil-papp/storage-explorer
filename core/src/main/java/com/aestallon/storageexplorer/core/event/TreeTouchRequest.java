package com.aestallon.storageexplorer.core.event;

import com.aestallon.storageexplorer.core.model.entry.StorageEntry;

public record TreeTouchRequest(StorageEntry storageEntry) {}

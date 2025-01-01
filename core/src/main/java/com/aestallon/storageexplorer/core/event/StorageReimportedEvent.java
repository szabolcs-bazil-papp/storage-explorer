package com.aestallon.storageexplorer.core.event;

import com.aestallon.storageexplorer.core.model.instance.StorageInstance;

public record StorageReimportedEvent(StorageInstance storageInstance) {}

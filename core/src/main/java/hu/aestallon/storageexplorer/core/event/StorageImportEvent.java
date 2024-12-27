package hu.aestallon.storageexplorer.core.event;

import hu.aestallon.storageexplorer.core.model.instance.StorageInstance;

public record StorageImportEvent(StorageInstance storageInstance) {}

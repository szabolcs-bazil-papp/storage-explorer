package hu.aestallon.storageexplorer.storage.event;

import hu.aestallon.storageexplorer.storage.model.instance.StorageInstance;

public record StorageImportEvent(StorageInstance storageInstance) {}
package hu.aestallon.storageexplorer.storage.event;

import java.net.URI;
import hu.aestallon.storageexplorer.storage.model.instance.StorageInstance;

public record EntryAcquisitionFailed(StorageInstance storageInstance, URI uri) {}

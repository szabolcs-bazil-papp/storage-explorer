package hu.aestallon.storageexplorer.core.event;

import java.net.URI;
import hu.aestallon.storageexplorer.core.model.instance.StorageInstance;

public record EntryAcquisitionFailed(StorageInstance storageInstance, URI uri) {}

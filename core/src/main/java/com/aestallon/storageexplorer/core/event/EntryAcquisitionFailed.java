package com.aestallon.storageexplorer.core.event;

import java.net.URI;
import com.aestallon.storageexplorer.core.model.instance.StorageInstance;

public record EntryAcquisitionFailed(StorageInstance storageInstance, URI uri) {}

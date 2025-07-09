package com.aestallon.storageexplorer.client.userconfig.event;

import com.aestallon.storageexplorer.client.userconfig.service.StorageEntryTrackingService;
import com.aestallon.storageexplorer.core.model.entry.StorageEntry;

public record StorageEntryUserDataChanged(
    StorageEntry storageEntry,
    StorageEntryTrackingService.StorageEntryUserData data) {}

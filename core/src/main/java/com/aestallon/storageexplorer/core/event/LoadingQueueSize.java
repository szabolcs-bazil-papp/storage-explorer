package com.aestallon.storageexplorer.core.event;

import com.aestallon.storageexplorer.core.model.instance.dto.StorageId;

public record LoadingQueueSize(StorageId storageId, long size) {}

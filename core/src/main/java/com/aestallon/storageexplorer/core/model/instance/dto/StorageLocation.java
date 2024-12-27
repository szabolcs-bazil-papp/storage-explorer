package com.aestallon.storageexplorer.core.model.instance.dto;

public sealed interface StorageLocation permits FsStorageLocation, SqlStorageLocation {
}

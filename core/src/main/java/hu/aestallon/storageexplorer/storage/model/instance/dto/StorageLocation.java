package hu.aestallon.storageexplorer.storage.model.instance.dto;

public sealed interface StorageLocation permits FsStorageLocation, SqlStorageLocation {
}

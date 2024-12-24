package hu.aestallon.storageexplorer.domain.storage.model.instance.dto;

public sealed interface StorageLocation permits FsStorageLocation, SqlStorageLocation {
}

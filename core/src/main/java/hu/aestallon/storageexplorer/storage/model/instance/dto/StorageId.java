package hu.aestallon.storageexplorer.storage.model.instance.dto;

import java.util.Objects;
import java.util.UUID;

public record StorageId(UUID uuid) {

  public StorageId(final UUID uuid) {
    this.uuid = uuid == null ? UUID.randomUUID() : uuid;
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass())
      return false;
    StorageId storageId = (StorageId) o;
    return Objects.equals(uuid, storageId.uuid);
  }

  @Override
  public String toString() {
    return uuid.toString();
  }
}

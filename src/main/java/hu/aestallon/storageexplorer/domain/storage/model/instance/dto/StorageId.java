package hu.aestallon.storageexplorer.domain.storage.model.instance.dto;

import java.util.Objects;
import java.util.UUID;

public final class StorageId {
  
  private final UUID uuid;
  
  public StorageId(final UUID uuid) {
    this.uuid = uuid == null ? UUID.randomUUID() : uuid;
  }

  public UUID uuid() {
    return uuid;
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass())
      return false;
    StorageId storageId = (StorageId) o;
    return Objects.equals(uuid, storageId.uuid);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(uuid);
  }

  @Override
  public String toString() {
    return "StorageId { " + uuid + " }";
  }
}

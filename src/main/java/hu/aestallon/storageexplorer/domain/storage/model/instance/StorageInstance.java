package hu.aestallon.storageexplorer.domain.storage.model.instance;

import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;
import org.smartbit4all.core.utility.StringConstant;
import org.springframework.util.Assert;
import hu.aestallon.storageexplorer.domain.storage.model.entry.StorageEntry;
import hu.aestallon.storageexplorer.domain.storage.model.instance.dto.Availability;
import hu.aestallon.storageexplorer.domain.storage.model.instance.dto.FsStorageLocation;
import hu.aestallon.storageexplorer.domain.storage.model.instance.dto.SqlStorageLocation;
import hu.aestallon.storageexplorer.domain.storage.model.instance.dto.StorageId;
import hu.aestallon.storageexplorer.domain.storage.model.instance.dto.StorageInstanceDto;
import hu.aestallon.storageexplorer.domain.storage.model.instance.dto.StorageInstanceType;
import hu.aestallon.storageexplorer.domain.storage.service.StorageIndex;

public final class StorageInstance {

  public static StorageInstance fromDto(final StorageInstanceDto dto) {
    Assert.notNull(dto, "StorageInstanceDto must not be null!");
    return new StorageInstance(new StorageId(dto.getId())).applyDto(dto);
  }

  private final StorageId id;
  private String name;
  private Availability availability;
  private StorageLocation location;

  private StorageIndex index;

  private StorageInstance(final StorageId id) {
    this.id = Objects.requireNonNull(id, "Storage Instance ID cannot be null!");
    availability = Availability.UNAVAILABLE;
  }

  public StorageId id() {
    return id;
  }

  public String name() {
    return name;
  }

  public void setName(String name) {
    this.name = (name == null) ? StringConstant.EMPTY : name;
  }

  public Availability availability() {
    return availability;
  }

  public void setAvailability(
      Availability availability) {
    this.availability = (availability == null) ? Availability.UNAVAILABLE : availability;
  }

  public StorageLocation location() {
    return location;
  }

  public void setLocation(
      StorageLocation location) {
    Assert.notNull(location, "StorageLocation must not be null!");
    this.location = location;
  }

  public StorageInstanceType type() {
    return location instanceof FsStorageLocation ? StorageInstanceType.FS : StorageInstanceType.DB;
  }


  public StorageIndex index() {
    return index;
  }

  public void setIndex(final StorageIndex index) {
    this.index = index;
  }

  public Stream<StorageEntry> entities() {
    return index == null ? Stream.empty() : index.entities();
  }

  public StorageInstance applyDto(final StorageInstanceDto dto) {
    Assert.notNull(dto, "StorageInstanceDto must not be null!");

    setName(dto.getName());
    setAvailability(dto.getAvailability());
    switch (dto.getType()) {
      case FS:
        setLocation(dto.getFs());
        break;
      case DB:
        setLocation(dto.getDb());
        break;
      default:
        throw new IllegalArgumentException("Unsupported storage type: " + dto.getType());
    }

    return this;
  }

  public StorageInstanceDto toDto() {
    return new StorageInstanceDto()
        .id(id.uuid())
        .name(name)
        .availability(availability)
        .type(location instanceof FsStorageLocation
            ? StorageInstanceType.FS
            : StorageInstanceType.DB)
        .fs(location instanceof FsStorageLocation ? (FsStorageLocation) location : null)
        .db(location instanceof SqlStorageLocation ? (SqlStorageLocation) location : null);
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass())
      return false;
    StorageInstance that = (StorageInstance) o;
    return Objects.equals(id, that.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }

  @Override
  public String toString() {
    return toDto().toString();
  }

}

package hu.aestallon.storageexplorer.domain.storage.model.instance.dto;

import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;

/**
 * StorageInstanceDto
 */
@JsonPropertyOrder({
    StorageInstanceDto.ID,
    StorageInstanceDto.NAME,
    StorageInstanceDto.TYPE,
    StorageInstanceDto.INDEXING_STRATEGY,
    StorageInstanceDto.AVAILABILITY,
    StorageInstanceDto.FS,
    StorageInstanceDto.DB
})
@JsonTypeName("StorageInstanceDto")
@javax.annotation.Generated(value = "org.openapitools.codegen.languages.JavaClientCodegen")
public class StorageInstanceDto {
  public static final String ID = "id";
  private UUID id;

  public static final String NAME = "name";
  private String name;

  public static final String TYPE = "type";
  private StorageInstanceType type;

  public static final String INDEXING_STRATEGY = "indexingStrategy";
  private IndexingStrategyType indexingStrategy;

  public static final String AVAILABILITY = "availability";
  private Availability availability;

  public static final String FS = "fs";
  private FsStorageLocation fs;

  public static final String DB = "db";
  private SqlStorageLocation db;

  public StorageInstanceDto() {
  }

  public StorageInstanceDto id(UUID id) {

    this.id = id;
    return this;
  }

  @javax.annotation.Nullable
  @Valid
  @JsonProperty(ID)
  @JsonInclude(value = JsonInclude.Include.USE_DEFAULTS)

  public UUID getId() {
    return id;
  }

  @JsonProperty(ID)
  @JsonInclude(value = JsonInclude.Include.USE_DEFAULTS)
  public void setId(UUID id) {
    this.id = id;
  }


  public StorageInstanceDto name(String name) {

    this.name = name;
    return this;
  }

  @javax.annotation.Nullable
  @JsonProperty(NAME)
  @JsonInclude(value = JsonInclude.Include.USE_DEFAULTS)

  public String getName() {
    return name;
  }

  @JsonProperty(NAME)
  @JsonInclude(value = JsonInclude.Include.USE_DEFAULTS)
  public void setName(String name) {
    this.name = name;
  }

  public StorageInstanceDto type(StorageInstanceType type) {
    this.type = type;
    return this;
  }

  @javax.annotation.Nonnull
  @NotNull
  @Valid
  @JsonProperty(TYPE)
  @JsonInclude(value = JsonInclude.Include.USE_DEFAULTS)

  public StorageInstanceType getType() {
    return type;
  }

  @JsonProperty(INDEXING_STRATEGY)
  @JsonInclude(value = JsonInclude.Include.USE_DEFAULTS)
  public void setIndexingStrategy(IndexingStrategyType indexingStrategy) {
    this.indexingStrategy = indexingStrategy;
  }

  public StorageInstanceDto indexingStrategy(IndexingStrategyType indexingStrategy) {
    this.indexingStrategy = indexingStrategy;
    return this;
  }

  @javax.annotation.Nonnull
  @NotNull
  @Valid
  @JsonProperty(INDEXING_STRATEGY)
  @JsonInclude(value = JsonInclude.Include.USE_DEFAULTS)

  public IndexingStrategyType getIndexingStrategy() {
    return indexingStrategy;
  }

  @JsonProperty(TYPE)
  @JsonInclude(value = JsonInclude.Include.USE_DEFAULTS)
  public void setType(StorageInstanceType type) {
    this.type = type;
  }


  public StorageInstanceDto availability(Availability availability) {

    this.availability = availability;
    return this;
  }

  @javax.annotation.Nullable
  @Valid
  @JsonProperty(AVAILABILITY)
  @JsonInclude(value = JsonInclude.Include.USE_DEFAULTS)

  public Availability getAvailability() {
    return availability;
  }

  @JsonProperty(AVAILABILITY)
  @JsonInclude(value = JsonInclude.Include.USE_DEFAULTS)
  public void setAvailability(Availability availability) {
    this.availability = availability;
  }


  public StorageInstanceDto fs(FsStorageLocation fs) {
    this.fs = fs;
    return this;
  }

  @javax.annotation.Nullable
  @Valid
  @JsonProperty(FS)
  @JsonInclude(value = JsonInclude.Include.USE_DEFAULTS)

  public FsStorageLocation getFs() {
    return fs;
  }

  @JsonProperty(FS)
  @JsonInclude(value = JsonInclude.Include.USE_DEFAULTS)
  public void setFs(FsStorageLocation fs) {
    this.fs = fs;
  }


  public StorageInstanceDto db(SqlStorageLocation db) {
    this.db = db;
    return this;
  }

  @javax.annotation.Nullable
  @Valid
  @JsonProperty(DB)
  @JsonInclude(value = JsonInclude.Include.USE_DEFAULTS)

  public SqlStorageLocation getDb() {
    return db;
  }


  @JsonProperty(DB)
  @JsonInclude(value = JsonInclude.Include.USE_DEFAULTS)
  public void setDb(SqlStorageLocation db) {
    this.db = db;
  }


  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    StorageInstanceDto storageInstanceDto = (StorageInstanceDto) o;
    return Objects.equals(this.id, storageInstanceDto.id) &&
        Objects.equals(this.name, storageInstanceDto.name) &&
        Objects.equals(this.type, storageInstanceDto.type) &&
        Objects.equals(this.indexingStrategy, storageInstanceDto.indexingStrategy) &&
        Objects.equals(this.availability, storageInstanceDto.availability) &&
        Objects.equals(this.fs, storageInstanceDto.fs) &&
        Objects.equals(this.db, storageInstanceDto.db);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, name, type, indexingStrategy, availability, fs, db);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class StorageInstanceDto {\n");
    sb.append("    id: ").append(toIndentedString(id)).append("\n");
    sb.append("    name: ").append(toIndentedString(name)).append("\n");
    sb.append("    type: ").append(toIndentedString(type)).append("\n");
    sb.append("    indexingStrategy: ").append(toIndentedString(indexingStrategy)).append("\n");
    sb.append("    availability: ").append(toIndentedString(availability)).append("\n");
    sb.append("    fs: ").append(toIndentedString(fs)).append("\n");
    sb.append("    db: ").append(toIndentedString(db)).append("\n");
    sb.append("}");
    return sb.toString();
  }

  private String toIndentedString(Object o) {
    if (o == null) {
      return "null";
    }
    return o.toString().replace("\n", "\n    ");
  }

}


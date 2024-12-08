package hu.aestallon.storageexplorer.domain.storage.model.instance.dto;

import java.nio.file.Path;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import hu.aestallon.storageexplorer.domain.storage.model.instance.StorageLocation;

@JsonPropertyOrder({
  FsStorageLocation.PATH
})
@JsonTypeName("FsStorageLocation")
@javax.annotation.Generated(value = "org.openapitools.codegen.languages.JavaClientCodegen")
public class FsStorageLocation implements StorageLocation {
  public static final String PATH = "path";
  private Path path = null;

  public FsStorageLocation() { 
  }

  public FsStorageLocation path(Path path) {
    
    this.path = path;
    return this;
  }

  @javax.annotation.Nonnull
  @NotNull
  @Valid
  @JsonProperty(PATH)
  @JsonInclude(value = JsonInclude.Include.USE_DEFAULTS)
  public Path getPath() {
    return path;
  }


  @JsonProperty(PATH)
  @JsonInclude(value = JsonInclude.Include.USE_DEFAULTS)
  public void setPath(Path path) {
    this.path = path;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    FsStorageLocation fsStorageLocation = (FsStorageLocation) o;
    return Objects.equals(this.path, fsStorageLocation.path);
  }

  @Override
  public int hashCode() {
    return Objects.hash(path);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class FsStorageLocation {\n");
    sb.append("    path: ").append(toIndentedString(path)).append("\n");
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

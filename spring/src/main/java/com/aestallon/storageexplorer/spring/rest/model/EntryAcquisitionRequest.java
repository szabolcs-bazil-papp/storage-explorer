package com.aestallon.storageexplorer.spring.rest.model;

import java.net.URI;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import io.swagger.v3.oas.annotations.media.Schema;


import jakarta.annotation.Generated;

/**
 * EntryAcquisitionRequest
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.8.0")
public class EntryAcquisitionRequest {

  @Valid
  private List<URI> uris = new ArrayList<>();

  public EntryAcquisitionRequest() {
    super();
  }

  /**
   * Constructor with only required parameters
   */
  public EntryAcquisitionRequest(List<URI> uris) {
    this.uris = uris;
  }

  public EntryAcquisitionRequest uris(List<URI> uris) {
    this.uris = uris;
    return this;
  }

  public EntryAcquisitionRequest addUrisItem(URI urisItem) {
    if (this.uris == null) {
      this.uris = new ArrayList<>();
    }
    this.uris.add(urisItem);
    return this;
  }

  /**
   * Get uris
   * @return uris
   */
  @NotNull @Valid 
  @Schema(name = "uris", requiredMode = Schema.RequiredMode.REQUIRED)
  @JsonProperty("uris")
  public List<URI> getUris() {
    return uris;
  }

  public void setUris(List<URI> uris) {
    this.uris = uris;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    EntryAcquisitionRequest entryAcquisitionRequest = (EntryAcquisitionRequest) o;
    return Objects.equals(this.uris, entryAcquisitionRequest.uris);
  }

  @Override
  public int hashCode() {
    return Objects.hash(uris);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class EntryAcquisitionRequest {\n");
    sb.append("    uris: ").append(toIndentedString(uris)).append("\n");
    sb.append("}");
    return sb.toString();
  }

  /**
   * Convert the given object to string with each line indented by 4 spaces
   * (except the first line).
   */
  private String toIndentedString(Object o) {
    if (o == null) {
      return "null";
    }
    return o.toString().replace("\n", "\n    ");
  }
}


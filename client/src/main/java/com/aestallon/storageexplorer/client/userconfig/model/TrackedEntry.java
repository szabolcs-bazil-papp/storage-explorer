/*
 * Copyright (C) 2025 Szabolcs Bazil Papp
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along with this program.
 * If not, see <http://www.gnu.org/licenses/>.
 */

package com.aestallon.storageexplorer.client.userconfig.model;

import java.net.URI;
import java.util.Objects;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({ "storageId", "uri", "underInspection" })
public class TrackedEntry {

  private UUID storageId;
  private URI uri;
  private boolean underInspection;
  
  public TrackedEntry() { /* POJO constructor */ }
  
  public TrackedEntry(UUID storageId, URI uri, boolean underInspection) {
    this.storageId = storageId;
    this.uri = uri;
    this.underInspection = underInspection;
  }
  
  @JsonProperty("storageId")
  public UUID getStorageId() {
    return storageId;
  }
  
  @JsonProperty("storageId")
  public void setStorageId(UUID storageId) {
    this.storageId = storageId;
  }
  
  @JsonProperty("uri")
  public URI getUri() {
    return uri;
  }
  
  @JsonProperty("uri")
  public void setUri(URI uri) {
    this.uri = uri;
  }

  @JsonProperty("underInspection")
  public boolean isUnderInspection() {
    return underInspection;
  }

  @JsonProperty("underInspection")
  public void setUnderInspection(final boolean underInspection) {
    this.underInspection = underInspection;
  }

  @Override
  public String toString() {
    return "TrackedEntry {" +
        "\n  storageId: " + storageId +
        ",\n  uri: " + uri +
        ",\n  underInspection: " + underInspection +
        "\n}";
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass())
      return false;
    TrackedEntry that = (TrackedEntry) o;
    return underInspection == that.underInspection && Objects.equals(storageId,
        that.storageId) && Objects.equals(uri, that.uri);
  }

  @Override
  public int hashCode() {
    return Objects.hash(storageId, uri, underInspection);
  }
}

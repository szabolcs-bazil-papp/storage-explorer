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
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;
import com.aestallon.storageexplorer.core.model.entry.StorageEntry;
import com.aestallon.storageexplorer.core.model.instance.dto.StorageId;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.annotation.JsonValue;

@JsonTypeName("Problem")
@JsonPropertyOrder({ "id", "timestamp", "type", "message", "storageId", "uri" })
public class Problem {

  public static Problem of(final String message) {
    return new Problem()
        .id(UUID.randomUUID())
        .timestamp(LocalDateTime.now())
        .type(ProblemType.GENERIC);
  }

  public static Problem ofStorage(StorageId id, String message) {
    return new Problem()
        .id(UUID.randomUUID())
        .timestamp(LocalDateTime.now())
        .type(ProblemType.STORAGE)
        .storageId(id.uuid())
        .message(message);
  }

  public static Problem ofStorageEntry(StorageEntry entry, String message) {
    return new Problem()
        .id(UUID.randomUUID())
        .timestamp(LocalDateTime.now())
        .type(ProblemType.ENTRY)
        .storageId(entry.storageId().uuid())
        .uri(entry.uri())
        .message(message);
  }

  public static Problem ofStorageEntry(StorageId storageId, URI uri, String message) {
    return new Problem()
        .id(UUID.randomUUID())
        .timestamp(LocalDateTime.now())
        .type(ProblemType.ENTRY)
        .storageId(storageId.uuid())
        .uri(uri)
        .message(message); }

  public enum ProblemType {

    GENERIC("GENERIC"),
    ENTRY("ENTRY"),
    STORAGE("STORAGE");

    private final String value;

    ProblemType(String value) {
      this.value = value;
    }

    @JsonValue
    public String getValue() {
      return value;
    }

    @Override
    public String toString() {
      return String.valueOf(value);
    }

    @JsonCreator
    public static ProblemType fromValue(String value) {
      for (ProblemType b : ProblemType.values()) {
        if (b.value.equals(value)) {
          return b;
        }
      }
      throw new IllegalArgumentException("Unexpected value '" + value + "'");
    }

  }


  private UUID id;
  private LocalDateTime timestamp;
  private ProblemType type;
  private String message;
  private UUID storageId;
  private URI uri;

  public Problem() {}

  @JsonProperty("id")
  @JsonInclude(value = JsonInclude.Include.USE_DEFAULTS)
  public UUID getId() {
    return id;
  }

  @JsonProperty("id")
  @JsonInclude(value = JsonInclude.Include.USE_DEFAULTS)
  public void setId(UUID id) {
    this.id = id;
  }

  public Problem id(UUID id) {
    this.id = id;
    return this;
  }

  @JsonProperty("timestamp")
  @JsonInclude(value = JsonInclude.Include.USE_DEFAULTS)
  public LocalDateTime getTimestamp() {
    return timestamp;
  }

  @JsonProperty("timestamp")
  @JsonInclude(value = JsonInclude.Include.USE_DEFAULTS)
  public void setTimestamp(LocalDateTime timestamp) {
    this.timestamp = timestamp;
  }

  public Problem timestamp(LocalDateTime timestamp) {
    this.timestamp = timestamp;
    return this;
  }

  @JsonProperty("type")
  @JsonInclude(value = JsonInclude.Include.USE_DEFAULTS)
  public ProblemType getType() {
    return type;
  }

  @JsonProperty("type")
  @JsonInclude(value = JsonInclude.Include.USE_DEFAULTS)
  public void setType(ProblemType type) {
    this.type = type;
  }

  public Problem type(ProblemType type) {
    this.type = type;
    return this;
  }

  @JsonProperty("message")
  @JsonInclude(value = JsonInclude.Include.USE_DEFAULTS)
  public String getMessage() {
    return message;
  }

  @JsonProperty("message")
  @JsonInclude(value = JsonInclude.Include.USE_DEFAULTS)
  public void setMessage(String message) {
    this.message = message;
  }

  public Problem message(String message) {
    this.message = message;
    return this;
  }

  @JsonProperty("storageId")
  @JsonInclude(value = JsonInclude.Include.USE_DEFAULTS)
  public UUID getStorageId() {
    return storageId;
  }

  @JsonProperty("storageId")
  @JsonInclude(value = JsonInclude.Include.USE_DEFAULTS)
  public void setStorageId(UUID storageId) {
    this.storageId = storageId;
  }

  public Problem storageId(UUID storageId) {
    this.storageId = storageId;
    return this;
  }

  @JsonProperty("uri")
  @JsonInclude(value = JsonInclude.Include.USE_DEFAULTS)
  public URI getUri() {
    return uri;
  }

  @JsonProperty("uri")
  @JsonInclude(value = JsonInclude.Include.USE_DEFAULTS)
  public void setUri(URI uri) {
    this.uri = uri;
  }

  public Problem uri(URI uri) {
    this.uri = uri;
    return this;
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass())
      return false;
    Problem problem = (Problem) o;
    return Objects.equals(id, problem.id) && Objects.equals(timestamp,
        problem.timestamp) && type == problem.type && Objects.equals(message,
        problem.message) && Objects.equals(storageId, problem.storageId)
        && Objects.equals(uri, problem.uri);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, timestamp, type, message, storageId, uri);
  }

  @Override
  public String toString() {
    return "Problem {" +
        "\n  id: " + id +
        ",\n  timestamp: " + timestamp +
        ",\n  type: " + type +
        ",\n  message: " + message +
        ",\n  storageId: " + storageId +
        ",\n  uri: " + uri +
        "\n}";
  }

}

/*
 * Copyright (C) 2026 Szabolcs Bazil Papp
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

package com.aestallon.storageexplorer.core.service.fs;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import com.aestallon.storageexplorer.core.service.engine.EngineResult;

/**
 * One version record ({@code ObjectVersion}): which serials it refers to, who and when. Part 0
 * of every version file, and also embedded in the descriptor as {@code currentVersion}.
 *
 * <p><strong>{@code FsStorageEngine}-internal only</strong> — see {@link ObjectData}'s note.
 *
 * <p>Ported from the {@code sb4a-fs} Rust library's {@code model} module
 * ({@code ../sb4a-fs/crates/sb4a-fs/src/model.rs}).
 */
public final class ObjectVersion {

  private static final String WHAT = "version record";

  private final LinkedHashMap<String, Object> data;

  private ObjectVersion(final LinkedHashMap<String, Object> data) {
    this.data = data;
  }

  /** Wraps an already-parsed object as a version record, with no validation. */
  static ObjectVersion wrap(final LinkedHashMap<String, Object> data) {
    return new ObjectVersion(data);
  }

  /** Parses the document; anything that is not a JSON object is rejected. */
  public static EngineResult<ObjectVersion> parse(final byte[] bytes) {
    return switch (JsonDocuments.parseObject(bytes, WHAT)) {
      case EngineResult.Ok<LinkedHashMap<String, Object>> ok -> new EngineResult.Ok<>(new ObjectVersion(ok.value()));
      case EngineResult.Err<LinkedHashMap<String, Object>> err -> new EngineResult.Err<>(err.error());
    };
  }

  /**
   * A version record with the fields in the order and form the platform writes them. The
   * serials are set with the setters. {@code createdAt} is the caller's responsibility to format
   * (the write path, T1.7, supplies "now" in the platform's own timestamp format).
   */
  public static ObjectVersion of(final String transactionId, final String createdAt,
                                  final String createdBy) {
    final LinkedHashMap<String, Object> o = new LinkedHashMap<>();
    o.put("serialNoData", null);
    o.put("serialNoRelation", null);
    o.put("transactionId", transactionId);
    o.put("createdAt", createdAt);
    o.put("createdByUri", null);
    o.put("createdBy", createdBy);
    o.put("operation", null);
    o.put("rebasedFromUri", null);
    o.put("commonAncestorUri", null);
    o.put("mergedWithUri", null);
    o.put("aspects", new LinkedHashMap<>());
    o.put("encryptionRule", JsonDocuments.NO_ENCRYPTION);
    return new ObjectVersion(o);
  }

  /** The compact JSON bytes: the exact input again if nothing was changed. */
  public byte[] toBytes() {
    return JsonDocuments.toBytes(data);
  }

  /** The underlying JSON object, for the fields this engine has no accessor for. */
  public Map<String, Object> raw() {
    return data;
  }

  /** Structural equality on the underlying JSON object (order-insensitive, like {@code Map}'s own
   *  contract) — matching the Rust library's derived {@code PartialEq} on its own Map-backed
   *  struct, which indexmap's own equality is order-insensitive about too. */
  @Override
  public boolean equals(final Object o) {
    return (o instanceof ObjectVersion other) && data.equals(other.data);
  }

  @Override
  public int hashCode() {
    return data.hashCode();
  }

  /** The serial of the data file this version reads its content from. */
  public EngineResult<Optional<Long>> serialNoData() {
    return JsonDocuments.optLong(data, "serialNoData");
  }

  /** The serial of the relation file of this version. */
  public EngineResult<Optional<Long>> serialNoRelation() {
    return JsonDocuments.optLong(data, "serialNoRelation");
  }

  public EngineResult<Optional<String>> transactionId() {
    return JsonDocuments.optStr(data, "transactionId");
  }

  /** The creation time exactly as written (ISO-8601 text). */
  public EngineResult<Optional<String>> createdAt() {
    return JsonDocuments.optStr(data, "createdAt");
  }

  public EngineResult<Optional<String>> createdBy() {
    return JsonDocuments.optStr(data, "createdBy");
  }

  /** The rule the data of this version is encrypted with; absent means none. */
  public EngineResult<String> encryptionRule() {
    return switch (JsonDocuments.optStr(data, "encryptionRule")) {
      case EngineResult.Ok<Optional<String>> ok -> new EngineResult.Ok<>(
          ok.value().orElse(JsonDocuments.NO_ENCRYPTION));
      case EngineResult.Err<Optional<String>> err -> new EngineResult.Err<>(err.error());
    };
  }

  public void setSerialNoData(final Long serial) {
    JsonDocuments.setOptLong(data, "serialNoData", serial);
  }

  public void setSerialNoRelation(final Long serial) {
    JsonDocuments.setOptLong(data, "serialNoRelation", serial);
  }

  public void setEncryptionRule(final String rule) {
    data.put("encryptionRule", rule);
  }
}

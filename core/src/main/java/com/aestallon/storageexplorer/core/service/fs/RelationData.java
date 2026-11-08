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
 * The relations of an object version ({@code StorageObjectRelationData}): named references and
 * collections of references to other objects. Read-only for now — this engine carries it along
 * (relations are copied forward onto a new version, never edited; see T1.7).
 *
 * <p><strong>{@code FsStorageEngine}-internal only</strong> — see {@link ObjectData}'s note.
 *
 * <p>Ported from the {@code sb4a-fs} Rust library's {@code model} module
 * ({@code ../sb4a-fs/crates/sb4a-fs/src/model.rs}).
 */
public final class RelationData {

  private static final String WHAT = "relation data";

  private final LinkedHashMap<String, Object> data;

  private RelationData(final LinkedHashMap<String, Object> data) {
    this.data = data;
  }

  /** Parses the document; anything that is not a JSON object is rejected. */
  public static EngineResult<RelationData> parse(final byte[] bytes) {
    return switch (JsonDocuments.parseObject(bytes, WHAT)) {
      case EngineResult.Ok<LinkedHashMap<String, Object>> ok -> new EngineResult.Ok<>(new RelationData(ok.value()));
      case EngineResult.Err<LinkedHashMap<String, Object>> err -> new EngineResult.Err<>(err.error());
    };
  }

  /** The compact JSON bytes: the exact input again if nothing was changed. */
  public byte[] toBytes() {
    return JsonDocuments.toBytes(data);
  }

  /** The underlying JSON object, for the fields this engine has no accessor for. */
  public Map<String, Object> raw() {
    return data;
  }

  /** Structural equality on the underlying JSON object — see {@link ObjectVersion#equals}. */
  @Override
  public boolean equals(final Object o) {
    return (o instanceof RelationData other) && data.equals(other.data);
  }

  @Override
  public int hashCode() {
    return data.hashCode();
  }

  public EngineResult<Optional<String>> uri() {
    return JsonDocuments.optStr(data, "uri");
  }

  /** The {@code references} map (name to reference), if the document has one. */
  public EngineResult<Optional<Map<String, Object>>> references() {
    return JsonDocuments.optObject(data, "references");
  }

  /** The {@code collections} map (name to list of references), if the document has one. */
  public EngineResult<Optional<Map<String, Object>>> collections() {
    return JsonDocuments.optObject(data, "collections");
  }
}

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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import com.aestallon.storageexplorer.core.service.engine.EngineResult;
import com.aestallon.storageexplorer.core.service.engine.StorageEngineError;

/**
 * The head descriptor of a versioned object ({@code StorageObjectData}) — a typed view over an
 * order-preserving JSON object. Only the fields this engine needs have accessors; everything else
 * travels along untouched on a read-modify-write round trip. The entity payload itself is not
 * modelled at all; it is opaque bytes.
 *
 * <p><strong>{@code FsStorageEngine}-internal only</strong> — never crosses the
 * {@code StorageEngine} interface boundary. See the package's design note in the implementation
 * plan (Milestone 1).
 *
 * <p>Ported from the {@code sb4a-fs} Rust library's {@code model} module
 * ({@code ../sb4a-fs/crates/sb4a-fs/src/model.rs}).
 */
public final class ObjectData {

  private static final String WHAT = "object descriptor";

  private final LinkedHashMap<String, Object> data;

  private ObjectData(final LinkedHashMap<String, Object> data) {
    this.data = data;
  }

  /** Parses the document; anything that is not a JSON object is rejected. */
  public static EngineResult<ObjectData> parse(final byte[] bytes) {
    return switch (JsonDocuments.parseObject(bytes, WHAT)) {
      case EngineResult.Ok<LinkedHashMap<String, Object>> ok -> new EngineResult.Ok<>(new ObjectData(ok.value()));
      case EngineResult.Err<LinkedHashMap<String, Object>> err -> new EngineResult.Err<>(err.error());
    };
  }

  /**
   * The descriptor of a brand new object, with the fields in the order and form the platform
   * writes them. The first version is attached with {@link #setCurrentVersion(ObjectVersion)}.
   */
  public static ObjectData of(final String uri, final String className, final Strategy strategy,
                               final Integer indexSize) {
    final LinkedHashMap<String, Object> o = new LinkedHashMap<>();
    o.put("uri", uri);
    o.put("currentVersion", null);
    o.put("className", className);
    o.put("pendingVersion", null);
    o.put("deleted", false);
    o.put("strategy", strategy.asDescriptorString());
    o.put("indexSize", indexSize);
    o.put("objectStreams", new ArrayList<>());
    o.put("encryptionRule", JsonDocuments.NO_ENCRYPTION);
    o.put("transactionUri", null);
    return new ObjectData(o);
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
    return (o instanceof ObjectData other) && data.equals(other.data);
  }

  @Override
  public int hashCode() {
    return data.hashCode();
  }

  public EngineResult<Optional<String>> uri() {
    return JsonDocuments.optStr(data, "uri");
  }

  public EngineResult<Optional<String>> className() {
    return JsonDocuments.optStr(data, "className");
  }

  /** The version file layout; an absent or null strategy means {@link Strategy#CLASSIC}. */
  public EngineResult<Strategy> strategy() {
    final Optional<String> raw;
    switch (JsonDocuments.optStr(data, "strategy")) {
      case EngineResult.Ok<Optional<String>> ok -> raw = ok.value();
      case EngineResult.Err<Optional<String>> err -> {
        return new EngineResult.Err<>(err.error());
      }
    }
    return Strategy.fromDescriptor(raw);
  }

  public EngineResult<Optional<Integer>> indexSize() {
    final Optional<Long> value;
    switch (JsonDocuments.optLong(data, "indexSize")) {
      case EngineResult.Ok<Optional<Long>> ok -> value = ok.value();
      case EngineResult.Err<Optional<Long>> err -> {
        return new EngineResult.Err<>(err.error());
      }
    }
    if (value.isEmpty()) {
      return new EngineResult.Ok<>(Optional.empty());
    }
    if (value.get() > Integer.MAX_VALUE) {
      return new EngineResult.Err<>(JsonDocuments.wrongType("indexSize", "a 32 bit integer"));
    }
    return new EngineResult.Ok<>(Optional.of(value.get().intValue()));
  }

  public EngineResult<Boolean> deleted() {
    final Optional<Boolean> raw;
    switch (JsonDocuments.optBool(data, "deleted")) {
      case EngineResult.Ok<Optional<Boolean>> ok -> raw = ok.value();
      case EngineResult.Err<Optional<Boolean>> err -> {
        return new EngineResult.Err<>(err.error());
      }
    }
    return new EngineResult.Ok<>(raw.orElse(false));
  }

  /** The encryption rule of the object (single-version objects keep it here); absent means none. */
  public EngineResult<String> encryptionRule() {
    final Optional<String> raw;
    switch (JsonDocuments.optStr(data, "encryptionRule")) {
      case EngineResult.Ok<Optional<String>> ok -> raw = ok.value();
      case EngineResult.Err<Optional<String>> err -> {
        return new EngineResult.Err<>(err.error());
      }
    }
    return new EngineResult.Ok<>(raw.orElse(JsonDocuments.NO_ENCRYPTION));
  }

  /** The transaction that wrote this descriptor, if it was written inside one. */
  public EngineResult<Optional<String>> transactionUri() {
    return JsonDocuments.optStr(data, "transactionUri");
  }

  /**
   * The object streams of the object and where each of them ends. An absent or null list is
   * empty.
   */
  @SuppressWarnings("unchecked")
  public EngineResult<List<StreamRef>> objectStreams() {
    final Object raw = data.get("objectStreams");
    if (raw == null) {
      return new EngineResult.Ok<>(List.of());
    }
    if (!(raw instanceof List<?> list)) {
      return new EngineResult.Err<>(JsonDocuments.wrongType("objectStreams", "a list"));
    }
    final List<StreamRef> refs = new ArrayList<>(list.size());
    for (final Object item : list) {
      if (!(item instanceof Map<?, ?> itemMap)) {
        return new EngineResult.Err<>(JsonDocuments.wrongType("objectStreams", "a list of objects"));
      }
      final Map<String, Object> object = (Map<String, Object>) itemMap;
      final Optional<String> name;
      switch (JsonDocuments.optStr(object, "name")) {
        case EngineResult.Ok<Optional<String>> ok -> name = ok.value();
        case EngineResult.Err<Optional<String>> err -> {
          return new EngineResult.Err<>(err.error());
        }
      }
      if (name.isEmpty()) {
        return new EngineResult.Err<>(new StorageEngineError.Malformed("an object stream has no name"));
      }
      final long headPosition;
      switch (JsonDocuments.optLong(object, "headPosition")) {
        case EngineResult.Ok<Optional<Long>> ok -> headPosition = ok.value().orElse(0L);
        case EngineResult.Err<Optional<Long>> err -> {
          return new EngineResult.Err<>(err.error());
        }
      }
      refs.add(new StreamRef(name.get(), headPosition));
    }
    return new EngineResult.Ok<>(refs);
  }

  /**
   * The version the head points to; empty for a descriptor without a version (single-version
   * objects, or objects without any save yet).
   */
  @SuppressWarnings("unchecked")
  public EngineResult<Optional<ObjectVersion>> currentVersion() {
    final Object raw = data.get("currentVersion");
    if (raw == null) {
      return new EngineResult.Ok<>(Optional.empty());
    }
    if (!(raw instanceof Map)) {
      return new EngineResult.Err<>(JsonDocuments.wrongType("currentVersion", "an object"));
    }
    return new EngineResult.Ok<>(Optional.of(
        ObjectVersion.wrap(new LinkedHashMap<>((Map<String, Object>) raw))));
  }

  /** Redirects the head to {@code version}. The key keeps its place if it exists already. */
  public void setCurrentVersion(final ObjectVersion version) {
    data.put("currentVersion", version.raw());
  }
}

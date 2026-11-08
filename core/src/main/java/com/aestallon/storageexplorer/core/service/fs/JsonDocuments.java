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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.aestallon.storageexplorer.core.service.engine.EngineResult;
import com.aestallon.storageexplorer.core.service.engine.StorageEngineError;

/**
 * Shared parsing/serialization and typed-field-access helpers for {@link ObjectData},
 * {@link ObjectVersion} and {@link RelationData} — ported from {@code model.rs}'s free functions
 * and its {@code json_document!} macro, which Java has no equivalent of (hence this holder class
 * instead of generated code per type).
 */
final class JsonDocuments {

  private JsonDocuments() {}

  /** The rule name that means "not encrypted". */
  static final String NO_ENCRYPTION = "none";

  /**
   * A dedicated, minimal mapper — not shared with any other part of the codebase. Compact output
   * (no pretty-printing) is Jackson's default; nothing here changes inclusion rules, so an
   * explicit JSON {@code null} in the source round-trips as {@code null}, never omitted.
   *
   * <p>{@code USE_LONG_FOR_INTS} is on: Jackson's untyped deserialization otherwise returns
   * {@code Integer} for small whole numbers, which would silently fail {@code equals()} against
   * a value this engine itself set as a boxed {@code Long} (see {@code ObjectVersion#equals}).
   * Rust's {@code serde_json::Number} has no such int/long split to begin with.
   */
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
      .configure(DeserializationFeature.USE_LONG_FOR_INTS, true);

  /** Parses {@code bytes} as a JSON object, order-preserving. Anything else is rejected. */
  @SuppressWarnings("unchecked")
  static EngineResult<LinkedHashMap<String, Object>> parseObject(final byte[] bytes,
                                                                   final String what) {
    final Object value;
    try {
      value = OBJECT_MAPPER.readValue(bytes, Object.class);
    } catch (final IOException e) {
      return new EngineResult.Err<>(
          new StorageEngineError.Malformed("the " + what + " is not valid JSON: " + e.getMessage()));
    }
    if (!(value instanceof Map)) {
      return new EngineResult.Err<>(
          new StorageEngineError.Malformed("the " + what + " is not a JSON object"));
    }
    // readValue(bytes, Object.class) binds a top-level JSON object to a LinkedHashMap already
    // (Jackson's default Map implementation), preserving source key order.
    return new EngineResult.Ok<>((LinkedHashMap<String, Object>) value);
  }

  /** Compact output, byte-identical to the input if the map wasn't changed. */
  static byte[] toBytes(final Map<String, Object> object) {
    try {
      return OBJECT_MAPPER.writeValueAsBytes(object);
    } catch (final IOException e) {
      // a plain Map<String,Object> of JSON-shaped values always serializes.
      throw new UncheckedIOException(e);
    }
  }

  static StorageEngineError.Malformed wrongType(final String key, final String expected) {
    return new StorageEngineError.Malformed("the field `" + key + "` is not " + expected);
  }

  static EngineResult<Optional<String>> optStr(final Map<String, Object> object, final String key) {
    final Object v = object.get(key);
    if (v == null) {
      return new EngineResult.Ok<>(Optional.empty());
    }
    if (v instanceof String s) {
      return new EngineResult.Ok<>(Optional.of(s));
    }
    return new EngineResult.Err<>(wrongType(key, "a string"));
  }

  /**
   * A non-negative integer, as a JSON number literal with no decimal point — mirroring
   * {@code serde_json::Value::as_u64}, which rejects a float-typed number (even one with an
   * integral value, like {@code 3.0}) as firmly as it rejects a negative one.
   */
  static EngineResult<Optional<Long>> optLong(final Map<String, Object> object, final String key) {
    final Object v = object.get(key);
    if (v == null) {
      return new EngineResult.Ok<>(Optional.empty());
    }
    if (isJsonInteger(v)) {
      final long value = ((Number) v).longValue();
      if (value >= 0) {
        return new EngineResult.Ok<>(Optional.of(value));
      }
    }
    return new EngineResult.Err<>(wrongType(key, "a non-negative integer"));
  }

  static EngineResult<Optional<Boolean>> optBool(final Map<String, Object> object, final String key) {
    final Object v = object.get(key);
    if (v == null) {
      return new EngineResult.Ok<>(Optional.empty());
    }
    if (v instanceof Boolean b) {
      return new EngineResult.Ok<>(Optional.of(b));
    }
    return new EngineResult.Err<>(wrongType(key, "a boolean"));
  }

  @SuppressWarnings("unchecked")
  static EngineResult<Optional<Map<String, Object>>> optObject(final Map<String, Object> object,
                                                                 final String key) {
    final Object v = object.get(key);
    if (v == null) {
      return new EngineResult.Ok<>(Optional.empty());
    }
    if (v instanceof Map) {
      return new EngineResult.Ok<>(Optional.of((Map<String, Object>) v));
    }
    return new EngineResult.Err<>(wrongType(key, "an object"));
  }

  static void setOptLong(final Map<String, Object> object, final String key, final Long value) {
    object.put(key, value);
  }

  private static boolean isJsonInteger(final Object v) {
    return v instanceof Integer || v instanceof Long || v instanceof Short || v instanceof Byte
        || v instanceof BigInteger;
  }
}

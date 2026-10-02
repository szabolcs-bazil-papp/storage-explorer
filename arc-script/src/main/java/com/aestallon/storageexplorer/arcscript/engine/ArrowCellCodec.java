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

package com.aestallon.storageexplorer.arcscript.engine;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.arrow.vector.BigIntVector;
import org.apache.arrow.vector.BitVector;
import org.apache.arrow.vector.FieldVector;
import org.apache.arrow.vector.Float8Vector;
import org.apache.arrow.vector.VarCharVector;
import org.apache.arrow.vector.VectorSchemaRoot;
import groovy.json.JsonSlurper;
import com.aestallon.storageexplorer.core.model.entry.StorageEntry;
import com.aestallon.storageexplorer.core.service.StorageInstanceExaminer;

/**
 * Shared read-back logic for {@link ArrowColumnStore}'s two physical layouts of a property column
 * - a {@code #fast} typed lane plus an always-present {@code #json} fallback lane - reused by both
 * the store itself (for its own resident/spilled scans) and {@link ArrowResultSetStore} (which reads
 * a detached spill file with no live {@link ArrowColumnStore} to consult).
 */
final class ArrowCellCodec {

  static final String FAST_SUFFIX = "#fast";
  static final String JSON_SUFFIX = "#json";

  private ArrowCellCodec() {}

  /**
   * Reads the value of {@code prop} at row {@code local} of {@code root}, dispatching on the
   * runtime type of the {@code #fast} vector rather than requiring the caller to already know the
   * column's decided {@link ArrowColumnStore.Kind}.
   */
  static StorageInstanceExaminer.PropertyDiscoveryResult read(
      final VectorSchemaRoot root, final String prop, final int local, final StorageEntry entry) {
    final VarCharVector json = (VarCharVector) root.getVector(prop + JSON_SUFFIX);
    if (json != null && !json.isNull(local)) {
      return jsonToPdr(new String(json.get(local), StandardCharsets.UTF_8), entry, prop);
    }

    final FieldVector fast = root.getVector(prop + FAST_SUFFIX);
    if (fast == null || fast.isNull(local)) {
      return new StorageInstanceExaminer.NoValue();
    }
    return switch (fast) {
      case VarCharVector v -> new StorageInstanceExaminer.StringFound(
          new String(v.get(local), StandardCharsets.UTF_8), entry, prop);
      case BigIntVector v -> new StorageInstanceExaminer.NumberFound(v.get(local), entry, prop);
      case Float8Vector v -> new StorageInstanceExaminer.NumberFound(v.get(local), entry, prop);
      case BitVector v -> new StorageInstanceExaminer.BooleanFound(v.get(local) != 0, entry, prop);
      default -> new StorageInstanceExaminer.NoValue();
    };
  }

  @SuppressWarnings("unchecked")
  static StorageInstanceExaminer.PropertyDiscoveryResult jsonToPdr(
      final String json, final StorageEntry entry, final String path) {
    final Object raw = new JsonSlurper().parseText(json);
    return toPdr(raw, entry, path);
  }

  @SuppressWarnings("unchecked")
  static StorageInstanceExaminer.PropertyDiscoveryResult toPdr(
      final Object raw, final StorageEntry entry, final String path) {
    return switch (raw) {
      case null -> new StorageInstanceExaminer.NoValue();
      case Boolean b -> new StorageInstanceExaminer.BooleanFound(b, entry, path);
      case Number num -> new StorageInstanceExaminer.NumberFound(num, entry, path);
      case String s -> new StorageInstanceExaminer.StringFound(s, entry, path);
      case List<?> l -> {
        final var elements = new ArrayList<StorageInstanceExaminer.PropertyDiscoveryResult>(l.size());
        for (int i = 0; i < l.size(); i++) {
          elements.add(toPdr(l.get(i), entry, path + "." + i));
        }
        yield new StorageInstanceExaminer.ListFound(elements, entry, true, path);
      }
      case Map<?, ?> m -> new StorageInstanceExaminer.ComplexFound((Map<String, Object>) m, entry, path);
      default -> new StorageInstanceExaminer.StringFound(String.valueOf(raw), entry, path);
    };
  }

}

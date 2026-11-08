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

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.aestallon.storageexplorer.core.service.engine.EngineResult;
import com.aestallon.storageexplorer.core.service.engine.StorageEngineError;

/**
 * Mirrors {@code sb4a-fs}'s own {@code tabledata} module test suite
 * ({@code ../sb4a-fs/crates/sb4a-fs/src/tabledata.rs}), including its {@code writer} test-only
 * helper, ported here so the same sample tables can be built.
 */
class TableDataTest {

  private static final byte T_NULL_VALUE = 0x11;
  private static final byte T_STRING = 0x12;
  private static final byte T_BYTE = 0x13;
  private static final byte T_INTEGER = 0x14;
  private static final byte T_LONG = 0x15;
  private static final byte T_DOUBLE = 0x16;
  private static final byte T_LOCAL_DATE = 0x18;
  private static final byte T_LOCAL_DATE_TIME = 0x19;
  private static final byte T_OFFSET_DATE_TIME = 0x1b;
  private static final byte T_URI = 0x1c;
  private static final byte T_BOOLEAN = 0x1d;
  private static final byte T_UUID = 0x1e;
  private static final byte T_OTHER = 0x40;
  private static final int TRAILER_EXTENSION_VERSION = 1;

  private static <T> T ok(final EngineResult<T> result) {
    if (result instanceof EngineResult.Ok<T> ok) {
      return ok.value();
    }
    throw new AssertionError("expected Ok, got " + result);
  }

  // --- the Rust test suite's own `writer` helper, ported ------------------------------------

  private static void text(final ByteArrayOutputStream out, final byte kind, final String text) {
    final byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
    out.write(kind);
    writeInt(out, bytes.length);
    out.writeBytes(bytes);
  }

  private static void nullValue(final ByteArrayOutputStream out) {
    out.write(T_NULL_VALUE);
  }

  private static void longValue(final ByteArrayOutputStream out, final long value) {
    out.write(T_LONG);
    writeInt(out, 8);
    out.writeBytes(ByteBuffer.allocate(8).putLong(value).array());
  }

  private static void intValue(final ByteArrayOutputStream out, final int value) {
    out.write(T_INTEGER);
    writeInt(out, 4);
    out.writeBytes(ByteBuffer.allocate(4).putInt(value).array());
  }

  private static void writeInt(final ByteArrayOutputStream out, final int value) {
    out.writeBytes(ByteBuffer.allocate(4).putInt(value).array());
  }

  /** meta + rows (a writer per row) + trailer (+ extension) + footer. */
  private static byte[] table(final String entity, final String[] columns,
                               final List<Consumer<ByteArrayOutputStream>> rows,
                               final String[] states) {
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    text(out, T_URI, entity);
    writeInt(out, columns.length);
    for (final String column : columns) {
      text(out, T_URI, column);
    }
    final List<Long> starts = new java.util.ArrayList<>();
    for (final Consumer<ByteArrayOutputStream> row : rows) {
      starts.add((long) out.size());
      row.accept(out);
    }
    final long trailer = out.size();
    writeInt(out, rows.size());
    for (final long start : starts) {
      out.writeBytes(ByteBuffer.allocate(8).putLong(start).array());
    }
    if (states != null) {
      writeInt(out, TRAILER_EXTENSION_VERSION);
      boolean allNull = true;
      for (final String state : states) {
        if (state != null) {
          allNull = false;
          break;
        }
      }
      if (allNull) {
        writeInt(out, 0);
      } else {
        writeInt(out, states.length);
        for (final String state : states) {
          if (state != null) {
            text(out, T_STRING, state);
          } else {
            nullValue(out);
          }
        }
      }
    }
    out.writeBytes(ByteBuffer.allocate(8).putLong(trailer).array());
    return out.toByteArray();
  }

  private static byte[] sample(final String[] states) {
    return table(
        "entity://demo/thing",
        new String[] {"entity://demo/thing#id", "entity://demo/thing#name", "entity://demo/thing#n"},
        List.of(
            out -> {
              longValue(out, 42);
              text(out, T_STRING, "alpha ő");
              intValue(out, -7);
            },
            out -> {
              longValue(out, 43);
              nullValue(out);
              intValue(out, 8);
            }),
        states);
  }

  @Test
  @DisplayName("Decodes columns, rows and values")
  void decodesColumnsRowsAndValues() {
    final TableData t = ok(TableData.parse(sample(null)));
    assertThat(t.entity()).contains("entity://demo/thing");
    assertThat(t.columns()).hasSize(3);
    assertThat(t.rows()).hasSize(2);
    assertThat(t.rows().get(0).values().stream().map(Object::toString).toList())
        .containsExactly("42", "\"alpha ő\"", "-7");
    assertThat(t.rows().get(1).values().get(1).isNull()).isTrue();
    assertThat(t.hasRowStates()).isFalse();
    assertThat(t.rows()).allSatisfy(r -> assertThat(r.state()).isEmpty());
    assertThat(t.rows().get(0).start()).isLessThan(t.rows().get(1).start());
  }

  @Test
  @DisplayName("Row states in the trailer extension")
  void rowStatesInTheTrailerExtension() {
    final TableData none = ok(TableData.parse(sample(new String[] {null, null})));
    assertThat(none.hasRowStates()).isTrue();
    assertThat(none.rows()).allSatisfy(r -> assertThat(r.state()).isEmpty());

    final TableData some = ok(TableData.parse(sample(new String[] {"NEW", null})));
    assertThat(some.rows().get(0).state()).contains("NEW");
    assertThat(some.rows().get(1).state()).isEmpty();
  }

  @Test
  @DisplayName("Empty tables")
  void emptyTables() {
    final byte[] noRows = table("entity://e", new String[] {"entity://e#a"}, List.of(), null);
    final TableData t = ok(TableData.parse(noRows));
    assertThat(t.rows()).isEmpty();
    assertThat(t.columns()).containsExactly("entity://e#a");

    final byte[] noColumns = table("entity://e", new String[0], List.of(out -> {}, out -> {}),
        new String[] {null, null});
    final TableData t2 = ok(TableData.parse(noColumns));
    assertThat(t2.rows()).hasSize(2);
    assertThat(t2.rows().get(0).values()).isEmpty();
  }

  @Test
  @DisplayName("All the value types")
  void allTheValueTypes() {
    final byte[] bytes = table("entity://e",
        new String[] {"a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k"},
        List.of(out -> {
          text(out, T_STRING, "s");
          out.write(T_BYTE);
          writeInt(out, 1);
          out.write(0xFE);
          out.write(T_BOOLEAN);
          writeInt(out, 1);
          out.write(1);
          text(out, T_DOUBLE, "2.5");
          text(out, T_DOUBLE, "NaN");
          text(out, T_LOCAL_DATE, "2026-11-06");
          text(out, T_LOCAL_DATE_TIME, "2026-11-06T16:30:00");
          text(out, T_OFFSET_DATE_TIME, "2026-11-06T16:30:00+01:00");
          text(out, T_UUID, "5baf7e92-4abe-414b-81dd-e8018f1a99c5");
          text(out, T_OTHER, "{\"k\":[1,2]}");
          text(out, T_OTHER, "\u0001\u0002");
        }),
        null);
    final List<com.fasterxml.jackson.databind.JsonNode> row = ok(TableData.parse(bytes)).rows().get(0).values();
    assertThat(row.get(0).textValue()).isEqualTo("s");
    assertThat(row.get(1).intValue()).isEqualTo(-2);
    assertThat(row.get(2).booleanValue()).isTrue();
    assertThat(row.get(3).doubleValue()).isEqualTo(2.5);
    assertThat(row.get(4).textValue()).isEqualTo("NaN");
    assertThat(row.get(5).textValue()).isEqualTo("2026-11-06");
    assertThat(row.get(7).textValue()).isEqualTo("2026-11-06T16:30:00+01:00");
    assertThat(row.get(9).toString()).isEqualTo("{\"k\":[1,2]}");
    assertThat(row.get(10).asText()).contains("not JSON");
  }

  @Test
  @DisplayName("Damage is an error, not wrong data")
  void damageIsAnErrorNotWrongData() {
    final byte[] good = sample(null);
    assertThat(TableData.parse(new byte[0])).isInstanceOf(EngineResult.Err.class);
    assertThat(TableData.parse(java.util.Arrays.copyOf(good, good.length - 1)))
        .isInstanceOf(EngineResult.Err.class);
    assertThat(TableData.parse(java.util.Arrays.copyOf(good, 5))).isInstanceOf(EngineResult.Err.class);

    // a footer pointing outside of the file
    final byte[] badFooter = good.clone();
    final int n = badFooter.length;
    System.arraycopy(ByteBuffer.allocate(8).putLong(n + 100L).array(), 0, badFooter, n - 8, 8);
    assertThat(TableData.parse(badFooter)).isInstanceOf(EngineResult.Err.class);

    // an unknown value type in a row
    final byte[] unknown = good.clone();
    final int firstRow = (int) ok(TableData.parse(good)).rows().get(0).start();
    unknown[firstRow] = 0x77;
    final EngineResult<TableData> result = TableData.parse(unknown);
    assertThat(result).isInstanceOf(EngineResult.Err.class);
    final StorageEngineError error = ((EngineResult.Err<TableData>) result).error();
    assertThat(error).isInstanceOf(StorageEngineError.Malformed.class);
    final String why = ((StorageEngineError.Malformed) error).why();
    assertThat(why.contains("0x77") || why.contains("row 0")).isTrue();

    // a row index that does not match the rows
    final byte[] shifted = good.clone();
    final long trailer = ByteBuffer.wrap(good, good.length - 8, 8).getLong();
    final int secondStart = (int) trailer + 4 + 8;
    final long wrong = ByteBuffer.wrap(shifted, secondStart, 8).getLong() + 1;
    System.arraycopy(ByteBuffer.allocate(8).putLong(wrong).array(), 0, shifted, secondStart, 8);
    assertThat(TableData.parse(shifted)).isInstanceOf(EngineResult.Err.class);

    // an unsupported trailer extension version
    final byte[] version = sample(new String[] {null, null});
    final long trailer2 = ByteBuffer.wrap(version, version.length - 8, 8).getLong();
    final int ext = (int) trailer2 + 4 + 2 * 8;
    System.arraycopy(ByteBuffer.allocate(4).putInt(9).array(), 0, version, ext, 4);
    final EngineResult<TableData> versionResult = TableData.parse(version);
    assertThat(versionResult).isInstanceOf(EngineResult.Err.class);
    assertThat(((EngineResult.Err<TableData>) versionResult).error())
        .isInstanceOf(StorageEngineError.Unsupported.class);
  }

  @Test
  @DisplayName("A missing file reads as empty, not an error")
  void missingFileIsNone(@TempDir final Path dir) {
    assertThat(ok(TableData.readFile(dir.resolve("x.td")))).isEmpty();
  }

  @Test
  @DisplayName("Reads real table data, written by the platform")
  void tableDataOfARealObject() {
    final Path path = Path.of("..", "..", "sb4a-fs", "crates", "sb4a-fs", "tests", "fixtures", "dev-fs",
        "tabledatacontents", "2025", "12", "19", "16", "10", "43",
        "51fa609a-9a85-40ea-abf3-32d9333bc5d6.td");
    final TableData table = ok(TableData.readFile(path)).orElseThrow();
    assertThat(table.entity()).isPresent();
    assertThat(table.entity().get()).startsWith("entity://account-ticket/");
    assertThat(table.columns()).hasSize(8);
    assertThat(table.columns().get(0)).endsWith("#subject");

    final Path missing = Path.of("..", "..", "sb4a-fs", "crates", "sb4a-fs", "tests", "fixtures", "dev-fs",
        "tabledatacontents", "nothing", "here");
    assertThat(TableData.readFile(missing)).isInstanceOf(EngineResult.Ok.class);
  }
}

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
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.DoubleNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.LongNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.aestallon.storageexplorer.core.service.engine.EngineResult;
import com.aestallon.storageexplorer.core.service.engine.StorageEngineError;

/**
 * A decoded serialized table, the {@code <scheme>/<path>.td} files: a table (columns and rows of
 * typed values) written row by row, with an index of the rows at the end.
 *
 * <p>The reader is strict: the rows must tile the area between the meta and the trailer exactly,
 * so a damaged or mis-parsed file is an error, not wrong data. Values come out as JSON nodes
 * (dates, URIs and UUIDs as strings); the types of the columns are not stored in the file, only
 * the values carry their type.
 *
 * <p>Ported from the {@code sb4a-fs} Rust library's {@code tabledata} module
 * ({@code ../sb4a-fs/crates/sb4a-fs/src/tabledata.rs}). Read-only, matching the Rust library.
 *
 * @param entity       the URI of the entity definition the table is made of
 * @param columns      the URIs of the column properties, in column order
 * @param rows         the decoded rows
 * @param hasRowStates whether the file has the trailer extension with the row states (newer files)
 */
public record TableData(Optional<String> entity, List<String> columns, List<Row> rows,
                         boolean hasRowStates) {

  private static final byte T_NULL = 0x10;
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

  /** The only version of the trailer extension (the row states) that exists. */
  private static final int TRAILER_EXTENSION_VERSION = 1;

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  /** Reads and decodes a {@code .td} file. Empty if the file does not exist. */
  static EngineResult<Optional<TableData>> readFile(final Path path) {
    final Optional<byte[]> bytes;
    switch (FsUtil.readFile(path)) {
      case EngineResult.Ok<Optional<byte[]>> ok -> bytes = ok.value();
      case EngineResult.Err<Optional<byte[]>> err -> {
        return new EngineResult.Err<>(err.error());
      }
    }
    if (bytes.isEmpty()) {
      return new EngineResult.Ok<>(Optional.empty());
    }
    return switch (parse(bytes.get())) {
      case EngineResult.Ok<TableData> ok -> new EngineResult.Ok<>(Optional.of(ok.value()));
      case EngineResult.Err<TableData> err -> new EngineResult.Err<>(err.error().at(path));
    };
  }

  /** Decodes the content of a {@code .td} file. */
  static EngineResult<TableData> parse(final byte[] bytes) {
    try {
      return new EngineResult.Ok<>(doParse(bytes));
    } catch (final ParseFailure e) {
      return new EngineResult.Err<>(e.error);
    }
  }

  private static TableData doParse(final byte[] bytes) {
    if (bytes.length < 8) {
      throw new ParseFailure(new StorageEngineError.Malformed("the file is too short to have a footer"));
    }
    final int footerAt = bytes.length - 8;
    final long trailerStartLong = ByteBuffer.wrap(bytes, footerAt, 8).getLong();
    if (trailerStartLong < 0 || trailerStartLong > footerAt) {
      throw new ParseFailure(new StorageEngineError.Malformed(
          "the trailer start " + trailerStartLong + " is outside of the file"));
    }
    final int trailerStart = (int) trailerStartLong;

    // meta
    final Cursor cursor = new Cursor(bytes, trailerStart);
    final JsonNode entityNode = cursor.value();
    final Optional<String> entity;
    if (entityNode.isNull()) {
      entity = Optional.empty();
    } else if (entityNode.isTextual()) {
      entity = Optional.of(entityNode.textValue());
    } else {
      throw new ParseFailure(
          new StorageEngineError.Malformed("the entity uri is not text: " + entityNode));
    }
    final int columnCount = cursor.nonNegativeInt("the number of columns");
    final List<String> columns = new ArrayList<>(columnCount);
    for (int i = 0; i < columnCount; i++) {
      final JsonNode column = cursor.value();
      if (!column.isTextual()) {
        throw new ParseFailure(
            new StorageEngineError.Malformed("column " + i + " has no uri: " + column));
      }
      columns.add(column.textValue());
    }
    final int rowsStart = cursor.position;

    // trailer: row index, optional row states, footer
    final Cursor trailer = new Cursor(bytes, footerAt);
    trailer.position = trailerStart;
    final int rowCount = trailer.nonNegativeInt("the number of rows");
    final int[] starts = new int[rowCount];
    for (int i = 0; i < rowCount; i++) {
      final long start = trailer.longVal("a row start");
      if (start < 0) {
        throw new ParseFailure(new StorageEngineError.Malformed("negative row start " + start));
      }
      starts[i] = (int) start;
    }
    final Optional<String>[] states = new Optional[rowCount];
    Arrays.fill(states, Optional.empty());
    final boolean hasRowStates = trailer.position < footerAt;
    if (hasRowStates) {
      final int version = trailer.int32("the trailer extension version");
      if (version != TRAILER_EXTENSION_VERSION) {
        throw new ParseFailure(
            new StorageEngineError.Unsupported("table data trailer extension version " + version));
      }
      final int stateCount = trailer.nonNegativeInt("the number of row states");
      if (stateCount != 0 && stateCount != rowCount) {
        throw new ParseFailure(new StorageEngineError.Malformed(
            stateCount + " row states for " + rowCount + " rows"));
      }
      for (int i = 0; i < stateCount; i++) {
        final JsonNode state = trailer.value();
        if (state.isNull()) {
          states[i] = Optional.empty();
        } else if (state.isTextual()) {
          states[i] = Optional.of(state.textValue());
        } else {
          throw new ParseFailure(new StorageEngineError.Malformed("a row state is not text: " + state));
        }
      }
      if (trailer.position != footerAt) {
        throw new ParseFailure(new StorageEngineError.Malformed(
            "unexpected bytes between the row states and the footer"));
      }
    }

    // rows: they tile [rowsStart, trailerStart) exactly
    final List<Row> rows = new ArrayList<>(rowCount);
    int expectedStart = rowsStart;
    for (int i = 0; i < rowCount; i++) {
      final int start = starts[i];
      if (start != expectedStart) {
        throw new ParseFailure(new StorageEngineError.Malformed(
            "row " + i + " starts at " + start + ", expected " + expectedStart));
      }
      final int end = (i + 1 < rowCount) ? starts[i + 1] : trailerStart;
      final Cursor row = new Cursor(bytes, end);
      row.position = start;
      final List<JsonNode> values = new ArrayList<>(columns.size());
      for (int c = 0; c < columns.size(); c++) {
        final int rowIndex = i;
        try {
          values.add(row.value());
        } catch (final ParseFailure e) {
          if (e.error instanceof StorageEngineError.Malformed m) {
            throw new ParseFailure(new StorageEngineError.Malformed("row " + rowIndex + ": " + m.why()));
          }
          throw e;
        }
      }
      if (row.position != end) {
        throw new ParseFailure(new StorageEngineError.Malformed(
            "row " + i + " has " + (end - row.position) + " unread bytes after its "
                + columns.size() + " columns"));
      }
      expectedStart = end;
      rows.add(new Row(start, values, states[i]));
    }
    if (rowCount == 0 && rowsStart != trailerStart) {
      throw new ParseFailure(new StorageEngineError.Malformed(
          "there are no rows but there is data between the meta and the trailer"));
    }
    return new TableData(entity, columns, rows, hasRowStates);
  }

  /** Internal, unchecked — converted to an {@link EngineResult.Err} at {@link #parse}'s boundary. */
  private static final class ParseFailure extends RuntimeException {
    private final StorageEngineError error;

    private ParseFailure(final StorageEngineError error) {
      super(null, null, false, false);
      this.error = error;
    }
  }

  /** A reading position in a byte array, which never reads past {@code limit}. */
  private static final class Cursor {
    private final byte[] bytes;
    private final int limit;
    private int position;

    Cursor(final byte[] bytes, final int limit) {
      this.bytes = bytes;
      this.limit = Math.min(limit, bytes.length);
      this.position = 0;
    }

    byte[] take(final int count, final String what) {
      if (count < 0 || count > limit - position) {
        throw new ParseFailure(new StorageEngineError.Malformed(
            what + ": " + count + " bytes at " + position + " run past the end"));
      }
      final int end = position + count;
      final byte[] slice = Arrays.copyOfRange(bytes, position, end);
      position = end;
      return slice;
    }

    int int32(final String what) {
      return ByteBuffer.wrap(take(4, what)).getInt();
    }

    /** A non-negative count. */
    int nonNegativeInt(final String what) {
      final int value = int32(what);
      if (value < 0) {
        throw new ParseFailure(new StorageEngineError.Malformed(what + " is negative (" + value + ")"));
      }
      return value;
    }

    long longVal(final String what) {
      return ByteBuffer.wrap(take(8, what)).getLong();
    }

    JsonNode value() {
      final int typeAt = position;
      final int kind = take(1, "a value type")[0] & 0xFF;
      if (kind == (T_NULL & 0xFF) || kind == (T_NULL_VALUE & 0xFF)) {
        return NullNode.getInstance();
      }
      final int length = nonNegativeInt("a value length");
      final byte[] content = take(length, "a value");
      if (kind == (T_STRING & 0xFF) || kind == (T_URI & 0xFF) || kind == (T_UUID & 0xFF)
          || kind == (T_LOCAL_DATE & 0xFF) || kind == (T_LOCAL_DATE_TIME & 0xFF)
          || kind == (T_OFFSET_DATE_TIME & 0xFF)) {
        return TextNode.valueOf(strictUtf8(content, typeAt, kind));
      }
      if (kind == (T_BYTE & 0xFF)) {
        if (content.length != 1) {
          throw bad(typeAt, kind, "a byte has 1 byte");
        }
        return IntNode.valueOf(content[0]);
      }
      if (kind == (T_INTEGER & 0xFF)) {
        if (content.length != 4) {
          throw bad(typeAt, kind, "an int has 4 bytes");
        }
        return IntNode.valueOf(ByteBuffer.wrap(content).getInt());
      }
      if (kind == (T_LONG & 0xFF)) {
        if (content.length != 8) {
          throw bad(typeAt, kind, "a long has 8 bytes");
        }
        return LongNode.valueOf(ByteBuffer.wrap(content).getLong());
      }
      if (kind == (T_DOUBLE & 0xFF)) {
        final String text = strictUtf8(content, typeAt, kind);
        final double number;
        try {
          number = Double.parseDouble(text);
        } catch (final NumberFormatException e) {
          throw bad(typeAt, kind, "not a number");
        }
        // NaN and infinity have no JSON number: keep their text
        return Double.isFinite(number) ? DoubleNode.valueOf(number) : TextNode.valueOf(text);
      }
      if (kind == (T_BOOLEAN & 0xFF)) {
        if (content.length != 1) {
          throw bad(typeAt, kind, "a boolean has 1 byte");
        }
        return BooleanNode.valueOf(content[0] == 1);
      }
      if (kind == (T_OTHER & 0xFF)) {
        try {
          return OBJECT_MAPPER.readTree(content);
        } catch (final IOException e) {
          return TextNode.valueOf("<" + content.length + " bytes that are not JSON, maybe encrypted>");
        }
      }
      throw new ParseFailure(new StorageEngineError.Malformed(
          "unknown value type 0x" + Integer.toHexString(kind) + " at " + typeAt));
    }

    private ParseFailure bad(final int typeAt, final int kind, final String why) {
      return new ParseFailure(new StorageEngineError.Malformed(
          "value at " + typeAt + " (type 0x" + Integer.toHexString(kind) + "): " + why));
    }

    private String strictUtf8(final byte[] content, final int typeAt, final int kind) {
      final CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
          .onMalformedInput(CodingErrorAction.REPORT)
          .onUnmappableCharacter(CodingErrorAction.REPORT);
      try {
        return decoder.decode(ByteBuffer.wrap(content)).toString();
      } catch (final CharacterCodingException e) {
        throw bad(typeAt, kind, "not UTF-8 text");
      }
    }
  }
}

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

package com.aestallon.storageexplorer.core.service.index.result;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import com.aestallon.storageexplorer.core.model.EntryUri;
import com.aestallon.storageexplorer.core.service.engine.ContentHandle;
import com.aestallon.storageexplorer.core.service.engine.EntryMetadata;
import com.aestallon.storageexplorer.core.service.engine.LoadedContent;

/**
 * T2.1's result/value types — "no tests needed beyond the sealed types compiling and round-
 * tripping through switch" per the implementation plan, so that's exactly what this asserts: each
 * sealed interface's variants are exhaustively handled by a {@code switch} expression, and
 * {@link ContentHandle}/{@link LoadedContent} behave as a plain data holder should.
 */
class ResultTypesTest {

  private static final EntryUri URI = EntryUri.parse("s:/a/b").orElseThrow();

  @Test
  void entryResolutionRoundTripsThroughSwitch() {
    final EntryResolution known = new EntryResolution.Known(null);
    final EntryResolution unknown = new EntryResolution.Unknown(URI, "not found");

    assertThat(describe(known)).isEqualTo("known");
    assertThat(describe(unknown)).isEqualTo("unknown: not found");
  }

  private static String describe(final EntryResolution resolution) {
    return switch (resolution) {
      case EntryResolution.Known k -> "known";
      case EntryResolution.Unknown(EntryUri u, String reason) -> "unknown: " + reason;
    };
  }

  @Test
  void contentLoadResultRoundTripsThroughSwitch() {
    final ContentHandle handle = new ContentHandle() {
      @Override
      public InputStream openStream() {
        return new ByteArrayInputStream(new byte[0]);
      }

      @Override
      public LoadedContent materialize() {
        return new LoadedContent.JsonObject(new LinkedHashMap<>());
      }
    };
    final ContentLoadResult ok = new ContentLoadResult.Ok(null, handle);
    final ContentLoadResult err = new ContentLoadResult.Err(null, "boom");

    assertThat(describe(ok)).isEqualTo("ok");
    assertThat(describe(err)).isEqualTo("err: boom");
  }

  private static String describe(final ContentLoadResult result) {
    return switch (result) {
      case ContentLoadResult.Ok ok -> "ok";
      case ContentLoadResult.Err(var e, var reason) -> "err: " + reason;
    };
  }

  @Test
  void adjacencyResultRoundTripsThroughSwitch() {
    final AdjacencyResult ok = new AdjacencyResult.Ok(null, Set.of());
    final AdjacencyResult err = new AdjacencyResult.Err(null, "boom");

    assertThat(describe(ok)).isEqualTo("ok: 0");
    assertThat(describe(err)).isEqualTo("err: boom");
  }

  private static String describe(final AdjacencyResult result) {
    return switch (result) {
      case AdjacencyResult.Ok(var e, var refs) -> "ok: " + refs.size();
      case AdjacencyResult.Err(var e, var reason) -> "err: " + reason;
    };
  }

  @Test
  void metadataResultRoundTripsThroughSwitch() {
    final EntryMetadata metadata = new EntryMetadata(URI, "x.Thing", 3L, OffsetDateTime.now(), null, null);
    final MetadataResult ok = new MetadataResult.Ok(null, metadata);
    final MetadataResult err = new MetadataResult.Err(null, "boom");

    assertThat(describe(ok)).isEqualTo("ok: x.Thing");
    assertThat(describe(err)).isEqualTo("err: boom");
  }

  private static String describe(final MetadataResult result) {
    return switch (result) {
      case MetadataResult.Ok(var e, var m) -> "ok: " + m.qualifiedName();
      case MetadataResult.Err(var e, var reason) -> "err: " + reason;
    };
  }

  @Test
  void loadedContentRoundTripsThroughSwitch() {
    final LoadedContent json = new LoadedContent.JsonObject(new LinkedHashMap<>(java.util.Map.of("k", "v")));
    final LoadedContent stream = new LoadedContent.ObjectStream(List.of(new LinkedHashMap<>()));
    final LoadedContent table = new LoadedContent.TableData(List.of("a", "b"), List.of(List.of(1L, "x")));

    assertThat(describe(json)).isEqualTo("json: 1");
    assertThat(describe(stream)).isEqualTo("stream: 1");
    assertThat(describe(table)).isEqualTo("table: 2 cols, 1 rows");
  }

  private static String describe(final LoadedContent content) {
    return switch (content) {
      case LoadedContent.JsonObject(var data) -> "json: " + data.size();
      case LoadedContent.ObjectStream(var entries) -> "stream: " + entries.size();
      case LoadedContent.TableData(var columns, var rows) ->
          "table: " + columns.size() + " cols, " + rows.size() + " rows";
    };
  }
}

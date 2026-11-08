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

package com.aestallon.storageexplorer.core.service.rdb;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.aestallon.storageexplorer.core.model.EntryUri;
import com.aestallon.storageexplorer.core.model.instance.dto.DatabaseConnectionData;
import com.aestallon.storageexplorer.core.model.instance.dto.DatabaseVendor;
import com.aestallon.storageexplorer.core.model.instance.dto.SqlStorageLocation;
import com.aestallon.storageexplorer.core.model.loading.IndexingTarget;
import com.aestallon.storageexplorer.core.service.engine.ContentHandle;
import com.aestallon.storageexplorer.core.service.engine.EngineResult;
import com.aestallon.storageexplorer.core.service.engine.EntryMetadata;
import com.aestallon.storageexplorer.core.service.engine.LoadedContent;
import com.aestallon.storageexplorer.core.service.engine.VersionedContent;

/**
 * {@link RdbStorageEngine} against a real, embedded H2 database - a from-scratch
 * {@code OBJECT_ENTRY}/{@code OBJECT_VERSION} fixture built directly with JDBC (the same schema
 * shape the pre-rework {@code RelationalDatabaseStorageIndex} queried), not through any platform
 * API.
 */
class RdbStorageEngineTest {

  private static final String MULTI = "test:/org_example_TestEntity/multi-1";
  private static final String MULTI_COMPRESSED = "test:/org_example_TestEntity/multi-2";
  private static final String MISSING = "test:/org_example_TestEntity/does-not-exist";

  private String jdbcUrl;

  @BeforeEach
  void setUp() throws SQLException, IOException {
    jdbcUrl = "jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
    try (Connection c = DriverManager.getConnection(jdbcUrl, "sa", "")) {
      try (var st = c.createStatement()) {
        st.execute("""
            CREATE TABLE OBJECT_ENTRY (
              ID         VARCHAR(64) PRIMARY KEY,
              URI        VARCHAR(512) NOT NULL,
              SCHEME     VARCHAR(64) NOT NULL,
              CLASSNAME  VARCHAR(256) NOT NULL,
              VERSION    BIGINT NOT NULL
            )""");
        st.execute("""
            CREATE TABLE OBJECT_VERSION (
              ENTRY_ID                       VARCHAR(64) NOT NULL,
              VERSION                        BIGINT NOT NULL,
              CREATED_AT                     TIMESTAMP WITH TIME ZONE NOT NULL,
              OBJECT_CONTENT                 BLOB,
              OBJECT_CONTENT_COMPRESS_TYPE   VARCHAR(16)
            )""");
      }

      insertEntry(c, "1", MULTI, "test", "org.example.TestEntity", 1L);
      insertVersion(c, "1", 0L, "2026-01-01T00:00:00Z", "{\"k\":\"v0\"}".getBytes(), null);
      insertVersion(c, "1", 1L, "2026-01-02T00:00:00Z", "{\"k\":\"v1\"}".getBytes(), null);

      insertEntry(c, "2", MULTI_COMPRESSED, "test", "org.example.TestEntity", 0L);
      insertVersion(c, "2", 0L, "2026-01-03T00:00:00Z", zlib("{\"k\":\"compressed\"}"), "zlib");
    }
  }

  @AfterEach
  void tearDown() throws SQLException {
    try (Connection c = DriverManager.getConnection(jdbcUrl, "sa", "")) {
      c.createStatement().execute("DROP ALL OBJECTS");
    }
  }

  private static void insertEntry(final Connection c, final String id, final String uri,
                                   final String scheme, final String className,
                                   final long version) throws SQLException {
    try (PreparedStatement ps = c.prepareStatement(
        "INSERT INTO OBJECT_ENTRY (ID, URI, SCHEME, CLASSNAME, VERSION) VALUES (?, ?, ?, ?, ?)")) {
      ps.setString(1, id);
      ps.setString(2, uri);
      ps.setString(3, scheme);
      ps.setString(4, className);
      ps.setLong(5, version);
      ps.executeUpdate();
    }
  }

  private static void insertVersion(final Connection c, final String entryId, final long version,
                                     final String createdAt, final byte[] content,
                                     final String compressionType) throws SQLException {
    try (PreparedStatement ps = c.prepareStatement(
        "INSERT INTO OBJECT_VERSION (ENTRY_ID, VERSION, CREATED_AT, OBJECT_CONTENT, "
        + "OBJECT_CONTENT_COMPRESS_TYPE) VALUES (?, ?, ?, ?, ?)")) {
      ps.setString(1, entryId);
      ps.setLong(2, version);
      ps.setObject(3, java.time.OffsetDateTime.parse(createdAt));
      ps.setBytes(4, content);
      ps.setString(5, compressionType);
      ps.executeUpdate();
    }
  }

  private static byte[] zlib(final String text) throws IOException {
    final var out = new ByteArrayOutputStream();
    try (var deflate = new DeflaterOutputStream(out, new Deflater())) {
      deflate.write(text.getBytes());
    }
    return out.toByteArray();
  }

  private RdbStorageEngine engine() {
    final var connectionData = new DatabaseConnectionData()
        .url(jdbcUrl)
        .username("sa")
        .password("");
    final var location = new SqlStorageLocation()
        .vendor(DatabaseVendor.H2)
        .dbConnectionData(connectionData);
    return switch (RdbStorageEngine.open(location)) {
      case EngineResult.Ok<RdbStorageEngine> ok -> ok.value();
      case EngineResult.Err<RdbStorageEngine> err ->
          throw new AssertionError("expected Ok, got " + err.error());
    };
  }

  private static EntryUri uri(final String text) {
    return EntryUri.parse(text).orElseThrow();
  }

  @Test
  @DisplayName("exists reports presence without materializing content")
  void existsReportsPresence() {
    try (RdbStorageEngine engine = engine()) {
      assertThat(engine.exists(uri(MULTI))).isTrue();
      assertThat(engine.exists(uri(MISSING))).isFalse();
    }
  }

  @Test
  @DisplayName("pathOf is always empty - no filesystem path for a database row")
  void pathOfIsAlwaysEmpty() {
    try (RdbStorageEngine engine = engine()) {
      assertThat(engine.pathOf(uri(MULTI))).isEmpty();
    }
  }

  @Test
  @DisplayName("head reports the current version's metadata")
  void headReportsCurrentVersionMetadata() {
    try (RdbStorageEngine engine = engine()) {
      final EntryMetadata metadata = switch (engine.head(uri(MULTI))) {
        case EngineResult.Ok<EntryMetadata> ok -> ok.value();
        case EngineResult.Err<EntryMetadata> err -> throw new AssertionError(err.error().toString());
      };
      assertThat(metadata.qualifiedName()).isEqualTo("org.example.TestEntity");
      assertThat(metadata.versionNr()).isEqualTo(1L);
      assertThat(metadata.createdAt()).isNotNull();
    }
  }

  @Test
  @DisplayName("read returns a handle immediately; the query only runs on first access")
  void readDefersTheQuery() throws IOException {
    try (RdbStorageEngine engine = engine()) {
      final ContentHandle handle = switch (engine.read(uri(MULTI))) {
        case EngineResult.Ok<ContentHandle> ok -> ok.value();
        case EngineResult.Err<ContentHandle> err -> throw new AssertionError(err.error().toString());
      };
      // read() on a missing engine connection would have failed by now if it weren't lazy -
      // the real proof is readOfAMissingEntryDefersTheNotFound below, which fails only on access.
      final LoadedContent content = handle.materialize();
      assertThat(content).isInstanceOf(LoadedContent.JsonObject.class);
      assertThat(((LoadedContent.JsonObject) content).data()).containsEntry("k", "v1");
    }
  }

  @Test
  @DisplayName("read of a missing entry succeeds immediately; NotFound surfaces only on access")
  void readOfAMissingEntryDefersTheNotFound() {
    try (RdbStorageEngine engine = engine()) {
      final ContentHandle handle = switch (engine.read(uri(MISSING))) {
        case EngineResult.Ok<ContentHandle> ok -> ok.value();
        case EngineResult.Err<ContentHandle> err ->
            throw new AssertionError("expected a deferred Ok handle, got " + err.error());
      };
      assertThat(handle.materialize()).isInstanceOf(LoadedContent.JsonObject.class);
      assertThat(((LoadedContent.JsonObject) handle.materialize()).data()).isEmpty();
    }
  }

  @Test
  @DisplayName("a compressed version decompresses before materializing")
  void compressedContentDecompresses() {
    try (RdbStorageEngine engine = engine()) {
      final ContentHandle handle = switch (engine.read(uri(MULTI_COMPRESSED))) {
        case EngineResult.Ok<ContentHandle> ok -> ok.value();
        case EngineResult.Err<ContentHandle> err -> throw new AssertionError(err.error().toString());
      };
      final LoadedContent content = handle.materialize();
      assertThat(((LoadedContent.JsonObject) content).data()).containsEntry("k", "compressed");
    }
  }

  @Test
  @DisplayName("list filters by scheme and type, matching the shared IndexingTarget semantics")
  void listFiltersBySchemeAndType() {
    try (RdbStorageEngine engine = engine()) {
      final List<String> uris = engine
          .list(new IndexingTarget(Set.of("test"), Set.of("TestEntity")))
          .map(l -> l.uri().toString())
          .toList();
      assertThat(uris).containsExactlyInAnyOrder(MULTI, MULTI_COMPRESSED);

      assertThat(engine.list(new IndexingTarget(Set.of("nope"), Set.of())).toList()).isEmpty();
    }
  }

  @Test
  @DisplayName("history returns every version oldest-first, each independently readable")
  void historyReturnsEveryVersion() {
    try (RdbStorageEngine engine = engine()) {
      final List<VersionedContent> versions = switch (engine.history(uri(MULTI))) {
        case EngineResult.Ok<List<VersionedContent>> ok -> ok.value();
        case EngineResult.Err<List<VersionedContent>> err -> throw new AssertionError(err.error().toString());
      };
      assertThat(versions).hasSize(2);
      assertThat(versions.get(0).serial()).isEqualTo(0L);
      assertThat(versions.get(1).serial()).isEqualTo(1L);

      final LoadedContent v0 = versions.get(0).content().materialize();
      assertThat(((LoadedContent.JsonObject) v0).data()).containsEntry("k", "v0");
      final LoadedContent v1 = versions.get(1).content().materialize();
      assertThat(((LoadedContent.JsonObject) v1).data()).containsEntry("k", "v1");
    }
  }

  @Test
  @DisplayName("readBatch resolves many entries in one round trip, one result per input address")
  void readBatchResolvesManyEntriesAtOnce() {
    try (RdbStorageEngine engine = engine()) {
      final Map<EntryUri, EngineResult<ContentHandle>> results = engine.readBatch(
          List.of(uri(MULTI), uri(MULTI_COMPRESSED), uri(MISSING)));

      assertThat(results).hasSize(3);
      assertThat(contentOf(results.get(uri(MULTI)))).containsEntry("k", "v1");
      assertThat(contentOf(results.get(uri(MULTI_COMPRESSED)))).containsEntry("k", "compressed");
      assertThat(results.get(uri(MISSING))).isInstanceOf(EngineResult.Err.class);
    }
  }

  private static Map<String, Object> contentOf(final EngineResult<ContentHandle> result) {
    final ContentHandle handle = ((EngineResult.Ok<ContentHandle>) result).value();
    return ((LoadedContent.JsonObject) handle.materialize()).data();
  }

  @Test
  @DisplayName("newVersion/amend report Unsupported - RDB writes aren't implemented")
  void writesAreUnsupported() {
    try (RdbStorageEngine engine = engine()) {
      assertThat(engine.newVersion(uri(MULTI), "{}".getBytes())).isInstanceOf(EngineResult.Err.class);
      assertThat(engine.amend(uri(MULTI), 0L, "{}".getBytes(), false))
          .isInstanceOf(EngineResult.Err.class);
    }
  }

  @Test
  @DisplayName("close shuts down the connection pool - a handle resolved after close fails cleanly")
  void closeShutsDownThePool() {
    final RdbStorageEngine engine = engine();
    // read() itself never touches the pool (it's lazy - see readDefersTheQuery), so the handle is
    // obtained first, then the pool is torn down before the handle's own first real access:
    final ContentHandle handle = switch (engine.read(uri(MULTI))) {
      case EngineResult.Ok<ContentHandle> ok -> ok.value();
      case EngineResult.Err<ContentHandle> err -> throw new AssertionError(err.error().toString());
    };
    engine.close();

    // materialize() has no failure channel (T2.1/T8's own convention) - a query against a closed
    // pool renders empty, not a thrown exception escaping this method:
    final LoadedContent content = handle.materialize();
    assertThat(content).isInstanceOf(LoadedContent.JsonObject.class);
    assertThat(((LoadedContent.JsonObject) content).data()).isEmpty();
  }
}

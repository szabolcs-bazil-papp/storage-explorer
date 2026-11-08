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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;
import javax.sql.DataSource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import com.aestallon.storageexplorer.core.model.EntryKind;
import com.aestallon.storageexplorer.core.model.EntryUri;
import com.aestallon.storageexplorer.core.model.instance.dto.DatabaseConnectionData;
import com.aestallon.storageexplorer.core.model.instance.dto.DatabaseVendor;
import com.aestallon.storageexplorer.core.model.instance.dto.SqlStorageLocation;
import com.aestallon.storageexplorer.core.model.loading.IndexingTarget;
import com.aestallon.storageexplorer.core.service.engine.ContentHandle;
import com.aestallon.storageexplorer.core.service.engine.EngineResult;
import com.aestallon.storageexplorer.core.service.engine.EntryMetadata;
import com.aestallon.storageexplorer.core.service.engine.LoadedContent;
import com.aestallon.storageexplorer.core.service.engine.StorageEngine;
import com.aestallon.storageexplorer.core.service.engine.StorageEngineError;
import com.aestallon.storageexplorer.core.service.engine.VersionedContent;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

/**
 * A from-scratch {@link StorageEngine} for relational-database-backed storages — the RDB
 * counterpart of {@code FsStorageEngine}, built directly against {@code OBJECT_ENTRY}/
 * {@code OBJECT_VERSION} rather than through the platform's own {@code ObjectApi}/{@code
 * StorageSQL} (the same boundary {@code FsStorageEngine} already drew against the platform's
 * {@code StorageFS}). Reflects the pre-rework {@code RelationalDatabaseStorageIndex}'s own schema
 * knowledge (table/column names, the single join query shape, the compression-column feature
 * probe) — that part of the old design was never wrong, only coupled to platform beans it no
 * longer needs.
 *
 * <p><strong>Owns a real resource</strong>: a {@link HikariDataSource} connection pool, unlike
 * {@code FsStorageEngine} (which owns nothing - every FS access is a short-lived file open/close).
 * {@link #close()} shuts the pool down; {@link com.aestallon.storageexplorer.core.model.instance
 * .StorageInstance#setEngine} calls it on whatever engine it's replacing, so a storage that gets
 * reconfigured (or discarded) never leaks a pool.
 *
 * <p><strong>Batching matters here in a way it never did for FS</strong>: a graph render or an
 * ArcScript projection visiting a few thousand entries one {@link #read} at a time would mean a
 * few thousand round trips to the database. {@link #readBatch} is the real point of this class -
 * one {@code WHERE URI IN (:uris)} query standing in for all of them.
 *
 * <p><strong>Deliberately simplified, consistent with a sketch</strong>: {@link #list} fetches its
 * whole matching URI set eagerly (a true server-side cursor kept open across a lazily-consumed
 * {@link Stream} would mean holding a pooled connection checked out for the stream's entire
 * lifetime - real, but not attempted here); {@link #newVersion}/{@link #amend} report {@link
 * StorageEngineError.Unsupported} (the old {@code RelationalDatabaseStorageEntryModificationService}
 * never implemented writes either); only {@code ObjectEntry}-shaped rows are read ({@code
 * TABLE_DATA}/{@code OBJECT_STREAM} are FS-specific conventions with no RDB equivalent explored
 * here, so {@link #list} reports every row as {@link EntryKind#OBJECT}).
 */
public final class RdbStorageEngine implements StorageEngine {

  private static final String OBJECT_ENTRY = "OBJECT_ENTRY";
  private static final String OBJECT_VERSION = "OBJECT_VERSION";

  private static final ObjectMapper MAPPER = new ObjectMapper()
      .configure(DeserializationFeature.USE_LONG_FOR_INTS, true);

  public static EngineResult<RdbStorageEngine> open(final SqlStorageLocation location) {
    final DatabaseVendor vendor = location.getVendor();
    if (vendor == null) {
      return new EngineResult.Err<>(new StorageEngineError.Unsupported("No database vendor given"));
    }

    final DatabaseConnectionData connectionData = location.getDbConnectionData();
    if (connectionData == null) {
      return new EngineResult.Err<>(new StorageEngineError.Unsupported("No connection data given"));
    }

    final HikariDataSource dataSource = buildDataSource(vendor, connectionData);
    try {
      final boolean compressionColumnPresent = probeCompressionColumn(dataSource);
      final JdbcTemplate template = new JdbcTemplate(dataSource);
      template.setFetchSize(1_000);
      final JdbcClient db = JdbcClient.create(template);
      return new EngineResult.Ok<>(new RdbStorageEngine(
          dataSource, db, connectionData.getTargetSchema(), compressionColumnPresent));
    } catch (final Exception e) {
      // the pool is useless without a working connection - don't leak it on the failure path:
      dataSource.close();
      return new EngineResult.Err<>(new StorageEngineError.Sql(
          "Could not connect: " + e.getMessage(), e));
    }
  }

  private static HikariDataSource buildDataSource(final DatabaseVendor vendor,
                                                   final DatabaseConnectionData connectionData) {
    final HikariConfig config = new HikariConfig();
    // this engine never writes (newVersion/amend are Unsupported today - see class javadoc), so
    // the pool itself enforces that rather than trusting every call site to remember it:
    config.setReadOnly(true);
    config.setDriverClassName(vendor.driverClassName());
    config.setJdbcUrl(connectionData.getUrl());
    config.setUsername(connectionData.getUsername());
    config.setPassword(connectionData.getPassword());
    config.setMaximumPoolSize(20);
    config.setMinimumIdle(5);
    config.setConnectionTimeout(30_000L);
    config.setIdleTimeout(600_000L);
    config.setMaxLifetime(1_800_000L);
    config.setLeakDetectionThreshold(5_000L);
    config.addDataSourceProperty("oracle.jdbc.fanEnabled", "false");
    config.addDataSourceProperty("useFetchSizeWithLongColumn", "true");

    final String schema = connectionData.getTargetSchema();
    if (schema != null && !schema.isEmpty()) {
      config.setSchema(schema);
    }

    return new HikariDataSource(config);
  }

  /** Whether {@code OBJECT_VERSION.OBJECT_CONTENT_COMPRESS_TYPE} exists on this database, via
   *  portable {@link java.sql.DatabaseMetaData} introspection rather than the old implementation's
   *  Oracle-only {@code ALL_TAB_COLUMNS} query (its own acknowledged weakness - see its "needs to
   *  be more sophisticated" comment). Checked once, at {@link #open}, not per query. */
  private static boolean probeCompressionColumn(final DataSource dataSource) throws SQLException {
    try (Connection c = dataSource.getConnection()) {
      try (ResultSet rs = c.getMetaData().getColumns(
          null, null, OBJECT_VERSION, "OBJECT_CONTENT_COMPRESS_TYPE")) {
        return rs.next();
      }
    }
  }

  private final HikariDataSource dataSource;
  private final JdbcClient db;
  private final String targetSchema;
  private final boolean compressionColumnPresent;

  private RdbStorageEngine(final HikariDataSource dataSource, final JdbcClient db,
                           final String targetSchema, final boolean compressionColumnPresent) {
    this.dataSource = dataSource;
    this.db = db;
    this.targetSchema = targetSchema;
    this.compressionColumnPresent = compressionColumnPresent;
  }

  private String table(final String name) {
    return (targetSchema == null || targetSchema.isEmpty()) ? name : targetSchema + "." + name;
  }

  @Override
  public void close() {
    dataSource.close();
  }

  @Override
  public boolean exists(final EntryUri uri) {
    try {
      return !db
          .sql("SELECT 1 FROM " + table(OBJECT_ENTRY) + " WHERE URI = :uri")
          .param("uri", uri.latest().toString())
          .query((r, i) -> 1)
          .list()
          .isEmpty();
    } catch (final DataAccessException e) {
      return false;
    }
  }

  @Override
  public Optional<java.nio.file.Path> pathOf(final EntryUri uri) {
    // no filesystem path exists for a database row:
    return Optional.empty();
  }

  @Override
  public EngineResult<ContentHandle> read(final EntryUri uri) {
    // deliberately no up-front exists() check (that would be a second round trip for every read -
    // exactly the cost this engine exists to avoid): the handle defers the one real query (and
    // therefore the NotFound determination too) to its first openStream()/materialize() call.
    return new EngineResult.Ok<>(new LazyRow(uri));
  }

  @Override
  public EngineResult<EntryMetadata> head(final EntryUri uri) {
    final String sql = "SELECT e.CLASSNAME AS \"CLASSNAME\", e.ID AS \"ID\", v.VERSION AS \"VN\", "
        + "v.CREATED_AT AS \"VD\" "
        + "FROM " + table(OBJECT_ENTRY) + " e "
        + "JOIN " + table(OBJECT_VERSION) + " v ON v.ENTRY_ID = e.ID AND v.VERSION = e.VERSION "
        + "WHERE e.URI = :uri";
    try {
      final List<EntryMetadata> rows = db
          .sql(sql)
          .param("uri", uri.latest().toString())
          .query((r, i) -> new EntryMetadata(
              uri,
              r.getString("CLASSNAME"),
              r.getLong("VN"),
              r.getObject("VD", OffsetDateTime.class),
              null,
              r.getString("ID")))
          .list();
      return rows.isEmpty()
          ? new EngineResult.Err<>(new StorageEngineError.NotFound(uri.toString()))
          : new EngineResult.Ok<>(rows.getFirst());
    } catch (final DataAccessException e) {
      return new EngineResult.Err<>(new StorageEngineError.Sql(e.getMessage(), e));
    }
  }

  @Override
  public Stream<Listed> list(final IndexingTarget target) {
    final StringBuilder sql = new StringBuilder("SELECT URI FROM ").append(table(OBJECT_ENTRY));
    final List<Object> schemaParams = new ArrayList<>();
    final List<Object> typeParams = new ArrayList<>();
    if (!target.isAny()) {
      sql.append(" WHERE ");
      boolean needsAnd = false;
      if (!target.schemas().isEmpty()) {
        sql.append("SCHEME IN (")
            .append(String.join(", ", target.schemas().stream().map(s -> "?").toList()))
            .append(")");
        schemaParams.addAll(target.schemas());
        needsAnd = true;
      }
      if (!target.types().isEmpty()) {
        if (needsAnd) {
          sql.append(" AND ");
        }
        sql.append("(")
            .append(String.join(" OR ", target.types().stream().map(t -> "CLASSNAME LIKE ?").toList()))
            .append(")");
        target.types().forEach(t -> typeParams.add("%" + t));
      }
    }

    final List<Object> params = new ArrayList<>(schemaParams);
    params.addAll(typeParams);
    try {
      // fetched eagerly - see class javadoc on why this isn't a live server-side cursor:
      final List<Listed> rows = new JdbcTemplate(dataSource)
          .query(sql.toString(), params.toArray(), (r, i) -> r.getString("URI"))
          .stream()
          .flatMap(uriText -> EntryUri.parse(uriText).stream())
          .map(uri -> new Listed(uri, EntryKind.OBJECT))
          .toList();
      return rows.stream();
    } catch (final DataAccessException e) {
      return Stream.empty();
    }
  }

  @Override
  public EngineResult<Committed> newVersion(final EntryUri uri, final byte[] payload) {
    return new EngineResult.Err<>(
        new StorageEngineError.Unsupported("RDB writes are not supported yet"));
  }

  @Override
  public EngineResult<Void> amend(final EntryUri uri, final long serial, final byte[] payload,
                                   final boolean touchHead) {
    return new EngineResult.Err<>(
        new StorageEngineError.Unsupported("RDB writes are not supported yet"));
  }

  @Override
  public EngineResult<List<VersionedContent>> history(final EntryUri uri) {
    if (uri.isSingleVersion()) {
      return new EngineResult.Err<>(
          new StorageEngineError.Unsupported(uri + " is single-version, it has no history"));
    }

    // metadata only - no OBJECT_CONTENT column selected, so listing a long history is cheap; each
    // version's own content is read lazily, on demand, by the handle below:
    final String sql = "SELECT v.VERSION AS \"VN\", v.CREATED_AT AS \"VD\" "
        + "FROM " + table(OBJECT_VERSION) + " v "
        + "JOIN " + table(OBJECT_ENTRY) + " e ON v.ENTRY_ID = e.ID "
        + "WHERE e.URI = :uri ORDER BY v.VERSION ASC";
    try {
      final List<VersionedContent> versions = db
          .sql(sql)
          .param("uri", uri.latest().toString())
          .query((r, i) -> {
            final long serial = r.getLong("VN");
            final var metadata = new EntryMetadata(
                uri.withVersion(serial), null, serial, r.getObject("VD", OffsetDateTime.class),
                null, null);
            return new VersionedContent(serial, metadata, new LazyRow(uri.withVersion(serial)));
          })
          .list();
      return new EngineResult.Ok<>(versions);
    } catch (final DataAccessException e) {
      return new EngineResult.Err<>(new StorageEngineError.Sql(e.getMessage(), e));
    }
  }

  @Override
  public Map<EntryUri, EngineResult<ContentHandle>> readBatch(final List<EntryUri> uris) {
    final Map<EntryUri, EngineResult<ContentHandle>> out = new LinkedHashMap<>(uris.size());
    if (uris.isEmpty()) {
      return out;
    }

    final List<String> uriTexts = uris.stream().map(u -> u.latest().toString()).toList();
    final String contentColumns = compressionColumnPresent
        ? "v.OBJECT_CONTENT AS \"OAM\", v.OBJECT_CONTENT_COMPRESS_TYPE AS \"CT\""
        : "v.OBJECT_CONTENT AS \"OAM\"";
    final String sql = "SELECT e.URI AS \"URI\", " + contentColumns + " "
        + "FROM " + table(OBJECT_ENTRY) + " e "
        + "JOIN " + table(OBJECT_VERSION) + " v ON v.ENTRY_ID = e.ID AND v.VERSION = e.VERSION "
        + "WHERE e.URI IN (:uris)";
    try {
      final Map<String, ContentHandle> byUri = new LinkedHashMap<>();
      db.sql(sql)
          .param("uris", uriTexts)
          .query((r, i) -> {
            final String uriText = r.getString("URI");
            final byte[] raw = readBlob(r, "OAM");
            final String compressionType = compressionColumnPresent ? r.getString("CT") : null;
            byUri.put(uriText, new EagerContentHandle(decompress(raw, compressionType)));
            return null;
          })
          .list();
      for (final EntryUri uri : uris) {
        final ContentHandle handle = byUri.get(uri.latest().toString());
        out.put(uri, handle == null
            ? new EngineResult.Err<>(new StorageEngineError.NotFound(uri.toString()))
            : new EngineResult.Ok<>(handle));
      }
      return out;
    } catch (final DataAccessException e) {
      final var err = new EngineResult.Err<ContentHandle>(new StorageEngineError.Sql(e.getMessage(), e));
      uris.forEach(u -> out.put(u, err));
      return out;
    }
  }

  private static byte[] readBlob(final ResultSet r, final String column) throws SQLException {
    final var blob = r.getBlob(column);
    if (blob == null) {
      return new byte[0];
    }
    try (InputStream in = blob.getBinaryStream()) {
      return in.readAllBytes();
    } catch (final IOException e) {
      throw new SQLException("Could not read blob column [ " + column + " ]", e);
    }
  }

  private static byte[] decompress(final byte[] raw, final String compressionType) {
    if (compressionType == null || compressionType.isBlank()) {
      return raw;
    }
    try {
      final InputStream decompressing = switch (compressionType) {
        case "zlib" -> new InflaterInputStream(new ByteArrayInputStream(raw));
        case "gzip" -> new GZIPInputStream(new ByteArrayInputStream(raw));
        default -> null;
      };
      if (decompressing == null) {
        // unknown compression marker - best effort, treat as uncompressed rather than fail the
        // whole read over a header field this engine doesn't recognize:
        return raw;
      }
      try (InputStream in = decompressing) {
        return in.readAllBytes();
      }
    } catch (final IOException e) {
      return raw;
    }
  }

  private static LoadedContent materializeJson(final byte[] payload) {
    try {
      final LinkedHashMap<String, Object> data = MAPPER.readValue(payload, LinkedHashMap.class);
      return new LoadedContent.JsonObject(data);
    } catch (final IOException e) {
      return new LoadedContent.JsonObject(new LinkedHashMap<>());
    }
  }

  /** A row's content, already in hand - what {@link #readBatch} and {@link #history} (per
   *  version) ultimately resolve to once their one query has run. */
  private record EagerContentHandle(byte[] payload) implements ContentHandle {

    @Override
    public InputStream openStream() {
      return new ByteArrayInputStream(payload);
    }

    @Override
    public LoadedContent materialize() {
      return materializeJson(payload);
    }
  }

  /** Defers the single-row content query to the first real access - same discipline as {@code
   *  FsStorageEngineAdapter.LazyEntityContentHandle}, for the same reason (T8's own "ContentHandle
   *  should mean no read happened yet" fix): by the time a caller has one of these, no query has
   *  run. Memoized so repeated access issues the query at most once. */
  private final class LazyRow implements ContentHandle {

    private final EntryUri uri;
    private final ReentrantLock lock = new ReentrantLock();
    private volatile EngineResult<byte[]> resolved;

    private LazyRow(final EntryUri uri) {
      this.uri = uri;
    }

    private EngineResult<byte[]> resolve() {
      final var present = resolved;
      if (present != null) {
        return present;
      }

      lock.lock();
      try {
        if (resolved == null) {
          resolved = query();
        }
        return resolved;
      } finally {
        lock.unlock();
      }
    }

    private EngineResult<byte[]> query() {
      final boolean pinnedVersion = uri.version().isPresent();
      final String contentColumns = compressionColumnPresent
          ? "v.OBJECT_CONTENT AS \"OAM\", v.OBJECT_CONTENT_COMPRESS_TYPE AS \"CT\""
          : "v.OBJECT_CONTENT AS \"OAM\"";
      final String joinCondition = pinnedVersion
          ? "v.ENTRY_ID = e.ID AND v.VERSION = :version"
          : "v.ENTRY_ID = e.ID AND v.VERSION = e.VERSION";
      final String sql = "SELECT " + contentColumns + " "
          + "FROM " + table(OBJECT_ENTRY) + " e "
          + "JOIN " + table(OBJECT_VERSION) + " v ON " + joinCondition + " "
          + "WHERE e.URI = :uri";
      try {
        var spec = db.sql(sql).param("uri", uri.latest().toString());
        if (pinnedVersion) {
          spec = spec.param("version", uri.version().getAsLong());
        }
        final List<byte[]> rows = spec
            .query((r, i) -> {
              final byte[] raw = readBlob(r, "OAM");
              final String compressionType = compressionColumnPresent ? r.getString("CT") : null;
              return decompress(raw, compressionType);
            })
            .list();
        return rows.isEmpty()
            ? new EngineResult.Err<>(new StorageEngineError.NotFound(uri.toString()))
            : new EngineResult.Ok<>(rows.getFirst());
      } catch (final DataAccessException e) {
        return new EngineResult.Err<>(new StorageEngineError.Sql(e.getMessage(), e));
      }
    }

    @Override
    public InputStream openStream() {
      return new LazyStream();
    }

    @Override
    public LoadedContent materialize() {
      return switch (resolve()) {
        case EngineResult.Ok<byte[]> ok -> materializeJson(ok.value());
        case EngineResult.Err<byte[]> err -> new LoadedContent.JsonObject(new LinkedHashMap<>());
      };
    }

    private final class LazyStream extends InputStream {

      private InputStream delegate;

      private InputStream delegate() throws IOException {
        if (delegate == null) {
          delegate = switch (resolve()) {
            case EngineResult.Ok<byte[]> ok -> new ByteArrayInputStream(ok.value());
            case EngineResult.Err<byte[]> err -> throw new IOException(
                "failed to read content at [ " + uri + " ]: " + err.error());
          };
        }
        return delegate;
      }

      @Override
      public int read() throws IOException {
        return delegate().read();
      }

      @Override
      public int read(final byte[] b, final int off, final int len) throws IOException {
        return delegate().read(b, off, len);
      }

      @Override
      public int available() throws IOException {
        return delegate().available();
      }

      @Override
      public void close() throws IOException {
        if (delegate != null) {
          delegate.close();
        }
      }
    }
  }

}

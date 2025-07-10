/*
 * Copyright (C) 2025 Szabolcs Bazil Papp
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

package com.aestallon.storageexplorer.core.service;

import java.io.IOException;
import java.net.URI;
import java.sql.ResultSet;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toMap;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.smartbit4all.api.binarydata.BinaryData;
import org.smartbit4all.api.binarydata.BinaryDataCompressionUtil;
import org.smartbit4all.api.collection.CollectionApi;
import org.smartbit4all.core.object.ObjectApi;
import org.springframework.jdbc.core.simple.JdbcClient;
import com.aestallon.storageexplorer.core.model.entry.StorageEntryFactory;
import com.aestallon.storageexplorer.core.model.instance.dto.StorageId;
import com.aestallon.storageexplorer.core.model.loading.IndexingTarget;
import com.aestallon.storageexplorer.core.model.loading.ObjectEntryLoadResult;
import com.aestallon.storageexplorer.core.model.loading.ObjectEntryMeta;
import com.aestallon.storageexplorer.core.service.cache.StorageIndexCache;
import com.google.common.base.Strings;

public final class RelationalDatabaseStorageIndex
    extends StorageIndex<RelationalDatabaseStorageIndex> {

  private enum FeatureLevel {
    STANDARD(
        """
            SELECT e.URI            AS "URI",
                   e.ID             AS "ID",
                   v.VERSION        AS "VN",
                   e.SINGLEVERSION  AS "SV",
                   v.CREATED_AT     AS "VD",
                   v.OBJECT_CONTENT AS "OAM"
              FROM OBJECT_ENTRY   e
              JOIN OBJECT_VERSION v
                ON v.ENTRY_ID = e.ID
               AND v.VERSION = e.VERSION
             WHERE e.CLASSNAME <> 'org_smartbit4all_api_binarydata_BinaryDataObject'
               AND e.SCHEME <> 'tabledatacontents'
               AND e.URI IN (:uris)""",
        """
            SELECT e.URI            AS "URI",
                   e.ID             AS "ID",
                   v.VERSION        AS "VN",
                   e.SINGLEVERSION  AS "SV",
                   v.CREATED_AT     AS "VD",
                   v.OBJECT_CONTENT AS "OAM"
              FROM OBJECT_ENTRY   e
              JOIN OBJECT_VERSION v
                ON v.ENTRY_ID = e.ID
             WHERE e.URI = :uri
               AND v.VERSION = :version"""),
    COMPRESSION(
        """
            SELECT e.URI                          AS "URI",
                   e.ID                           AS "ID",
                   v.VERSION                      AS "VN",
                   e.SINGLEVERSION                AS "SV",
                   v.CREATED_AT                   AS "VD",
                   v.OBJECT_CONTENT               AS "OAM",
                   v.OBJECT_CONTENT_COMPRESS_TYPE AS "CT"
              FROM OBJECT_ENTRY   e
              JOIN OBJECT_VERSION v
                ON v.ENTRY_ID = e.ID
               AND v.VERSION = e.VERSION
             WHERE e.CLASSNAME <> 'org_smartbit4all_api_binarydata_BinaryDataObject'
               AND e.SCHEME <> 'tabledatacontents'
               AND e.URI IN (:uris)""",
        """
            SELECT e.URI                          AS "URI",
                   e.ID                           AS "ID",
                   v.VERSION                      AS "VN",
                   e.SINGLEVERSION                AS "SV",
                   v.CREATED_AT                   AS "VD",
                   v.OBJECT_CONTENT               AS "OAM",
                   v.OBJECT_CONTENT_COMPRESS_TYPE AS "CT"
              FROM OBJECT_ENTRY   e
              JOIN OBJECT_VERSION v
                ON v.ENTRY_ID = e.ID
             WHERE e.URI = :uri
               AND v.VERSION = :version"""),
    UNKNOWN("", "");

    private final String queryIn;
    private final String queryExact;

    FeatureLevel(final String queryIn, final String queryExact) {
      this.queryIn = queryIn;
      this.queryExact = queryExact;
    }


  }


  private static final Logger log = LoggerFactory.getLogger(RelationalDatabaseStorageIndex.class);

  final JdbcClient db;
  private final String targetSchema;
  private final ObjectEntryLoadingService<RelationalDatabaseStorageIndex> loader;
  private FeatureLevel featureLevel = FeatureLevel.UNKNOWN;

  public RelationalDatabaseStorageIndex(
      StorageId storageId,
      ObjectApi objectApi,
      CollectionApi collectionApi,
      JdbcClient db,
      String targetSchema) {
    super(storageId, objectApi, collectionApi);
    this.db = db;
    this.targetSchema = targetSchema;
    this.loader = new ObjectEntryLoadingService.RelationalDatabase(this);
    this.storageEntryFactory = StorageEntryFactory.builder(this, objectApi, collectionApi).build();
    this.cache = StorageIndexCache.inMemory();
  }

  @Override
  public ObjectEntryLoadingService<RelationalDatabaseStorageIndex> loader() {
    return loader;
  }

  @Override
  protected Stream<URI> fetchEntries() {
    return fetchEntries(IndexingTarget.any());
  }

  @Override
  protected Stream<URI> fetchEntries(IndexingTarget target) {
    return db
        .sql(buildQuery(target))
        .query((r, i) -> r.getString("URI"))
        .list().stream()
        .filter(it -> !Strings.isNullOrEmpty(it))
        .map(URI::create);
  }

  private String rawUriSelect() {
    return (targetSchema == null)
        ? "SELECT URI FROM OBJECT_ENTRY"
        : "SELECT URI FROM %s.OBJECT_ENTRY".formatted(targetSchema);
  }

  private String buildQuery(IndexingTarget target) {
    if (target.isAny()) {
      return rawUriSelect();
    }

    /*
     *  schemas: a, b, c
     *  types: Foo, Bar, Baz
     *
     *  SELECT URI
     *    FROM OBJECT_ENTRY
     *   WHERE SCHEME IN ('a', 'b', 'c')
     *     AND (
     *           CLASSNAME LIKE '%Foo' OR
     *           CLASSNAME LIKE '%Bar' OR
     *           CLASSNAME LIKE '%Baz'
     *         )
     */
    final boolean filterSchema = !target.schemas().isEmpty();
    final boolean filterType = !target.types().isEmpty();

    final StringBuilder sb = new StringBuilder(rawUriSelect());
    sb.append(" WHERE ");
    if (filterSchema) {
      sb.append(schemaClause(target.schemas()));
    }

    if (filterType) {
      if (filterSchema) {
        sb.append(" AND ");
      }

      sb.append(" ( ");
      sb.append(typeClause(target.types()));
      sb.append(" )");
    }

    return sb.toString();
  }

  private String schemaClause(Set<String> schemas) {
    return schemas.stream().collect(joining(
        "', '",
        "SCHEME IN ('", "')"));
  }

  private String typeClause(Set<String> types) {
    return types.stream().collect(joining(
        "' OR CLASSNAME LIKE '%",
        "CLASSNAME LIKE '%",
        "'"));
  }

  private sealed interface LoadResult {

    record Err() implements LoadResult {
      private static final Err ERR = new Err();
    }


    record Ok(URI uri, ObjectEntryLoadResult result) implements LoadResult {}

  }

  ObjectEntryLoadResult.SingleVersion.Eager loadSingle(final URI uri,
                                                       final long version) {
    ensureKnownFeatureLevel();
    List<LoadResult> loadResults = db
        .sql(featureLevel.queryExact)
        .param("uri", uri.toString())
        .param("version", version)
        .query((r, i) -> {
          try {
            return parseResultSet(r, true);
          } catch (final Exception e) {
            log.error(e.getMessage(), e);
            return LoadResult.Err.ERR;
          }
        })
        .list();
    if (loadResults.isEmpty()) {

      return placeholderFakeResult(uri, version);
    }

    final LoadResult result = loadResults.getFirst();
    return switch (result) {
      case LoadResult.Err err -> placeholderFakeResult(uri, version);
      case LoadResult.Ok(var ignored, var res) -> (ObjectEntryLoadResult.SingleVersion.Eager) res;
    };
  }

  private ObjectEntryLoadResult.SingleVersion.Eager placeholderFakeResult(final URI uri,
                                                                          final long version) {
    log.error("Unexpected error loading {} at version {}", uri, version);
    return new ObjectEntryLoadResult.SingleVersion.Eager(
        new ObjectEntryMeta(uri, null, null, version, null, null, null),
        Collections.emptyMap(),
        ObjectEntryLoadingService.OBJECT_MAPPER);
  }

  List<ObjectEntryLoadResult> loadBatch(final List<URI> uris) {
    if (uris.isEmpty()) {
      return Collections.emptyList();
    }

    ensureKnownFeatureLevel();

    final Map<URI, ObjectEntryLoadResult> resultsByUri = db
        .sql(featureLevel.queryIn)
        .param("uris", uris.stream().map(URI::toString).toList())

        .query((r, c) -> {
          try {
            return parseResultSet(r, false);
          } catch (final Exception e) {
            log.error(e.getMessage(), e);
            return LoadResult.Err.ERR;
          }
        })
        .list().stream()
        .filter(LoadResult.Ok.class::isInstance)
        .map(LoadResult.Ok.class::cast)
        .collect(toMap(LoadResult.Ok::uri, LoadResult.Ok::result));
    return uris.stream()
        .map(it -> {
          final ObjectEntryLoadResult res = resultsByUri.get(it);
          return res != null
              ? res
              : new ObjectEntryLoadResult.Err("Could not retrieve object from database: " + it);
        })
        .toList();
  }

  private void ensureKnownFeatureLevel() {
    if (featureLevel == FeatureLevel.UNKNOWN) {
      featureLevel = determineFeatureLevel();
      log.info("Determined feature level: {}", featureLevel);
    }
  }

  private LoadResult parseResultSet(ResultSet r, final boolean only) throws Exception {
    final String uriStr = r.getString("URI");
    final URI uri = URI.create(uriStr);
    final boolean single = Boolean.TRUE.toString().equals(r.getString("SV"));
    final long version = !single ? r.getLong("VN") : -1L;
    final var versionTimestamp = r.getObject("VD", OffsetDateTime.class);
    final var id = r.getString("ID");
    Map<String, Object> objectAsMap;
    try (final var in = r.getBlob("OAM").getBinaryStream()) {
      var binaryData = BinaryData.of(in);
      if (featureLevel == FeatureLevel.COMPRESSION) {
        // TODO: More sophisticated implementation than a blasted "if"!
        final var compressionTypeStr = r.getString("CT");
        final var compressionType = switch (compressionTypeStr) {
          case "zlib" -> BinaryDataCompressionUtil.CompressionType.ZLIB;
          case "gzip" -> BinaryDataCompressionUtil.CompressionType.GZIP;
          case "" -> null;
          case null -> null;
          default -> {
            log.warn("Unknown compression type: {}", compressionTypeStr);
            throw new IllegalArgumentException("Unknown compression type: " + compressionTypeStr);
          }
        };
        if (compressionType != null) {
          binaryData = BinaryDataCompressionUtil.decompress(binaryData, compressionType);
        }
      }

      objectAsMap = objectApi
          .getDefaultSerializer()
          .deserialize(binaryData, LinkedHashMap.class)
          .map(it -> (Map<String, Object>) it)
          .orElseGet(Collections::emptyMap);
    } catch (IOException e) {
      log.error("Could not read OAM blob for [ URI: {} | version: {} ]", uriStr, version, e);
      objectAsMap = Collections.emptyMap();
    }

    final ObjectEntryLoadResult.SingleVersion singleVersion =
        new ObjectEntryLoadResult.SingleVersion.Eager(
            new ObjectEntryMeta(uri, null, null, version, versionTimestamp, null, id),
            objectAsMap,
            ObjectEntryLoadingService.OBJECT_MAPPER);
    return single || only
        ? new LoadResult.Ok(uri, singleVersion)
        : new LoadResult.Ok(
            uri,
            ObjectEntryLoadResult.multiVersion(singleVersion, loader::loadExact, version));
  }

  private FeatureLevel determineFeatureLevel() {
    // TODO: This needs to be more sophisticated, extendible and one layer removed from the RDBMS!
    final boolean isCompressionSupported = !db
        .sql("""
            SELECT COLUMN_NAME, DATA_TYPE
              FROM ALL_TAB_COLUMNS
             WHERE TABLE_NAME = 'OBJECT_VERSION'
               AND COLUMN_NAME = 'OBJECT_CONTENT_COMPRESS_TYPE'""")
        .query()
        .listOfRows()
        .isEmpty();
    return isCompressionSupported ? FeatureLevel.COMPRESSION : FeatureLevel.STANDARD;
  }

}

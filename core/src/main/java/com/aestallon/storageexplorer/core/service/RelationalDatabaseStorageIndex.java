package com.aestallon.storageexplorer.core.service;

import java.io.IOException;
import java.net.URI;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toMap;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.smartbit4all.api.binarydata.BinaryData;
import org.smartbit4all.api.collection.CollectionApi;
import org.smartbit4all.core.object.ObjectApi;
import org.springframework.jdbc.core.JdbcTemplate;
import com.aestallon.storageexplorer.core.model.entry.StorageEntryFactory;
import com.aestallon.storageexplorer.core.model.instance.dto.StorageId;
import com.aestallon.storageexplorer.core.model.loading.IndexingTarget;
import com.aestallon.storageexplorer.core.model.loading.ObjectEntryLoadResult;
import com.aestallon.storageexplorer.core.model.loading.ObjectEntryMeta;
import com.google.common.base.Strings;

public final class RelationalDatabaseStorageIndex
    extends StorageIndex<RelationalDatabaseStorageIndex> {

  private static final Logger log = LoggerFactory.getLogger(RelationalDatabaseStorageIndex.class);

  private static final int BATCH_SIZE = 20;
  private static final String BATCH_QUERY = """
      SELECT e.URI            AS "URI",
             v.VERSION        AS "VN",
             e.SINGLEVERSION  AS "SV",
             v.CREATED_AT     AS "VD",
             v.OBJECT_CONTENT AS "OAM"
        FROM OBJECT_ENTRY   e
        JOIN OBJECT_VERSION v
          ON v.ENTRY_ID = e.ID
       WHERE v.VERSION  = (SELECT MAX(VERSION)
                              FROM OBJECT_VERSION
                             WHERE ENTRY_ID = v.ENTRY_ID)
         AND "URI" IN (%s)""".formatted(IntStream.range(0, BATCH_SIZE)
      .mapToObj(i -> "?")
      .collect(joining(", ")));

  private final JdbcTemplate db;
  private final String targetSchema;
  private final ObjectEntryLoadingService<RelationalDatabaseStorageIndex> loader;

  public RelationalDatabaseStorageIndex(
      StorageId storageId,
      ObjectApi objectApi,
      CollectionApi collectionApi,
      JdbcTemplate db,
      String targetSchema) {
    super(storageId, objectApi, collectionApi);
    this.db = db;
    this.targetSchema = targetSchema;
    this.loader = new ObjectEntryLoadingService.RelationalDatabase(this);
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
        .query(buildQuery(target), (r, i) -> r.getString("URI"))
        .stream()
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

  @Override
  protected StorageEntryFactory storageEntryFactory() {
    return StorageEntryFactory.builder(this, objectApi, collectionApi)
        .build();
  }

  private sealed interface LoadResult {

    record Err() implements LoadResult {
      private static final Err ERR = new Err();
    }


    record Ok(URI uri, ObjectEntryLoadResult result) implements LoadResult {}

  }

  List<ObjectEntryLoadResult> loadBatch(final List<URI> uris) {
    if (uris.isEmpty()) {
      return Collections.emptyList();
    }
    Map<URI, ObjectEntryLoadResult> resultsByUri = new HashMap<>();
    for (int i = 0; i < uris.size() / BATCH_SIZE + 1; i++) {
      final int fro = i * BATCH_SIZE;
      final int to = Math.min(fro + BATCH_SIZE, uris.size());
      final var batchResult = db.queryForStream(
              con -> {
                final PreparedStatement pStmt = con.prepareStatement(BATCH_QUERY);
                int idx = 1;
                for (int j = fro; j < to; j++) {
                  pStmt.setString(idx++, uris.get(j).toString());
                }

                while (idx <= BATCH_SIZE) {
                  pStmt.setNull(idx++, Types.VARCHAR);
                }
                return pStmt;
              },
              (r, c) -> {
                try {
                  return parseResultSet(r);
                } catch (final Exception e) {
                  log.error(e.getMessage(), e);
                  return LoadResult.Err.ERR;
                }
              })
          .filter(LoadResult.Ok.class::isInstance)
          .map(LoadResult.Ok.class::cast)
          .collect(toMap(LoadResult.Ok::uri, LoadResult.Ok::result));
      resultsByUri.putAll(batchResult);
    }
    return uris.stream()
        .map(it -> {
          final ObjectEntryLoadResult res = resultsByUri.get(it);
          return res != null
              ? res
              : new ObjectEntryLoadResult.Err("Could not retrieve object from database: " + it);
        })
        .toList();
  }

  private LoadResult parseResultSet(ResultSet r) throws Exception {
    final String uriStr = r.getString("URI");
    final URI uri = URI.create(uriStr);
    final boolean single = Boolean.TRUE.toString().equals(r.getString("SV"));
    final long version = single ? r.getLong("VN") : -1L;
    final var versionTimestamp = r.getObject("VD", OffsetDateTime.class);
    Map<String, Object> objectAsMap;
    try (final var in = r.getBlob("OAM").getBinaryStream()) {
      final var binaryData = BinaryData.of(in);
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
            new ObjectEntryMeta(uri, null, null, version, versionTimestamp, null),
            objectAsMap,
            ObjectEntryLoadingService.OBJECT_MAPPER);
    return single
        ? new LoadResult.Ok(uri, singleVersion)
        : new LoadResult.Ok(
            uri,
            new ObjectEntryLoadResult.MultiVersion(Collections.singletonList(singleVersion)));
  }

}

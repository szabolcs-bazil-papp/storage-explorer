package com.aestallon.storageexplorer.core.service;

import java.net.URI;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.smartbit4all.api.collection.CollectionApi;
import org.smartbit4all.core.object.ObjectApi;
import org.springframework.jdbc.core.JdbcTemplate;
import com.aestallon.storageexplorer.core.model.loading.IndexingTarget;
import com.google.common.base.Strings;
import com.aestallon.storageexplorer.core.model.entry.StorageEntryFactory;
import com.aestallon.storageexplorer.core.model.instance.dto.StorageId;
import static java.util.stream.Collectors.joining;

public final class RelationalDatabaseStorageIndex extends StorageIndex {

  private static final Logger log = LoggerFactory.getLogger(RelationalDatabaseStorageIndex.class);

  private final JdbcTemplate db;
  private final String targetSchema;

  public RelationalDatabaseStorageIndex(
      StorageId storageId,
      ObjectApi objectApi,
      CollectionApi collectionApi,
      JdbcTemplate db,
      String targetSchema) {
    super(storageId, objectApi, collectionApi);
    this.db = db;
    this.targetSchema = targetSchema;
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
    return StorageEntryFactory.builder(storageId, objectApi, collectionApi)
        .build();
  }

}

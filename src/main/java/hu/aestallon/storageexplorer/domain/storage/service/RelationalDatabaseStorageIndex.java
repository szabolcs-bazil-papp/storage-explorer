package hu.aestallon.storageexplorer.domain.storage.service;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.smartbit4all.api.collection.CollectionApi;
import org.smartbit4all.core.object.ObjectApi;
import org.springframework.jdbc.core.JdbcTemplate;
import hu.aestallon.storageexplorer.domain.storage.model.instance.dto.StorageId;

public final class RelationalDatabaseStorageIndex extends StorageIndex {

  private static final Logger log = LoggerFactory.getLogger(RelationalDatabaseStorageIndex.class);
  
  private final JdbcTemplate db;

  RelationalDatabaseStorageIndex(
      StorageId storageId,
      ObjectApi objectApi,
      CollectionApi collectionApi,
      JdbcTemplate db) {
    super(storageId, objectApi, collectionApi);
    this.db = db;
  }

  @Override
  void refresh() {
    final List<Integer> count = db.query(
        "SELECT COUNT(*) AS \"COUNT\" FROM OBJECT_ENTRY;", 
        (r, i) -> r.getInt("COUNT"));
    log.info("Hello world! Object count: {}", count);
  }
}

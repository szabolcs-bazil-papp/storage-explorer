package hu.aestallon.storageexplorer.domain.storage.service;

import java.net.URI;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.smartbit4all.api.collection.CollectionApi;
import org.smartbit4all.core.object.ObjectApi;
import org.springframework.jdbc.core.JdbcTemplate;
import com.google.common.base.Strings;
import hu.aestallon.storageexplorer.domain.storage.model.entry.StorageEntryFactory;
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
  protected Stream<URI> fetchEntries() {
    return db
        .query("SELECT URI FROM OBJECT_ENTRY", (r, i) -> r.getString("URI"))
        .stream()
        .filter(it -> !Strings.isNullOrEmpty(it))
        .map(URI::create);
  }

  @Override
  protected StorageEntryFactory storageEntryFactory() {
    return StorageEntryFactory.builder(storageId, objectApi, collectionApi)
        .build();
  }

}

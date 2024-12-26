package hu.aestallon.storageexplorer.spring.service.impl;

import java.util.UUID;
import org.smartbit4all.api.collection.CollectionApi;
import org.smartbit4all.core.object.ObjectApi;
import org.springframework.jdbc.core.JdbcTemplate;
import hu.aestallon.storageexplorer.spring.service.StorageIndexProvider;
import hu.aestallon.storageexplorer.storage.model.instance.dto.StorageId;
import hu.aestallon.storageexplorer.storage.service.RelationalDatabaseStorageIndex;
import hu.aestallon.storageexplorer.storage.service.StorageIndex;

public class RelationalDatabaseStorageIndexProvider implements StorageIndexProvider {
  private final StorageId id = new StorageId(UUID.randomUUID());

  private final ObjectApi objectApi;
  private final CollectionApi collectionApi;
  private final JdbcTemplate jdbcTemplate;

  public RelationalDatabaseStorageIndexProvider(final ObjectApi objectApi,
                                                final CollectionApi collectionApi,
                                                final JdbcTemplate jdbcTemplate) {
    this.objectApi = objectApi;
    this.collectionApi = collectionApi;
    this.jdbcTemplate = jdbcTemplate;
  }


  @Override
  public StorageIndex provide() {
    return new RelationalDatabaseStorageIndex(id, objectApi, collectionApi, jdbcTemplate);
  }
}

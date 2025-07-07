package com.aestallon.storageexplorer.spring.service.impl;

import java.util.UUID;
import org.smartbit4all.api.collection.CollectionApi;
import org.smartbit4all.core.object.ObjectApi;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import com.aestallon.storageexplorer.spring.service.StorageIndexProvider;
import com.aestallon.storageexplorer.core.model.instance.dto.StorageId;
import com.aestallon.storageexplorer.core.service.RelationalDatabaseStorageIndex;
import com.aestallon.storageexplorer.core.service.StorageIndex;

public class RelationalDatabaseStorageIndexProvider implements StorageIndexProvider {
  private final StorageId id = new StorageId(UUID.randomUUID());

  private final ObjectApi objectApi;
  private final CollectionApi collectionApi;
  private final JdbcClient jdbcClient;

  public RelationalDatabaseStorageIndexProvider(final ObjectApi objectApi,
                                                final CollectionApi collectionApi,
                                                final JdbcClient jdbcClient) {
    this.objectApi = objectApi;
    this.collectionApi = collectionApi;
    this.jdbcClient = jdbcClient;
  }


  @Override
  public StorageIndex<?> provide() {
    return new RelationalDatabaseStorageIndex(id, objectApi, collectionApi, jdbcClient, null);
  }
}

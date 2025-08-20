package com.aestallon.storageexplorer.spring.service.impl;

import java.util.UUID;
import org.smartbit4all.api.collection.CollectionApi;
import org.smartbit4all.core.object.ObjectApi;
import org.springframework.jdbc.core.simple.JdbcClient;
import com.aestallon.storageexplorer.core.model.instance.dto.StorageId;
import com.aestallon.storageexplorer.core.service.RelationalDatabaseStorageIndex;
import com.aestallon.storageexplorer.core.service.StorageIndex;
import com.aestallon.storageexplorer.spring.service.StorageIndexProvider;

public class RelationalDatabaseStorageIndexProvider implements StorageIndexProvider {
  private final StorageId id = new StorageId(UUID.randomUUID());

  private final ObjectApi objectApi;
  private final CollectionApi collectionApi;
  private final JdbcClient jdbcClient;
  private final boolean trustPlatformBeans;

  public RelationalDatabaseStorageIndexProvider(final ObjectApi objectApi,
                                                final CollectionApi collectionApi,
                                                final JdbcClient jdbcClient,
                                                final boolean trustPlatformBeans) {
    this.objectApi = objectApi;
    this.collectionApi = collectionApi;
    this.jdbcClient = jdbcClient;
    this.trustPlatformBeans = trustPlatformBeans;
  }


  @Override
  public StorageIndex<?> provide() {
    return new RelationalDatabaseStorageIndex(
        id, objectApi, collectionApi, jdbcClient, null, trustPlatformBeans);
  }
  
}

package com.aestallon.storageexplorer.springstarter;

import org.smartbit4all.api.collection.CollectionApi;
import org.smartbit4all.core.object.ObjectApi;
import org.smartbit4all.storage.fs.StorageFS;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.aestallon.storageexplorer.spring.StorageExplorerProperties;
import com.aestallon.storageexplorer.spring.service.StorageIndexProvider;
import com.aestallon.storageexplorer.spring.service.StorageIndexService;
import com.aestallon.storageexplorer.spring.service.impl.FileSystemStorageIndexProvider;

/**
 * RDB-backed storages have no auto-configuration here anymore — the old {@code
 * RelationalDatabaseStorageIndexProvider}/{@code RelationalDatabaseConfiguration} pair (platform
 * {@code ObjectApi}/{@code CollectionApi}/{@code JdbcClient}-based) was deleted along with {@code
 * core}'s {@code RelationalDatabaseStorageIndex} (Milestone 4, T4.2) — {@code RdbStorageEngine}
 * doesn't exist yet. A host application wanting RDB support today must supply its own {@link
 * StorageIndexProvider} bean.
 */
@AutoConfiguration
@ConditionalOnBean({ ObjectApi.class, CollectionApi.class })
@EnableConfigurationProperties(StorageExplorerProperties.class)
public class EmbeddedStorageExplorerAutoConfiguration {

  private final StorageExplorerProperties properties;

  public EmbeddedStorageExplorerAutoConfiguration(StorageExplorerProperties properties) {
    this.properties = properties;
  }

  @Configuration(proxyBeanMethods = false)
  @ConditionalOnBean({ ObjectApi.class, CollectionApi.class, StorageFS.class })
  static class FileSystemConfiguration {

    @Bean
    @ConditionalOnMissingBean(StorageIndexProvider.class)
    public StorageIndexProvider fileSystemStorageIndexProvider(StorageFS storageFS) {
      return new FileSystemStorageIndexProvider(storageFS.getRootFolder().toPath().toAbsolutePath());
    }

    @Bean
    @ConditionalOnMissingBean(StorageIndexService.class)
    public StorageIndexService storageIndexService(StorageIndexProvider storageIndexProvider) {
      return new StorageIndexService(storageIndexProvider);
    }

  }

}

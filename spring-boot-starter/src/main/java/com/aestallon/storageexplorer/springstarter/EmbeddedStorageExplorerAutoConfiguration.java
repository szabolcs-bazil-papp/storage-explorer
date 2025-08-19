package com.aestallon.storageexplorer.springstarter;

import java.nio.file.Path;
import org.smartbit4all.api.collection.CollectionApi;
import org.smartbit4all.core.object.ObjectApi;
import org.smartbit4all.sql.storage.StorageSQL;
import org.smartbit4all.storage.fs.StorageFS;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import com.aestallon.storageexplorer.spring.StorageExplorerProperties;
import com.aestallon.storageexplorer.spring.service.StorageIndexProvider;
import com.aestallon.storageexplorer.spring.service.StorageIndexService;
import com.aestallon.storageexplorer.spring.service.impl.FileSystemStorageIndexProvider;
import com.aestallon.storageexplorer.spring.service.impl.RelationalDatabaseStorageIndexProvider;

@AutoConfiguration
@ConditionalOnBean({ ObjectApi.class, CollectionApi.class })
@EnableConfigurationProperties(StorageExplorerProperties.class)
public class EmbeddedStorageExplorerAutoConfiguration {

  private final StorageExplorerProperties properties;

  public EmbeddedStorageExplorerAutoConfiguration(StorageExplorerProperties properties) {
    this.properties = properties;
  }

  @Configuration(proxyBeanMethods = false)
  @ConditionalOnProperty(name = "fs.base.directory")
  @ConditionalOnBean({ ObjectApi.class, CollectionApi.class, StorageFS.class })
  static class FileSystemConfiguration {

    private final StorageExplorerProperties properties;

    public FileSystemConfiguration(StorageExplorerProperties properties) {
      this.properties = properties;
    }

    @Bean
    @ConditionalOnMissingBean(StorageIndexProvider.class)
    public StorageIndexProvider fileSystemStorageIndexProvider(
        ObjectApi objectApi,
        CollectionApi collectionApi,
        @Value("${fs.base.directory}") String fsBaseDirectory) {
      return new FileSystemStorageIndexProvider(
          objectApi, collectionApi, Path.of(fsBaseDirectory),
          properties.getSettings().getTrustPlatformBeans());
    }

    @Bean
    @ConditionalOnMissingBean(StorageIndexService.class)
    public StorageIndexService storageIndexService(StorageIndexProvider storageIndexProvider) {
      return new StorageIndexService(storageIndexProvider);
    }

  }


  @Configuration(proxyBeanMethods = false)
  @ConditionalOnBean({ ObjectApi.class, CollectionApi.class, JdbcTemplate.class, StorageSQL.class })
  static class RelationalDatabaseConfiguration {

    private final StorageExplorerProperties properties;

    public RelationalDatabaseConfiguration(StorageExplorerProperties properties) {
      this.properties = properties;
    }

    @Bean
    @ConditionalOnMissingBean({ JdbcClient.class })
    public JdbcClient jdbcClient(JdbcTemplate jdbcTemplate) {
      return JdbcClient.create(jdbcTemplate);
    }

    @Bean
    @ConditionalOnMissingBean(StorageIndexProvider.class)
    public StorageIndexProvider relationalDatabaseStorageIndexProvider(
        ObjectApi objectApi,
        CollectionApi collectionApi,
        JdbcClient jdbcClient) {
      return new RelationalDatabaseStorageIndexProvider(
          objectApi, collectionApi, jdbcClient,
          properties.getSettings().getTrustPlatformBeans());
    }

    @Bean
    @ConditionalOnMissingBean(StorageIndexService.class)
    public StorageIndexService storageIndexService(StorageIndexProvider storageIndexProvider) {
      return new StorageIndexService(storageIndexProvider);
    }

  }

}

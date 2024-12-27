package com.aestallon.storageexplorer.springstarter;

import java.nio.file.Path;
import org.smartbit4all.api.collection.CollectionApi;
import org.smartbit4all.core.object.ObjectApi;
import org.smartbit4all.sql.storage.StorageSQL;
import org.smartbit4all.storage.fs.StorageFS;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.web.servlet.DispatcherServletAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import com.aestallon.storageexplorer.spring.rest.api.ExplorerApiController;
import com.aestallon.storageexplorer.spring.rest.api.ExplorerApiDelegate;
import com.aestallon.storageexplorer.spring.rest.impl.ExplorerApiDelegateImpl;
import com.aestallon.storageexplorer.spring.service.StorageIndexProvider;
import com.aestallon.storageexplorer.spring.service.StorageIndexService;
import com.aestallon.storageexplorer.spring.service.impl.FileSystemStorageIndexProvider;
import com.aestallon.storageexplorer.spring.service.impl.RelationalDatabaseStorageIndexProvider;

@Configuration(proxyBeanMethods = false)
@ConditionalOnBean(ObjectApi.class)
@AutoConfigureAfter(DispatcherServletAutoConfiguration.class)
public class EmbeddedStorageExplorerAutoConfiguration {


  @Configuration(proxyBeanMethods = false)
  @ConditionalOnClass(StorageFS.class)
  @ConditionalOnProperty(name = "fs.base.directory")
  @ConditionalOnBean({ ObjectApi.class, CollectionApi.class })
  static class FileSystemConfiguration {

    @Bean
    @ConditionalOnMissingBean(StorageIndexProvider.class)
    public StorageIndexProvider fileSystemStorageIndexProvider(
        ObjectApi objectApi,
        CollectionApi collectionApi,
        @Value("${fs.base.directory}") String fsBaseDirectory) {
      return new FileSystemStorageIndexProvider(objectApi, collectionApi, Path.of(fsBaseDirectory));
    }

    @Bean
    @ConditionalOnMissingBean(StorageIndexService.class)
    public StorageIndexService storageIndexService(StorageIndexProvider storageIndexProvider) {
      return new StorageIndexService(storageIndexProvider);
    }

    @Bean
    public ExplorerApiDelegate explorerApiDelegate(StorageIndexService storageIndexService) {
      return new ExplorerApiDelegateImpl(storageIndexService);
    }

    @Bean
    public ExplorerApiController explorerApiController(ExplorerApiDelegate explorerApiDelegate) {
      return new ExplorerApiController(explorerApiDelegate);
    }
  }


  @Configuration(proxyBeanMethods = false)
  @ConditionalOnClass({ StorageSQL.class, JdbcTemplate.class })
  @ConditionalOnBean({ ObjectApi.class, CollectionApi.class, JdbcTemplate.class })
  static class RelationalDatabaseConfiguration {

    @Bean
    @ConditionalOnMissingBean(StorageIndexProvider.class)
    public StorageIndexProvider relationalDatabaseStorageIndexProvider(
        ObjectApi objectApi,
        CollectionApi collectionApi,
        JdbcTemplate jdbcTemplate) {
      return new RelationalDatabaseStorageIndexProvider(objectApi, collectionApi, jdbcTemplate);
    }

    @Bean
    @ConditionalOnMissingBean(StorageIndexService.class)
    public StorageIndexService storageIndexService(StorageIndexProvider storageIndexProvider) {
      return new StorageIndexService(storageIndexProvider);
    }

    @Bean
    public ExplorerApiDelegate explorerApiDelegate(StorageIndexService storageIndexService) {
      return new ExplorerApiDelegateImpl(storageIndexService);
    }

    @Bean
    public ExplorerApiController explorerApiController(ExplorerApiDelegate explorerApiDelegate) {
      return new ExplorerApiController(explorerApiDelegate);
    }
  }

}

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
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import com.aestallon.storageexplorer.spring.StorageExplorerProperties;
import com.aestallon.storageexplorer.spring.rest.api.ExplorerApiController;
import com.aestallon.storageexplorer.spring.rest.api.ExplorerApiDelegate;
import com.aestallon.storageexplorer.spring.rest.impl.ExplorerApiDelegateImpl;
import com.aestallon.storageexplorer.spring.service.StorageIndexProvider;
import com.aestallon.storageexplorer.spring.service.StorageIndexService;
import com.aestallon.storageexplorer.spring.service.impl.FileSystemStorageIndexProvider;
import com.aestallon.storageexplorer.spring.service.impl.RelationalDatabaseStorageIndexProvider;
import com.aestallon.storageexplorer.spring.servlet.SpaServlet;

@AutoConfiguration
@ConditionalOnBean(ObjectApi.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(StorageExplorerProperties.class)
@ConditionalOnProperty("storage-explorer.api-path")
public class EmbeddedStorageExplorerAutoConfiguration {

  private final StorageExplorerProperties properties;

  public EmbeddedStorageExplorerAutoConfiguration(StorageExplorerProperties properties) {
    this.properties = properties;
  }

  @Configuration(proxyBeanMethods = false)
  @ConditionalOnProperty(name = "fs.base.directory")
  @ConditionalOnBean({ ObjectApi.class, CollectionApi.class, StorageFS.class })
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
  @ConditionalOnBean({ ObjectApi.class, CollectionApi.class, JdbcTemplate.class, StorageSQL.class })
  static class RelationalDatabaseConfiguration {

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
      return new RelationalDatabaseStorageIndexProvider(objectApi, collectionApi, jdbcClient);
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


  @Bean
  @ConditionalOnProperty(prefix = "storage-explorer", name = "ui-enabled", havingValue = "true")
  ServletRegistrationBean<SpaServlet> webStorageExplorer() {
    final String contextPath =
        properties.getUiPath() + (properties.getUiPath().endsWith("/") ? "*" : "/*");
    final String uiContextPath = properties.getUiPath().endsWith("/")
        ? properties.getUiPath().substring(0, properties.getUiPath().length())
        : properties.getUiPath();
    final String apiPath = properties.getApiPath();
    final boolean allowOthers = properties.getSettings().getWebAllowOthers();
    return new ServletRegistrationBean<>(
        new SpaServlet(allowOthers, uiContextPath, apiPath),
        contextPath);
  }

}

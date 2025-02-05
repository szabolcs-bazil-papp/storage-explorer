package com.aestallon.storageexplorer.core.service;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.smartbit4all.api.collection.CollectionApi;
import org.smartbit4all.api.config.PlatformApiConfig;
import org.smartbit4all.core.object.ObjectApi;
import org.smartbit4all.core.object.ObjectDefinitionApi;
import org.smartbit4all.domain.data.storage.ObjectStorage;
import org.smartbit4all.domain.meta.EntityConfiguration;
import org.smartbit4all.domain.service.identifier.IdentifierService;
import org.smartbit4all.sql.config.SQLConfig;
import org.smartbit4all.sql.config.SQLDBParameter;
import org.smartbit4all.sql.config.SQLDBParameterH2;
import org.smartbit4all.sql.config.SQLDBParameterOracle;
import org.smartbit4all.sql.config.SQLDBParameterPostgres;
import org.smartbit4all.sql.config.SQLObjectStorageEntityConfiguration;
import org.smartbit4all.sql.service.identifier.SQLCurrentIdentifierOracle;
import org.smartbit4all.sql.service.identifier.SQLCurrentIdentifierPg;
import org.smartbit4all.sql.service.identifier.SQLIdentifierService;
import org.smartbit4all.sql.service.identifier.SQLIdentifierServiceH2;
import org.smartbit4all.sql.service.identifier.SQLNextIdentifierOracle;
import org.smartbit4all.sql.service.identifier.SQLNextIdentifierPg;
import org.smartbit4all.sql.storage.StorageSQL;
import org.smartbit4all.storage.fs.StorageFS;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import com.aestallon.storageexplorer.common.util.NotImplementedException;
import com.aestallon.storageexplorer.core.model.instance.dto.Availability;
import com.aestallon.storageexplorer.core.model.instance.dto.DatabaseConnectionData;
import com.aestallon.storageexplorer.core.model.instance.dto.DatabaseVendor;
import com.aestallon.storageexplorer.core.model.instance.dto.FsStorageLocation;
import com.aestallon.storageexplorer.core.model.instance.dto.SqlStorageLocation;
import com.aestallon.storageexplorer.core.model.instance.dto.StorageId;
import com.aestallon.storageexplorer.core.model.instance.dto.StorageLocation;

final class StorageIndexFactory {

  static abstract sealed class StorageIndexCreationResult permits
      StorageIndexCreationResult.Ok,
      StorageIndexCreationResult.Err {

    protected final Availability availability;

    protected StorageIndexCreationResult(final Availability availability) {
      this.availability = availability;
    }

    static final class Ok extends StorageIndexCreationResult {

      private final StorageIndex storageIndex;
      private final AnnotationConfigApplicationContext springContext;

      Ok(final StorageIndex storageIndex, AnnotationConfigApplicationContext springContext) {
        super(Availability.AVAILABLE);
        this.storageIndex = storageIndex;
        this.springContext = springContext;
      }

      StorageIndex storageIndex() {
        return storageIndex;
      }

      AnnotationConfigApplicationContext springContext() {
        return springContext;
      }

    }


    static final class Err extends StorageIndexCreationResult {

      private final String errorMessage;

      Err(final Availability availability, final String errorMessage) {
        super(availability);
        this.errorMessage = errorMessage;
      }

      String errorMessage() {
        return errorMessage;
      }

    }

  }

  static StorageIndexFactory of(StorageId storageId) {
    return new StorageIndexFactory(storageId);
  }

  private final StorageId storageId;

  StorageIndexFactory(StorageId storageId) {
    this.storageId = Objects.requireNonNull(storageId, "storageId cannot be null!");
  }

  StorageIndexCreationResult create(final StorageLocation storageLocation) {
    final ClassLoader original = Thread.currentThread().getContextClassLoader();
    final ClassLoader swap = getClass().getClassLoader();
    Thread.currentThread().setContextClassLoader(swap);
    try {

      return switch (storageLocation) {
        case FsStorageLocation fs -> createFs(fs);
        case SqlStorageLocation sql -> createDb(sql);
      };

    } catch (final Exception e) {
      return new StorageIndexCreationResult.Err(Availability.UNAVAILABLE, e.getMessage());
    } finally {
      Thread.currentThread().setContextClassLoader(original);
    }
  }

  private Map<String, Object> defaultProps() {
    final Map<String, Object> props = new HashMap<>();
    props.put("applicationruntime.maintain.enabled", "false");
    props.put("invocationregistry.refresh.enabled", "false");
    props.put("application.setup.enabled", "false");
    return props;
  }

  private StorageIndexCreationResult createFs(final FsStorageLocation fsStorageLocation) {
    final Path path = fsStorageLocation.getPath().toAbsolutePath();
    final var ctx = new AnnotationConfigApplicationContext();
    ctx.register(PlatformApiConfig.class);
    ctx.registerBean(storageId.toString(), ObjectStorage.class, () -> new StorageFS(
        path.toFile(),
        ctx.getBean(ObjectDefinitionApi.class)));

    final ConfigurableEnvironment env = ctx.getEnvironment();
    env.getPropertySources().addFirst(new MapPropertySource("default", defaultProps()));

    ctx.refresh();

    final ObjectApi objectApi = ctx.getBean(ObjectApi.class);
    final CollectionApi collectionApi = ctx.getBean(CollectionApi.class);

    final var index = new FileSystemStorageIndex(storageId, objectApi, collectionApi, path);
    return new StorageIndexCreationResult.Ok(index, ctx);
  }


  private StorageIndexCreationResult createDb(final SqlStorageLocation sqlStorageLocation) {
    final Map<String, Object> props = defaultProps();
    final DatabaseVendor vendor = sqlStorageLocation.getVendor();
    if (vendor == null) {
      // we don't even need the vendor, the DriverManager can figure it out from the URL...
      // but we are going to need the vendor later, when we dynamically download drivers, and not
      // ship all of them...
      return new StorageIndexCreationResult.Err(Availability.MISCONFIGURED, "No DB Vendor!");
    }

    final String driverClassName = vendor.driverClassName();
    props.put("spring.datasource.driver-class-name", driverClassName);

    final DatabaseConnectionData connectionData = sqlStorageLocation.getDbConnectionData();
    if (connectionData == null) {
      return new StorageIndexCreationResult.Err(Availability.MISCONFIGURED, "No Connection Data!");
    }
    props.putAll(connectionData.asProperties());
    final String targetSchema = connectionData.getTargetSchema();

    final var ctx = new AnnotationConfigApplicationContext();

    ctx.register(SQLConfig.class);
    ctx.register(SQLObjectStorageEntityConfiguration.class);

    ctx.registerBean("dataSource", DataSource.class, getDataSourceFactory(vendor, connectionData));
    ctx.registerBean("jdbcTemplate", JdbcTemplate.class, getJdbcTemplateFactory(ctx),
        it -> it.setDependsOn("dataSource"));
    ctx.registerBean(
        "sqlDbParameter",
        SQLDBParameter.class,
        getSqlDBParameterFactory(ctx, vendor, targetSchema), it -> it.setDependsOn("jdbcTemplate"));
    ctx.registerBean(
        "identifierService",
        IdentifierService.class,
        getIdentifierServiceFactory(ctx, vendor), it -> it.setDependsOn("jdbcTemplate"));
    ctx.registerBean(
        "defaultStorage",
        ObjectStorage.class,
        () -> {
          final var storage = new StorageSQL(ctx.getBean(ObjectDefinitionApi.class));
          new EntityConfiguration().setupEntityDefinitions(ctx);
          return storage;
        },
        it -> it.setDependsOn("objectDefinitionApi", "jdbcTemplate"));

    final ConfigurableEnvironment env = ctx.getEnvironment();
    env.getPropertySources().addFirst(new MapPropertySource("default", props));

    ctx.refresh();

    final ObjectApi objectApi = ctx.getBean(ObjectApi.class);
    final CollectionApi collectionApi = ctx.getBean(CollectionApi.class);
    final JdbcTemplate jdbcTemplate = ctx.getBean(JdbcTemplate.class);

    
    final var index = new RelationalDatabaseStorageIndex(
        storageId,
        objectApi,
        collectionApi,
        jdbcTemplate,
        targetSchema);
    return new StorageIndexCreationResult.Ok(index, ctx);
  }

  private Supplier<SQLDBParameter> getSqlDBParameterFactory(final ApplicationContext ctx,
                                                            final DatabaseVendor vendor,
                                                            final String targetSchema) {
    return switch (vendor) {
      case ORACLE -> () -> {
        final var p = new SQLDBParameterOracle();
        if (targetSchema != null && !targetSchema.isEmpty()) {
          p.setSchema(targetSchema);
        }
        
        return p;
      };
      case H2 -> SQLDBParameterH2::new;
      case PG -> SQLDBParameterPostgres::new;
      default -> throw new NotImplementedException("Unsupported database vendor: " + vendor);
    };
  }

  private Supplier<IdentifierService> getIdentifierServiceFactory(final ApplicationContext ctx,
                                                                  final DatabaseVendor vendor) {
    return switch (vendor) {
      case ORACLE -> () -> {
        final JdbcTemplate jdbcTemplate = ctx.getBean(JdbcTemplate.class);
        return new SQLIdentifierService(
            jdbcTemplate,
            () -> new SQLNextIdentifierOracle(jdbcTemplate),
            () -> new SQLCurrentIdentifierOracle(jdbcTemplate));
      };
      case H2 -> () -> new SQLIdentifierServiceH2(ctx.getBean(JdbcTemplate.class));
      case PG -> () -> {
        final JdbcTemplate jdbcTemplate = ctx.getBean(JdbcTemplate.class);
        return new SQLIdentifierService(
            jdbcTemplate,
            () -> new SQLNextIdentifierPg(jdbcTemplate),
            () -> new SQLCurrentIdentifierPg(jdbcTemplate));
      };
      default -> throw new NotImplementedException("Unsupported database vendor: " + vendor);
    };
  }

  private Supplier<DataSource> getDataSourceFactory(final DatabaseVendor vendor,
                                                    final DatabaseConnectionData connectionData) {
    return () -> {
      final var dataSource = new DriverManagerDataSource();
      dataSource.setDriverClassName(vendor.driverClassName());
      dataSource.setUrl(connectionData.getUrl());
      dataSource.setUsername(connectionData.getUsername());
      dataSource.setPassword(connectionData.getPassword());
      
      final var schema = connectionData.getTargetSchema();
      if (schema != null && !schema.isEmpty()) {
        dataSource.setSchema(schema);
      }
      return dataSource;
    };
  }

  private Supplier<JdbcTemplate> getJdbcTemplateFactory(final ApplicationContext ctx) {
    return () -> new JdbcTemplate(ctx.getBean(DataSource.class));
  }

}

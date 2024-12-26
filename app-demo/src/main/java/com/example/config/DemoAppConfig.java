package com.example.config;

import java.nio.file.Path;
import java.util.List;
import org.smartbit4all.api.config.PlatformApiConfig;
import org.smartbit4all.api.org.OrgApi;
import org.smartbit4all.api.org.OrgApiStorageImpl;
import org.smartbit4all.api.org.SecurityOption;
import org.smartbit4all.api.session.SessionApi;
import org.smartbit4all.api.session.SessionManagementApi;
import org.smartbit4all.api.session.restserver.config.SessionSrvRestConfig;
import org.smartbit4all.api.view.restserver.config.ViewSrvRestConfig;
import org.smartbit4all.core.object.ObjectDefinitionApi;
import org.smartbit4all.core.utility.FinalReference;
import org.smartbit4all.domain.data.storage.ObjectStorage;
import org.smartbit4all.domain.data.storage.StorageApi;
import org.smartbit4all.sec.jwt.JwtSessionRequestFilter;
import org.smartbit4all.sec.jwt.JwtUtil;
import org.smartbit4all.sec.session.SessionApiImpl;
import org.smartbit4all.sec.session.SessionManagementApiImpl;
import org.smartbit4all.sec.token.SessionTokenHandler;
import org.smartbit4all.sec.token.SessionTokenHandlerJWT;
import org.smartbit4all.storage.fs.StorageFS;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import({
    PlatformApiConfig.class,
    ViewSrvRestConfig.class,
    SessionSrvRestConfig.class
})
public class DemoAppConfig {
  @Value("${fs.base.directory:./dev-fs}")
  private String fsBaseDirectory;
  
  
  @Bean
  ObjectStorage objectStorage(ObjectDefinitionApi objectDefinitionApi) {
    return new StorageFS(Path.of(fsBaseDirectory).toFile(), objectDefinitionApi);
  }
  
  @Bean
  OrgApi orgApi(StorageApi storageApi, List<SecurityOption> securityOptions) throws Exception {
    return new OrgApiStorageImpl(storageApi, securityOptions);
  }
  
  @Bean
  SessionManagementApi sessionManagementApi() {
    final var api = new SessionManagementApiImpl();
    api.setDefaultLocaleProvider(() -> "hu");
    return api;
  }
  
  @Bean
  SessionApi sessionApi() {
    return new SessionApiImpl();
  }
  
  @Bean
  SessionTokenHandler sessionTokenHandler() {
    return new SessionTokenHandlerJWT();
  }

  @Bean
  JwtUtil jwtUtil() {
    return new JwtUtil();
  }
  
  @Bean
  JwtSessionRequestFilter jwtSessionRequestFilter() {
    return new JwtSessionRequestFilter();
  }
}

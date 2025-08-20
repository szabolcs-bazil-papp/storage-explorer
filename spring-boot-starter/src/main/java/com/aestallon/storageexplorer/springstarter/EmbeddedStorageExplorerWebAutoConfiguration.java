/*
 * Copyright (C) 2025 Szabolcs Bazil Papp
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along with this program.
 * If not, see <http://www.gnu.org/licenses/>.
 */

package com.aestallon.storageexplorer.springstarter;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servletapi.SecurityContextHolderAwareRequestFilter;
import com.aestallon.storageexplorer.spring.StorageExplorerProperties;
import com.aestallon.storageexplorer.spring.auth.AuthService;
import com.aestallon.storageexplorer.spring.auth.impl.BasicAuthServiceImpl;
import com.aestallon.storageexplorer.spring.auth.impl.DefaultExplorerApiOncePerRequestFilter;
import com.aestallon.storageexplorer.spring.rest.api.ExplorerApiController;
import com.aestallon.storageexplorer.spring.rest.api.ExplorerApiDelegate;
import com.aestallon.storageexplorer.spring.rest.impl.ExplorerApiDelegateImpl;
import com.aestallon.storageexplorer.spring.service.StorageIndexService;
import com.aestallon.storageexplorer.spring.servlet.SpaServlet;

@AutoConfiguration(after = EmbeddedStorageExplorerAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(StorageExplorerProperties.class)
@ConditionalOnBean(StorageIndexService.class)
@ConditionalOnProperty(prefix = "storage-explorer", name = "api-path")
public class EmbeddedStorageExplorerWebAutoConfiguration {

  private final StorageExplorerProperties properties;

  public EmbeddedStorageExplorerWebAutoConfiguration(StorageExplorerProperties properties) {
    this.properties = properties;
  }

  @Bean
  @ConditionalOnMissingBean(AuthService.class)
  public AuthService authService() {
    return new BasicAuthServiceImpl(properties.getUsername(), properties.getPassword());
  }

  @Bean
  @ConditionalOnMissingBean(ExplorerApiOncePerRequestFilterFactory.class)
  public ExplorerApiOncePerRequestFilterFactory explorerApiOncePerRequestFilter(
      AuthService authService) {
    return () -> new DefaultExplorerApiOncePerRequestFilter(authService);
  }



  @ConditionalOnClass({ SecurityFilterChain.class, HttpSecurity.class })
  @Configuration(proxyBeanMethods = false)
  static class SecurityConfiguration {

    @Bean("storageExplorerSecurityFilterChain")
    @Order(100)
    @ConditionalOnBean({ SecurityFilterChain.class })
    public SecurityFilterChain storageExplorerSecurityFilterChain(HttpSecurity http,
                                                                  ExplorerApiOncePerRequestFilterFactory filterFactory,
                                                                  StorageExplorerProperties storageExplorerProperties)
        throws Exception {
      return http
          .securityMatcher(storageExplorerProperties.getApiPath() + "/**")
          .csrf(AbstractHttpConfigurer::disable)
          .cors(Customizer.withDefaults())
          .authorizeHttpRequests(it -> it
              .requestMatchers(storageExplorerProperties.getApiPath() + "/verify").permitAll()
              .anyRequest().authenticated())
          .addFilterBefore(
              filterFactory.create(),
              SecurityContextHolderAwareRequestFilter.class)
          .build();
    }


  }

  @Bean
  public ExplorerApiDelegate explorerApiDelegate(StorageIndexService storageIndexService,
                                                 AuthService authService) {
    return new ExplorerApiDelegateImpl(storageIndexService, authService);
  }

  @Bean
  public ExplorerApiController explorerApiController(ExplorerApiDelegate explorerApiDelegate) {
    return new ExplorerApiController(explorerApiDelegate);
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

/*
 * Copyright (C) 2024 it4all Hungary Kft.
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

package hu.aestallon.storageexplorer.domain.userconfig.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.ObjectMapper;
import hu.aestallon.storageexplorer.domain.userconfig.model.UserConfig;

@Service
public class UserConfigService {

  private static final Logger log = LoggerFactory.getLogger(UserConfigService.class);

  private final String configFileLocation;
  private final ApplicationEventPublisher eventPublisher;
  private final ObjectMapper objectMapper;

  private final AtomicReference<UserConfig> userConfig;

  public UserConfigService(@Value("${config.file.location:./app.cfg}") String configFileLocation,
                           ApplicationEventPublisher eventPublisher, ObjectMapper objectMapper) {
    this.configFileLocation = configFileLocation;
    this.eventPublisher = eventPublisher;
    this.objectMapper = objectMapper;
    userConfig = new AtomicReference<>(init());
  }

  private UserConfig init() {
    final Path path = Path.of(configFileLocation);
    if (!Files.exists(path)) {
      log.warn(
          "User configuration file does not yet exist at [ {} ], falling back to default values!",
          path);
      return new UserConfig();
    }
    try (final var in = Files.newInputStream(path)) {
      return objectMapper.readerFor(UserConfig.class).readValue(in);
    } catch (IOException e) {
      log.error(
          "Could not read user configuration from [ {} ], falling back to default values!",
          path);
      log.error(e.getMessage(), e);
      return new UserConfig();
    }
  }

  public UserConfig get() {
    return userConfig.get();
  }

}

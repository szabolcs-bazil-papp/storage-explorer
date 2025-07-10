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

package com.aestallon.storageexplorer.client.userconfig.service;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class UserConfigPersistenceService {

  private static final Logger log = LoggerFactory.getLogger(UserConfigPersistenceService.class);

  public static final String SETTINGS_FOLDER = System.getProperty("java.io.tmpdir")
                                               + FileSystems.getDefault().getSeparator()
                                               + "storage-explorer";

  private static boolean createSettingsDirectory(Path path) {
    try {
      Files.createDirectories(path);
      return true;
    } catch (IOException e) {
      log.error("Cannot create missing settings directory at [ {} ]", path);
      log.error(e.getMessage(), e);
      return false;
    }
  }

  private final ObjectMapper objectMapper;
  private final Path settingsFolder;

  public UserConfigPersistenceService(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
    settingsFolder = Path.of(SETTINGS_FOLDER);
  }

  Path settingsFolder() {
    return settingsFolder;
  }

  <T> T readSettingsAt(String settingsFilename, TypeReference<T> type,
                       Supplier<T> fallback) {
    if (!Files.exists(settingsFolder)) {
      log.debug("Reading: Creating settings folder at [ {} ]...", settingsFolder);
      createSettingsDirectory(settingsFolder);
    }

    final Path settingsFile = settingsFolder.resolve(settingsFilename);
    try (final var in = Files.newInputStream(settingsFile)) {
      return objectMapper.readerFor(type).readValue(in);
    } catch (IOException e) {
      log.error(
          "Could not read user configuration from [ {} ], falling back to default values!",
          settingsFile);
      return fallback.get();
    }
  }

  <T> void writeSettingsTo(String settingsFilename, T settings) {
    if (!Files.exists(settingsFolder)) {
      log.debug("Writing: Creating settings folder at [ {} ]...", settingsFolder);
      if (!createSettingsDirectory(settingsFolder)) {
        return;
      }
    }
    final Path settingsFile = settingsFolder.resolve(settingsFilename);
    try (final var out = Files.newOutputStream(settingsFile)) {
      objectMapper.writerWithDefaultPrettyPrinter().writeValue(out, settings);
    } catch (IOException e) {
      log.error("Could not write settings to [ {} ]!", settingsFile);
      log.error(e.getMessage(), e);
    }
  }

}

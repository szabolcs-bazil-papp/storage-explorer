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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import com.aestallon.storageexplorer.client.userconfig.event.GraphConfigChanged;
import com.aestallon.storageexplorer.client.userconfig.event.KeymapChanged;
import com.aestallon.storageexplorer.client.userconfig.model.GraphSettings;
import com.aestallon.storageexplorer.client.userconfig.model.Keymap;
import com.aestallon.storageexplorer.client.userconfig.model.StorageLocationSettings;
import com.aestallon.storageexplorer.core.model.instance.dto.StorageInstanceDto;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class UserConfigService {

  private static final Logger log = LoggerFactory.getLogger(UserConfigService.class);

  public static final String SETTINGS_FOLDER = System.getProperty("java.io.tmpdir")
                                               + FileSystems.getDefault().getSeparator()
                                               + "storage-explorer";

  public static final String STORAGE_SETTINGS = "storage.settings";
  public static final String KEYMAP_SETTINGS = "keymap.settings";

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

  private final String settingsFolder;
  private final ApplicationEventPublisher eventPublisher;
  private final ObjectMapper objectMapper;

  private final AtomicReference<GraphSettings> graphSettings;
  private final AtomicReference<StorageLocationSettings> storageLocationSettings;
  private final AtomicReference<Map<String, Keymap>> keymapSettings;

  public UserConfigService(ApplicationEventPublisher eventPublisher, ObjectMapper objectMapper) {
    this.settingsFolder = SETTINGS_FOLDER;
    this.eventPublisher = eventPublisher;
    this.objectMapper = objectMapper;
    graphSettings = new AtomicReference<>(readSettingsAt(
        "graph.settings",
        new TypeReference<>() {},
        GraphSettings::new));
    storageLocationSettings = new AtomicReference<>(readSettingsAt(
        STORAGE_SETTINGS,
        new TypeReference<>() {},
        StorageLocationSettings::new));
    keymapSettings = new AtomicReference<>(readSettingsAt(
        KEYMAP_SETTINGS,
        new TypeReference<>() {},
        Keymap::defaultKeymaps));
  }

  private <T> T readSettingsAt(String settingsFilename, TypeReference<T> type,
                               Supplier<T> fallback) {
    final Path settingsFolderPath = Path.of(this.settingsFolder);
    if (!Files.exists(settingsFolderPath)) {
      log.debug("Reading: Creating settings folder at [ {} ]...", settingsFolderPath);
      createSettingsDirectory(settingsFolderPath);
    }

    final Path settingsFile = settingsFolderPath.resolve(settingsFilename);
    try (final var in = Files.newInputStream(settingsFile)) {
      return objectMapper.readerFor(type).readValue(in);
    } catch (IOException e) {
      log.error(
          "Could not read user configuration from [ {} ], falling back to default values!",
          settingsFile);
      return fallback.get();
    }
  }

  private <T> void writeSettingsTo(String settingsFilename, T settings) {
    final Path settingsFolderPath = Path.of(this.settingsFolder);
    if (!Files.exists(settingsFolderPath)) {
      log.debug("Writing: Creating settings folder at [ {} ]...", settingsFolderPath);
      if (!createSettingsDirectory(settingsFolderPath)) {
        return;
      }
    }
    final Path settingsFile = settingsFolderPath.resolve(settingsFilename);
    try (final var out = Files.newOutputStream(settingsFile)) {
      objectMapper.writerWithDefaultPrettyPrinter().writeValue(out, settings);
    } catch (IOException e) {
      log.error("Could not write settings to [ {} ]!", settingsFile);
      log.error(e.getMessage(), e);
    }
  }


  public GraphSettings graphSettings() {
    return graphSettings.get();
  }

  public StorageLocationSettings storageLocationSettings() {
    return storageLocationSettings.get();
  }

  public Map<String, Keymap> keymapSettings() {
    return new HashMap<>(keymapSettings.get());
  }

  public void addStorageLocation(final StorageInstanceDto storageInstanceDto) {
    final var settings = storageLocationSettings
        .updateAndGet(it -> it.addImportedStorageLocationsItem(storageInstanceDto));
    writeSettingsTo(STORAGE_SETTINGS, settings);
  }

  public void removeStorageLocation(final StorageInstanceDto storageInstanceDto) {
    final var settings = storageLocationSettings.updateAndGet(it -> {
      it.getImportedStorageLocations()
          .removeIf(e -> Objects.equals(e.getId(), storageInstanceDto.getId()));
      return it;
    });
    writeSettingsTo(STORAGE_SETTINGS, settings);
  }

  public void updateStorageLocation(final StorageInstanceDto storageInstanceDto) {
    final var settings = storageLocationSettings.updateAndGet(it -> {
      int idx = -1;
      List<StorageInstanceDto> importedStorageLocations = it.getImportedStorageLocations();
      for (int i = 0; i < importedStorageLocations.size(); i++) {
        final var dto = importedStorageLocations.get(i);
        if (Objects.equals(dto.getId(), storageInstanceDto.getId())) {
          idx = i;
          break;
        }
      }
      if (idx >= 0) {
        importedStorageLocations.set(idx, storageInstanceDto);
      }
      return it;
    });
    writeSettingsTo(STORAGE_SETTINGS, settings);
  }

  public void updateGraphSettings(GraphSettings graphSettings) {
    final var baseline = graphSettings();
    if (baseline.equals(graphSettings)) {
      return;
    }

    this.graphSettings.set(graphSettings);
    writeSettingsTo("graph.settings", graphSettings);
    eventPublisher.publishEvent(new GraphConfigChanged());
  }

  public ArcScriptFileService arcScriptFileService() {
    return new ArcScriptFileService(Path.of(settingsFolder));
  }

  public void updateKeymapSettings(Map<String, Keymap> keymapSettings) {
    final var baseline = keymapSettings();
    if (baseline.equals(keymapSettings)) {
      return;
    }

    this.keymapSettings.set(keymapSettings);
    writeSettingsTo(KEYMAP_SETTINGS, keymapSettings);
    eventPublisher.publishEvent(new KeymapChanged());
  }

}

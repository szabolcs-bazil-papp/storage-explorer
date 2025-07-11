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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import com.aestallon.storageexplorer.client.userconfig.event.GraphConfigChanged;
import com.aestallon.storageexplorer.client.userconfig.event.KeymapChanged;
import com.aestallon.storageexplorer.client.userconfig.model.GraphSettings;
import com.aestallon.storageexplorer.client.userconfig.model.Keymap;
import com.aestallon.storageexplorer.client.userconfig.model.StorageLocationSettings;
import com.aestallon.storageexplorer.core.model.instance.dto.StorageId;
import com.aestallon.storageexplorer.core.model.instance.dto.StorageInstanceDto;
import com.fasterxml.jackson.core.type.TypeReference;

@Service
public class UserConfigService {

  private static final Logger log = LoggerFactory.getLogger(UserConfigService.class);

  public static final String GRAPH_SETTINGS = "graph.settings";
  public static final String STORAGE_SETTINGS = "storage.settings";
  public static final String KEYMAP_SETTINGS = "keymap.settings";
  public static final String MISC_STATE = "misc.state";

  private static final String MOST_RECENT_STORAGE_INSTANCE_LOAD = "mostRecentStorageInstanceLoad";

  private final UserConfigPersistenceService persistenceService;
  private final ApplicationEventPublisher eventPublisher;

  private final AtomicReference<GraphSettings> graphSettings;
  private final AtomicReference<StorageLocationSettings> storageLocationSettings;
  private final AtomicReference<Map<String, Keymap>> keymapSettings;
  private final AtomicReference<Map<String, String>> miscState;

  public UserConfigService(UserConfigPersistenceService persistenceService,
                           ApplicationEventPublisher eventPublisher) {
    this.persistenceService = persistenceService;
    this.eventPublisher = eventPublisher;
    graphSettings = new AtomicReference<>(persistenceService.readSettingsAt(
        GRAPH_SETTINGS,
        new TypeReference<>() {},
        GraphSettings::new));
    storageLocationSettings = new AtomicReference<>(persistenceService.readSettingsAt(
        STORAGE_SETTINGS,
        new TypeReference<>() {},
        StorageLocationSettings::new));
    keymapSettings = new AtomicReference<>(persistenceService.readSettingsAt(
        KEYMAP_SETTINGS,
        new TypeReference<>() {},
        Keymap::defaultKeymaps));
    miscState = new AtomicReference<>(persistenceService.readSettingsAt(
        MISC_STATE,
        new TypeReference<>() {},
        HashMap::new));
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
    persistenceService.writeSettingsTo(STORAGE_SETTINGS, settings);
  }

  public void removeStorageLocation(final StorageInstanceDto storageInstanceDto) {
    final var settings = storageLocationSettings.updateAndGet(it -> {
      it.getImportedStorageLocations()
          .removeIf(e -> Objects.equals(e.getId(), storageInstanceDto.getId()));
      return it;
    });
    persistenceService.writeSettingsTo(STORAGE_SETTINGS, settings);
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
    persistenceService.writeSettingsTo(STORAGE_SETTINGS, settings);
  }

  public void updateGraphSettings(GraphSettings graphSettings) {
    final var baseline = graphSettings();
    if (baseline.equals(graphSettings)) {
      return;
    }

    this.graphSettings.set(graphSettings);
    persistenceService.writeSettingsTo(GRAPH_SETTINGS, graphSettings);
    eventPublisher.publishEvent(new GraphConfigChanged());
  }

  public ArcScriptFileService arcScriptFileService() {
    return new ArcScriptFileService(persistenceService.settingsFolder());
  }

  public void updateKeymapSettings(Map<String, Keymap> keymapSettings) {
    final var baseline = keymapSettings();
    if (baseline.equals(keymapSettings)) {
      return;
    }

    this.keymapSettings.set(keymapSettings);
    persistenceService.writeSettingsTo(KEYMAP_SETTINGS, keymapSettings);
    eventPublisher.publishEvent(new KeymapChanged());
  }
  
  public void setMostRecentStorageInstanceLoad(StorageId storageId) {
    final var miscStateMap = miscState.updateAndGet(it -> {
      it.put(MOST_RECENT_STORAGE_INSTANCE_LOAD, storageId.toString());
      return it;
    });
    persistenceService.writeSettingsTo(MISC_STATE, miscStateMap);
  }
  
  public Optional<StorageId> getMostRecentStorageInstanceLoad() {
    return Optional.ofNullable(miscState.get().get(MOST_RECENT_STORAGE_INSTANCE_LOAD))
        .map(UUID::fromString)
        .map(StorageId::new);
  }

}

/*
 * Copyright (C) 2026 Szabolcs Bazil Papp
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

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.aestallon.storageexplorer.arcscript.engine.QueryEngineSettings;
import com.aestallon.storageexplorer.client.userconfig.event.QueryEngineSettingsChanged;
import static org.assertj.core.api.Assertions.assertThat;

class ArcScriptEngineSettingsServiceTest {

  @Test
  void settingsFallBackToDefaults_whenNothingWasPersistedYet(@TempDir java.nio.file.Path tempDir) {
    // given
    final var service = newService(tempDir, new ArrayList<>());

    // when
    final var settings = service.settings();

    // then
    assertThat(settings).isEqualTo(QueryEngineSettings.defaults());
  }

  @Test
  void updateSettingsPersistsAndPublishesAnEvent_whenTheSettingsActuallyChanged(
      @TempDir java.nio.file.Path tempDir) {
    // given
    final List<Object> publishedEvents = new ArrayList<>();
    final var service = newService(tempDir, publishedEvents);
    final var updated = QueryEngineSettings.defaults().toBuilder()
        .earlyTerminationEnabled(false)
        .queryTimeout(Duration.ofMinutes(5))
        .build();

    // when
    service.updateSettings(updated);

    // then
    assertThat(service.settings()).isEqualTo(updated);
    assertThat(publishedEvents).hasSize(1).first().isInstanceOf(QueryEngineSettingsChanged.class);

    // and: the new value survives a reload from disk
    final var reloaded = newService(tempDir, new ArrayList<>());
    assertThat(reloaded.settings()).isEqualTo(updated);
  }

  @Test
  void updateSettingsIsANoOp_whenTheSettingsAreUnchanged(@TempDir java.nio.file.Path tempDir) {
    // given
    final List<Object> publishedEvents = new ArrayList<>();
    final var service = newService(tempDir, publishedEvents);

    // when
    service.updateSettings(QueryEngineSettings.defaults());

    // then
    assertThat(publishedEvents).isEmpty();
  }

  private static ArcScriptEngineSettingsService newService(java.nio.file.Path tempDir,
                                                            List<Object> publishedEvents) {
    final var persistenceService = new UserConfigPersistenceService(tempDir.toString());
    return new ArcScriptEngineSettingsService(persistenceService, publishedEvents::add);
  }

}

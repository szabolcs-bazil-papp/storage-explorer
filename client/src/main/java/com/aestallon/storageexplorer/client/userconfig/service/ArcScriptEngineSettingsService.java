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

import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import com.aestallon.storageexplorer.arcscript.engine.QueryEngineSettings;
import com.aestallon.storageexplorer.client.userconfig.event.QueryEngineSettingsChanged;
import com.fasterxml.jackson.core.type.TypeReference;

/**
 * Holds the user's persisted {@link QueryEngineSettings}, used to tune ArcScript query execution
 * for every {@code query}/{@code index} instruction evaluated by this application instance.
 */
@Service
public class ArcScriptEngineSettingsService {

  private static final Logger log = LoggerFactory.getLogger(ArcScriptEngineSettingsService.class);

  public static final String QUERY_ENGINE_SETTINGS = "query.engine.settings";

  private final UserConfigPersistenceService persistenceService;
  private final ApplicationEventPublisher eventPublisher;
  private final AtomicReference<QueryEngineSettings> settings;

  public ArcScriptEngineSettingsService(UserConfigPersistenceService persistenceService,
                                        ApplicationEventPublisher eventPublisher) {
    this.persistenceService = persistenceService;
    this.eventPublisher = eventPublisher;
    this.settings = new AtomicReference<>(persistenceService.readSettingsAt(
        QUERY_ENGINE_SETTINGS,
        new TypeReference<>() {},
        QueryEngineSettings::defaults));
  }

  public QueryEngineSettings settings() {
    return settings.get();
  }

  public void updateSettings(final QueryEngineSettings settings) {
    final var baseline = settings();
    if (baseline.equals(settings)) {
      return;
    }

    this.settings.set(settings);
    persistenceService.writeSettingsTo(QUERY_ENGINE_SETTINGS, settings);
    log.info("Query engine settings updated: [ {} ]", settings);
    eventPublisher.publishEvent(new QueryEngineSettingsChanged());
  }

}

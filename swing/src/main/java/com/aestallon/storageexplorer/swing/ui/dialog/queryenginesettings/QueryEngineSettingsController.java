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

package com.aestallon.storageexplorer.swing.ui.dialog.queryenginesettings;

import java.util.function.Consumer;
import com.aestallon.storageexplorer.arcscript.engine.QueryEngineSettings;
import com.aestallon.storageexplorer.client.userconfig.service.ArcScriptEngineSettingsService;
import com.aestallon.storageexplorer.swing.ui.controller.AbstractDialogController;

public class QueryEngineSettingsController extends AbstractDialogController<QueryEngineSettings> {

  public static QueryEngineSettingsController newInstance(
      ArcScriptEngineSettingsService arcScriptEngineSettingsService) {
    return new QueryEngineSettingsController(
        arcScriptEngineSettingsService.settings(),
        (before, after) -> arcScriptEngineSettingsService.updateSettings(after));
  }

  public static QueryEngineSettingsController dummy() {
    return new QueryEngineSettingsController(
        QueryEngineSettings.defaults(),
        (before, after) -> System.out.println(after));
  }

  protected QueryEngineSettingsController(QueryEngineSettings initialModel,
                                          Finisher<QueryEngineSettings> finisher) {
    super(initialModel, finisher);
  }

  protected QueryEngineSettingsController(QueryEngineSettings initialModel,
                                          Finisher<QueryEngineSettings> finisher,
                                          Consumer<QueryEngineSettings> postProcessor) {
    super(initialModel, finisher, postProcessor);
  }

}

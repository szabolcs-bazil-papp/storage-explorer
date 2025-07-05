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

package com.aestallon.storageexplorer.swing.ui.dialog.graphsettings;

import java.util.function.Consumer;
import com.aestallon.storageexplorer.client.userconfig.model.GraphSettings;
import com.aestallon.storageexplorer.client.userconfig.service.UserConfigService;
import com.aestallon.storageexplorer.swing.ui.controller.AbstractDialogController;

public class GraphSettingsController extends AbstractDialogController<GraphSettings> {

  public static GraphSettingsController newInstance(UserConfigService userConfigService) {
    return new GraphSettingsController(
        userConfigService.graphSettings(),
        (before, after) -> userConfigService.updateGraphSettings(after));
  }

  public static GraphSettingsController dummy() {
    return new GraphSettingsController(new GraphSettings(),
        (before, after) -> System.out.println(after));
  }

  protected GraphSettingsController(GraphSettings initialModel,
                                    Finisher<GraphSettings> finisher) {
    super(initialModel, finisher);
  }

  protected GraphSettingsController(GraphSettings initialModel,
                                    Finisher<GraphSettings> finisher,
                                    Consumer<GraphSettings> postProcessor) {
    super(initialModel, finisher, postProcessor);
  }
}

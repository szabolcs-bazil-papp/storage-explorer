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

package com.aestallon.storageexplorer.swing.ui.dialog.keymap;


import java.util.Map;
import java.util.function.Consumer;
import com.aestallon.storageexplorer.client.userconfig.model.Keymap;
import com.aestallon.storageexplorer.client.userconfig.service.UserConfigService;
import com.aestallon.storageexplorer.swing.ui.controller.AbstractDialogController;

public class KeymapController extends AbstractDialogController<Map<String, Keymap>> {

  public static KeymapController newInstance(UserConfigService userConfigService) {
    return new KeymapController(
        userConfigService.keymapSettings(),
        (before, after) -> userConfigService.updateKeymapSettings(after));
  }

  public static KeymapController dummy() {
    return new KeymapController(Keymap.defaultKeymaps(),
        (before, after) -> System.out.println(after));
  }


  protected KeymapController(Map<String, Keymap> initialModel,
                             Finisher<Map<String, Keymap>> finisher) {
    super(initialModel, finisher);
  }

  protected KeymapController(Map<String, Keymap> initialModel,
                             Finisher<Map<String, Keymap>> finisher,
                             Consumer<Map<String, Keymap>> postProcessor) {
    super(initialModel, finisher, postProcessor);
  }

}

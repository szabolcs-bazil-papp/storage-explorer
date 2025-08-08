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

package com.aestallon.storageexplorer.swing.ui.commander;

import javax.swing.*;
import com.aestallon.storageexplorer.client.userconfig.service.UserConfigService;
import com.aestallon.storageexplorer.swing.ui.controller.SideBarController;

public abstract class AbstractCommanderTabbedView extends JTabbedPane implements CommanderView {


  protected final UserConfigService userConfigService;
  protected final SideBarController sideBarController;

  protected AbstractCommanderTabbedView(int tabPlacement, int tabLayoutPolicy,
                                        UserConfigService userConfigService,
                                        SideBarController sideBarController) {
    super(tabPlacement, tabLayoutPolicy);
    this.userConfigService = userConfigService;
    this.sideBarController = sideBarController;
    sideBarController.registerCommanderView(this);
  }


  @Override
  public void requestVisibility() {
    sideBarController.showCommanderView(this);
  }

  @Override
  public JComponent asComponent() {
    return this;
  }

}

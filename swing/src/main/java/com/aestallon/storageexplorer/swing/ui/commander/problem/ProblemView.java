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

package com.aestallon.storageexplorer.swing.ui.commander.problem;

import javax.swing.*;
import org.springframework.stereotype.Component;
import com.aestallon.storageexplorer.client.userconfig.service.ProblemService;
import com.aestallon.storageexplorer.client.userconfig.service.UserConfigService;
import com.aestallon.storageexplorer.swing.ui.commander.AbstractCommanderPanelView;
import com.aestallon.storageexplorer.swing.ui.commander.CommanderView;
import com.aestallon.storageexplorer.swing.ui.controller.SideBarController;
import com.aestallon.storageexplorer.swing.ui.misc.IconProvider;

@Component
public class ProblemView extends AbstractCommanderPanelView implements CommanderView {

  private final ProblemService problemService;

  public ProblemView(UserConfigService userConfigService,
                     SideBarController sideBarController,
                     ProblemService problemService) {
    super(userConfigService, sideBarController);
    this.problemService = problemService;
  }

  @Override
  public String name() {
    return "Problems";
  }

  @Override
  public ImageIcon icon() {
    return IconProvider.ERROR;
  }

  @Override
  public String tooltip() {
    return "Errors and anomalies encountered while running the software";
  }
}

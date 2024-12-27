/*
 * Copyright (C) 2024 it4all Hungary Kft.
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

package com.aestallon.storageexplorer.swing.ui;

import javax.swing.*;
import org.springframework.stereotype.Component;
import com.aestallon.storageexplorer.swing.ui.tree.MainTreeView;

@Component
public class MainView extends JSplitPane {

  private final MainTreeView mainTreeView;
  private final ExplorerView explorerView;
  public MainView(MainTreeView mainTreeView, ExplorerView explorerView) {
    super(JSplitPane.HORIZONTAL_SPLIT, mainTreeView, explorerView);
    this.mainTreeView = mainTreeView;
    this.explorerView = explorerView;
  }

  public MainTreeView mainTreeView() {
    return mainTreeView;
  }

  public ExplorerView explorerView() {
    return explorerView;
  }

}

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

import java.awt.*;
import javax.swing.*;
import org.springframework.stereotype.Component;
import com.aestallon.storageexplorer.swing.ui.explorer.ExplorerView;
import com.aestallon.storageexplorer.swing.ui.misc.HiddenPaneSize;
import com.aestallon.storageexplorer.swing.ui.tree.MainTreeView;

@Component
public class MainView extends JSplitPane {

  private final MainTreeView mainTreeView;
  private final ExplorerView explorerView;

  private HiddenPaneSize hiddenPaneSize;

  public MainView(MainTreeView mainTreeView, ExplorerView explorerView) {
    super(JSplitPane.HORIZONTAL_SPLIT, mainTreeView, explorerView);
    this.mainTreeView = mainTreeView;
    this.explorerView = explorerView;
  }

  private boolean showingTree() {
    return hiddenPaneSize == null;
  }

  public void showHideTree(final boolean show) {
    if (show == showingTree()) {
      return;
    }

    if (show) {
      setDividerLocation(hiddenPaneSize.dividerLocation());
      setDividerSize(hiddenPaneSize.dividerSize());
      mainTreeView.setMinimumSize(null);
      mainTreeView.setMaximumSize(null);
      mainTreeView.setPreferredSize(hiddenPaneSize.toPreferredSize());

      hiddenPaneSize = null;
    } else {
      final Dimension preferredSize = mainTreeView.getPreferredSize();
      final int dividerLocation = getDividerLocation();
      final int dividerSize = getDividerSize();
      hiddenPaneSize = HiddenPaneSize.of(preferredSize, dividerLocation, dividerSize);

      setDividerLocation(0);
      setDividerSize(0);
      mainTreeView.setMinimumSize(new Dimension(0, 0));
      mainTreeView.setMaximumSize(new Dimension(0, 0));
    }
  }

  public MainTreeView mainTreeView() {
    return mainTreeView;
  }

  public ExplorerView explorerView() {
    return explorerView;
  }

}

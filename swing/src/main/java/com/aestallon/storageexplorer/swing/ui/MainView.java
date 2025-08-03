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
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import com.aestallon.storageexplorer.swing.ui.controller.SideBarController;
import com.aestallon.storageexplorer.swing.ui.explorer.ExplorerView;
import com.aestallon.storageexplorer.swing.ui.misc.HiddenPaneSize;

@Component
public class MainView extends JSplitPane {

  private static JPanel placeholder() {
    final JPanel panel = new JPanel();
    panel.setMinimumSize(new Dimension(100, 500));
    return panel;
  }
  private final ExplorerView explorerView;

  private HiddenPaneSize hiddenPaneSize;

  public MainView(ExplorerView explorerView) {
    super(JSplitPane.HORIZONTAL_SPLIT, placeholder(), explorerView);
    this.explorerView = explorerView;
  }

  private boolean showingTree() {
    return hiddenPaneSize == null;
  }

  @EventListener
  public void showHideTree(final SideBarController.TreeShowEvent e) {
    final boolean showing = showingTree();
    if (!showing) {
      switch (e) {
        case SideBarController.TreeShowEvent.None n -> {
          // requested to show nothing and we already do
        }
        case SideBarController.TreeShowEvent.Some(var treeView) -> {
          final var comp = treeView.asComponent();
          setDividerLocation(hiddenPaneSize.dividerLocation());
          setDividerSize(hiddenPaneSize.dividerSize());
          comp.setMinimumSize(null);
          comp.setMaximumSize(null);
          comp.setPreferredSize(hiddenPaneSize.toPreferredSize());
          hiddenPaneSize = null;
          if (treeView != getLeftComponent()) {
            setLeftComponent(comp);
          }
        }
      }
    } else {
      // we are already showing *something*:
      switch (e) {
        case SideBarController.TreeShowEvent.None n -> {
          // but we shouldn't:
          final var comp = getLeftComponent();
          final int dividerLocation = getDividerLocation();
          final int dividerSize = getDividerSize();
          hiddenPaneSize = HiddenPaneSize.of(comp.getPreferredSize(), dividerLocation, dividerSize);
          setDividerLocation(0);
          setDividerSize(0);
          comp.setMinimumSize(new Dimension(0, 0));
          comp.setMaximumSize(new Dimension(0, 0));
        }
        case SideBarController.TreeShowEvent.Some(var treeView) -> {
          if (treeView == getLeftComponent()) {
            // we are already showing the exact thing needed to be shown:
            return;
          } else {
            final var divLoc = getDividerLocation();
            final var divSize = getDividerSize();
            // we have to swap:
            setLeftComponent(treeView.asComponent());
            setDividerLocation(divLoc);
            setDividerSize(divSize);
          }
        }
      }
    }
  }

  public ExplorerView explorerView() {
    return explorerView;
  }

}

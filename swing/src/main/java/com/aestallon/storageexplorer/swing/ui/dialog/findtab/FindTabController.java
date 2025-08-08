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

package com.aestallon.storageexplorer.swing.ui.dialog.findtab;

import java.util.Arrays;
import java.util.Objects;
import javax.swing.*;
import org.springframework.stereotype.Service;
import com.aestallon.storageexplorer.swing.ui.controller.SideBarController;
import com.aestallon.storageexplorer.swing.ui.explorer.TabContainerView;
import com.aestallon.storageexplorer.swing.ui.explorer.TabView;
import com.aestallon.storageexplorer.swing.ui.explorer.TabViewThumbnail;

@Service
public class FindTabController {

  private final TabContainerView tabContainerView;
  private final SideBarController sideBarController;

  public FindTabController(final TabContainerView tabContainerView,
                           final SideBarController sideBarController) {
    this.tabContainerView = tabContainerView;
    this.sideBarController = sideBarController;
  }

  public void showFindTabDialog(JFrame parent) {
    final var dialog = new FindTabDialog(this, parent);
    dialog.setVisible(true);
  }

  TabViewThumbnail[] getThumbnails() {
    return Arrays.stream(tabContainerView.getComponents())
        .filter(TabView.class::isInstance)
        .map(TabView.class::cast)
        .map(TabView::thumbnail)
        .filter(Objects::nonNull)
        .toArray(TabViewThumbnail[]::new);
  }

  TabViewThumbnail[] filter(TabViewThumbnail[] thumbnails, String filter) {
    final String filterToUse = filter.trim().toLowerCase();
    if (filterToUse.isBlank()) {
      return thumbnails;
    }
    
    return Arrays.stream(thumbnails)
        .filter(it -> it.title().toLowerCase().contains(filterToUse)
                      || it.description().toLowerCase().contains(filterToUse))
        .toArray(TabViewThumbnail[]::new);
  }

  void show(final TabViewThumbnail thumbnail) {
    sideBarController.select(thumbnail.locator());
  }

}

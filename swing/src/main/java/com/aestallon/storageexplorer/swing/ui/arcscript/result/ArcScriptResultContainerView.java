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

package com.aestallon.storageexplorer.swing.ui.arcscript.result;

import javax.swing.*;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import com.aestallon.storageexplorer.client.userconfig.service.UserConfigService;
import com.aestallon.storageexplorer.swing.ui.commander.AbstractCommanderTabbedView;
import com.aestallon.storageexplorer.swing.ui.commander.CommanderView;
import com.aestallon.storageexplorer.swing.ui.controller.SideBarController;
import com.aestallon.storageexplorer.swing.ui.event.ArcScriptViewRenamed;
import com.aestallon.storageexplorer.swing.ui.misc.IconProvider;

@Component
public class ArcScriptResultContainerView
    extends AbstractCommanderTabbedView
    implements CommanderView {

  protected ArcScriptResultContainerView(UserConfigService userConfigService,
                                         SideBarController sideBarController) {
    super(JTabbedPane.TOP, JTabbedPane.SCROLL_TAB_LAYOUT, userConfigService, sideBarController); 
    addTab("Home", new ArcScriptResultView.Initial());
  }

  @Override
  public String name() {
    return "ArcScript Results";
  }

  @Override
  public ImageIcon icon() {
    return IconProvider.DATA_TRANSFER;
  }

  @Override
  public String tooltip() {
    return "ArcScript results";
  }
  
  public void showResult(String title, ArcScriptResultView.ResultDisplay resultDisplay) {
    final var div = new ArcScriptResultView.ResultDisplayDiv(resultDisplay);
    addTab(title, div);
    setSelectedComponent(div);
  }
  
  @EventListener
  public void onArcScriptViewRenamed(final ArcScriptViewRenamed e) {
    SwingUtilities.invokeLater(() -> {
      for (final var component : getComponents()) {
        if (component instanceof ArcScriptResultView.ResultDisplayDiv div) {
          final var tab = (JLabel) getTabComponentAt(indexOfComponent(div));
          if (tab.getText().equals(e.from())) {
            tab.setText(e.to());
          }
        }
      }
    });
  }
  
  
}

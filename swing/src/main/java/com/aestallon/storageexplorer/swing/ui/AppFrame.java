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

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import javax.swing.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import com.aestallon.storageexplorer.core.service.StorageInstanceProvider;
import com.aestallon.storageexplorer.core.userconfig.service.UserConfigService;
import com.aestallon.storageexplorer.swing.ui.dialog.GraphSettingsDialog;
import com.aestallon.storageexplorer.swing.ui.dialog.importstorage.ImportStorageController;
import com.aestallon.storageexplorer.swing.ui.dialog.importstorage.ImportStorageDialog;
import com.aestallon.storageexplorer.swing.ui.dialog.SearchForEntryDialog;
import com.aestallon.storageexplorer.swing.ui.misc.IconProvider;

@Component
public class AppFrame extends JFrame {

  private final ApplicationEventPublisher eventPublisher;
  private final StorageInstanceProvider storageInstanceProvider;
  private final UserConfigService userConfigService;
  private final MainView mainView;

  public AppFrame(ApplicationEventPublisher eventPublisher,
                  StorageInstanceProvider storageInstanceProvider, UserConfigService userConfigService,
                  MainView mainView) {
    this.eventPublisher = eventPublisher;
    this.storageInstanceProvider = storageInstanceProvider;
    this.userConfigService = userConfigService;
    this.mainView = mainView;

    setTitle("Storage Explorer");
    setSize(900, 600);
    setDefaultCloseOperation(EXIT_ON_CLOSE);
    initMenu();
    addSearchAction();
    add(mainView);
  }

  private void addSearchAction() {
    final var ctrlShiftT = KeyStroke.getKeyStroke(
        KeyEvent.VK_T,
        KeyEvent.CTRL_DOWN_MASK | KeyEvent.SHIFT_DOWN_MASK);
    InputMap im = getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
    ActionMap am = getRootPane().getActionMap();

    im.put(ctrlShiftT, "cancel");
    am.put("cancel", new SearchAction());
  }

  private void initMenu() {
    final var menubar = new JMenuBar();
    final var commands = new JMenu("Commands");

    final var selectNode = new JMenuItem("Select node...");
    selectNode.addActionListener(new SearchAction());
    commands.add(selectNode);

    final var importStorage = new JMenuItem("Import storage...");
    importStorage.addActionListener(new ImportStorageAction());
    commands.add(importStorage);

    menubar.add(commands);

    final var settings = new JMenu("Settings");

    final var graphSettings = new JMenuItem("Graph Settings...");
    graphSettings.addActionListener(e -> {
      final var dialog = new GraphSettingsDialog(userConfigService);
      dialog.setLocationRelativeTo(this);
      dialog.setVisible(true);
    });
    settings.add(graphSettings);
    menubar.add(settings);

    setJMenuBar(menubar);
  }



  public void launch() {
    setVisible(true);
  }


  private final class SearchAction extends AbstractAction {

    private SearchAction() {
      super(null, IconProvider.OBJ);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      final var dialog = new SearchForEntryDialog(storageInstanceProvider, eventPublisher);
      dialog.setLocationRelativeTo(AppFrame.this);
      dialog.setVisible(true);
    }

  }


  private final class ImportStorageAction extends AbstractAction {

    private ImportStorageAction() {
      super("Import Storage...");
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      final ImportStorageDialog dialog = new ImportStorageDialog(
          ImportStorageController.forCreatingNew(storageInstanceProvider));
      dialog.pack();
      dialog.setLocationRelativeTo(AppFrame.this);
      dialog.setVisible(true);
    }
  }

}

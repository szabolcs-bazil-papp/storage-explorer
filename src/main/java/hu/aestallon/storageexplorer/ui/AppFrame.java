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

package hu.aestallon.storageexplorer.ui;

import java.net.URI;
import javax.swing.*;
import org.springframework.stereotype.Component;

@Component
public class AppFrame extends JFrame {


  private final MainView mainView;

  public AppFrame(MainView mainView) {
    this.mainView = null;
    setTitle("Storage Explorer");
    setSize(900, 600);
    setDefaultCloseOperation(EXIT_ON_CLOSE);
    initMenu();
    add(mainView);
  }

  private void initMenu() {
    final var menubar = new JMenuBar();
    final var popup = new JMenu("Commands");
    final var selectNode = new JMenuItem("Select node...");

    popup.add(selectNode);

    menubar.add(popup);

    setJMenuBar(menubar);
  }

  public void launch() {
    setVisible(true);
  }

}

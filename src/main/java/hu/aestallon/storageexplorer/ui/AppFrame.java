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

  private final StorageGraph storageGraph;

  public AppFrame(StorageGraph storageGraph) {
    this.storageGraph = storageGraph;

    setTitle("Storage Explorer");
    setSize(900, 600);
    setDefaultCloseOperation(EXIT_ON_CLOSE);
    initMenu();
  }

  private void initMenu() {
    final var menubar = new JMenuBar();
    final var popup = new JMenu("Commands");
    final var selectNode = new JMenuItem("Select node...");
    selectNode.addActionListener(it -> {
      final var dialog = new JFrame("Enter URI");
      final var pane = new JPanel();
      final var textfield = new JTextField("URI", 20);
      final var ok = new JButton("OK");
      ok.addActionListener(e -> {
        storageGraph.initOnUri(URI.create(textfield.getText()));
        dialog.dispose();
      });
      pane.add(textfield);
      pane.add(ok);
      dialog.add(pane);
      dialog.setSize(200, 200);
      dialog.setDefaultCloseOperation(DISPOSE_ON_CLOSE);
      dialog.setLocationRelativeTo(this);
      dialog.setVisible(true);
    });

    popup.add(selectNode);

    menubar.add(popup);

    setJMenuBar(menubar);
  }

  public void launch() {
    add(storageGraph.init());
    setVisible(true);
  }

}

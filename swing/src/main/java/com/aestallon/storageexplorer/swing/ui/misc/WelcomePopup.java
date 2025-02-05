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

package com.aestallon.storageexplorer.swing.ui.misc;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.net.URI;
import javax.swing.*;

public final class WelcomePopup extends JDialog {

  public static void show(java.awt.Component parent) {
    final var dialog = new WelcomePopup();
    dialog.setLocationRelativeTo(parent);
    dialog.pack();
    dialog.setVisible(true);
  }

  private JPanel contentPane;  // *-----+
  private JLabel title;        //       |
  private JPanel textPanel;    // *--+  |
  private JLabel textA;        //    |  |
  private JLink wikiLink;      //    |  |
  private JLabel textB;        // ---+  |
  private JPanel controlPanel; // *--+  |
  private JButton okButton;    // ---+  +



  public WelcomePopup() {
    setContentPane(contentPane);
    setModalityType(DEFAULT_MODALITY_TYPE);
    getRootPane().setDefaultButton(okButton);
    setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
    addWindowListener(new WindowAdapter() {

      @Override
      public void windowClosing(WindowEvent e) {
        onOk();
      }

    });
    contentPane.registerKeyboardAction(
        e -> onOk(),
        KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
        JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
    setTitle("Welcome");
    setIconImage(IconProvider.FAVICON.getImage());
    okButton.addActionListener(e -> onOk());
  }

  private void onOk() {
    dispose();
  }


  {
    setupUiInternal();
  }

  private void setupUiInternal() {
    contentPane = new JPanel();
    contentPane.setLayout(new BoxLayout(contentPane, BoxLayout.Y_AXIS));
    contentPane.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
    contentPane.setMinimumSize(new Dimension(480, 110));
    contentPane.setPreferredSize(new Dimension(480, 110));

    title = new JLabel("Welcome to Storage Explorer!");
    title.setIcon(IconProvider.FAVICON);
    title.putClientProperty("FlatLaf.styleClass", "h3");
    title.setAlignmentX(LEFT_ALIGNMENT);
    contentPane.add(title);

    textPanel = new JPanel();
    textPanel.setLayout(new FlowLayout(FlowLayout.LEFT, 0, 5));
    textPanel.setAlignmentX(LEFT_ALIGNMENT);

    textA = new JLabel("Import a storage to get started (Commands > Import storage...), or consult ");
    //textA.putClientProperty("FlatLaf.styleClass", "h4");
    textPanel.add(textA);

    wikiLink = new JLink("the wiki here",
        URI.create("https://github.com/szabolcs-bazil-papp/storage-explorer/wiki"));
    //wikiLink.putClientProperty("FlatLaf.styleClass", "h4");
    textPanel.add(wikiLink);

    textB = new JLabel(" for a detailed guide!");
    //textB.putClientProperty("FlatLaf.styleClass", "h4");
    textPanel.add(textB);

    contentPane.add(textPanel);

    controlPanel = new JPanel();
    controlPanel.setLayout(new BoxLayout(controlPanel, BoxLayout.X_AXIS));
    controlPanel.setAlignmentX(LEFT_ALIGNMENT);
    controlPanel.add(Box.createHorizontalGlue());

    okButton = new JButton("OK");
    controlPanel.add(okButton);

    contentPane.add(controlPanel);
  }
}

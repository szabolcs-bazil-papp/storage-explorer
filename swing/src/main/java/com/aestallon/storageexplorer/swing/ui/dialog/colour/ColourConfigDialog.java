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

package com.aestallon.storageexplorer.swing.ui.dialog.colour;

import java.awt.*;
import java.util.List;
import javax.swing.*;
import com.aestallon.storageexplorer.client.userconfig.model.Theme;
import com.aestallon.storageexplorer.common.util.Pair;
import com.aestallon.storageexplorer.swing.ui.misc.ColourService;
import com.aestallon.storageexplorer.swing.ui.misc.IconProvider;
import raven.color.ColorPicker;

public class ColourConfigDialog extends JDialog {

  private static final Dimension SWATCH_SIZE = new Dimension(32, 22);

  private final ColourService service;

  public ColourConfigDialog(ColourService service, Frame owner) {
    super(owner, "Colour Configuration", true);
    this.service = service;

    setDefaultCloseOperation(DISPOSE_ON_CLOSE);
    setSize(700, 700);
    setMinimumSize(new Dimension(440, 280));
    setLocationRelativeTo(owner);

    JPanel contentPanel = buildContentPanel();

    JScrollPane scrollPane = new JScrollPane(contentPanel,
        ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
        ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    scrollPane.setBorder(BorderFactory.createEmptyBorder());

    JPanel bottomBar = buildBottomBar();

    setLayout(new BorderLayout());
    add(scrollPane, BorderLayout.CENTER);
    add(bottomBar,  BorderLayout.SOUTH);
  }

  private JPanel buildContentPanel() {
    JPanel panel = new JPanel();
    panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

    List<Pair<String, String>> keys = service.keys();

    // Column header
    panel.add(buildHeader());
    panel.add(new JSeparator());

    for (Pair<String, String> entry : keys) {
      String key       = entry.a();
      String labelText = entry.b();
      panel.add(buildRow(key, labelText));
      panel.add(new JSeparator());
    }

    // Push rows to the top when there are few entries
    panel.add(Box.createVerticalGlue());

    return panel;
  }

  private JPanel buildHeader() {
    JPanel header = new JPanel(new BorderLayout());
    header.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));

    // Placeholder on the left matching name-column width
    JLabel nameLabel = new JLabel("Name");
    nameLabel.setEnabled(false);

    JPanel columnLabels = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
    columnLabels.setOpaque(false);
    columnLabels.add(new JLabel("Light"));
    columnLabels.add(Box.createHorizontalStrut(62)); // aligns with dark column
    columnLabels.add(new JLabel("Dark"));

    header.add(nameLabel,    BorderLayout.WEST);
    header.add(columnLabels, BorderLayout.EAST);
    return header;
  }

  private JPanel buildRow(String key, String labelText) {
    JPanel row = new JPanel(new BorderLayout(8, 0));
    row.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
    row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));

    JLabel nameLabel = new JLabel(labelText);
    nameLabel.setToolTipText(key);

    Color initialLight = service.get(key, Theme.LIGHT);
    JPanel lightSwatch = createSwatch(initialLight);
    JButton lightBtn   = new JButton(IconProvider.EDIT);
    lightBtn.setToolTipText("Pick light mode colour for \"" + labelText + "\"");
    lightBtn.addActionListener(e -> {
      Color current = service.get(key, Theme.LIGHT);
      Color chosen  = ColorPicker.showDialog(this, "Light Mode – " + labelText, current);
      if (chosen != null) {
        service.set(key, Theme.LIGHT, chosen);
        updateSwatch(lightSwatch, chosen);
      }
    });

    Color initialDark = service.get(key, Theme.DARK);
    JPanel darkSwatch = createSwatch(initialDark);
    JButton darkBtn   = new JButton(IconProvider.EDIT);
    darkBtn.setToolTipText("Pick dark mode colour for \"" + labelText + "\"");
    darkBtn.addActionListener(e -> {
      Color current = service.get(key, Theme.DARK);
      Color chosen  = ColorPicker.showDialog(this, "Dark Mode – " + labelText, current);
      if (chosen != null) {
        service.set(key, Theme.DARK, chosen);
        updateSwatch(darkSwatch, chosen);
      }
    });

    JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
    controls.setOpaque(false);
    controls.add(lightSwatch);
    controls.add(lightBtn);
    controls.add(Box.createHorizontalStrut(10));
    controls.add(darkSwatch);
    controls.add(darkBtn);

    row.add(nameLabel, BorderLayout.CENTER);
    row.add(controls,  BorderLayout.EAST);
    return row;
  }

  private JPanel buildBottomBar() {
    JButton closeBtn = new JButton("Close");
    closeBtn.addActionListener(e -> dispose());

    JPanel bar = new JPanel(new FlowLayout(FlowLayout.RIGHT));
    bar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0,
        UIManager.getColor("Separator.foreground")));
    bar.add(closeBtn);
    return bar;
  }

  private JPanel createSwatch(Color colour) {
    JPanel swatch = new JPanel() {
      @Override protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        // Checkerboard background to reveal transparency
        int sq = 5;
        for (int y = 0; y < getHeight(); y += sq) {
          for (int x = 0; x < getWidth(); x += sq) {
            g.setColor(((x / sq + y / sq) % 2 == 0) ? Color.WHITE : Color.LIGHT_GRAY);
            g.fillRect(x, y, sq, sq);
          }
        }
        if (getBackground() != null) {
          g.setColor(getBackground());
          g.fillRect(0, 0, getWidth(), getHeight());
        }
      }
    };
    swatch.setPreferredSize(SWATCH_SIZE);
    swatch.setMinimumSize(SWATCH_SIZE);
    swatch.setMaximumSize(SWATCH_SIZE);
    swatch.setBorder(BorderFactory.createLineBorder(
        UIManager.getColor("Component.borderColor"), 1));
    swatch.setBackground(colour);
    swatch.setOpaque(true); // let paintComponent handle everything
    return swatch;
  }

  private void updateSwatch(JPanel swatch, Color colour) {
    swatch.setBackground(colour);
    swatch.repaint();
  }

}

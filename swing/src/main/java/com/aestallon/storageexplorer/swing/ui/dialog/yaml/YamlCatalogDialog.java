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

package com.aestallon.storageexplorer.swing.ui.dialog.yaml;

import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.stream.Collectors;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import com.aestallon.storageexplorer.client.userconfig.service.NominalTypeService;
import com.aestallon.storageexplorer.swing.ui.misc.IconProvider;

public class YamlCatalogDialog extends JDialog {

  private final NominalTypeService service;

  private final JPanel listPanel;
  private final JScrollPane scrollPane;

  public YamlCatalogDialog(NominalTypeService service, Frame parent) {
    super(parent, "YAML Catalog", true);
    this.service = service;

    setDefaultCloseOperation(DISPOSE_ON_CLOSE);
    setSize(700, 700);
    setMinimumSize(new Dimension(360, 260));
    setLocationRelativeTo(parent);

    listPanel = new JPanel();
    listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));

    scrollPane = new JScrollPane(listPanel,
        ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
        ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    scrollPane.setBorder(BorderFactory.createEmptyBorder());

    JButton selectNewBtn = new JButton("Select New…", IconProvider.PLUS);
    selectNewBtn.addActionListener(e -> onSelectNew());

    JPanel bottomBar = new JPanel(new FlowLayout(FlowLayout.RIGHT));
    bottomBar.add(selectNewBtn);

    setLayout(new BorderLayout());
    add(scrollPane, BorderLayout.CENTER);
    add(bottomBar, BorderLayout.SOUTH);

    refreshList();
  }

  private void refreshList() {
    listPanel.removeAll();

    final var names = service.findAll(); // already sorted alphabetically

    if (names.isEmpty()) {
      JLabel empty = new JLabel("No files registered yet.");
      empty.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
      empty.setEnabled(false);
      listPanel.add(empty);
    } else {
      for (String name : names) {
        listPanel.add(buildRow(name));
        listPanel.add(new JSeparator());
      }
    }

    listPanel.revalidate();
    listPanel.repaint();
  }

  private JPanel buildRow(String name) {
    JPanel row = new JPanel(new BorderLayout(8, 0));
    row.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
    row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height + 8));

    JLabel nameLabel = new JLabel(name);
    nameLabel.setToolTipText(name);

    JButton deleteBtn = new JButton(IconProvider.DELETE);
    deleteBtn.setToolTipText("Delete " + name);
    deleteBtn.setFocusable(false);
    deleteBtn.addActionListener(e -> onDelete(name));

    row.add(nameLabel, BorderLayout.CENTER);
    row.add(deleteBtn, BorderLayout.EAST);
    return row;
  }

  private void onDelete(String name) {
    int choice = JOptionPane.showConfirmDialog(
        this,
        "Delete \"" + name + "\"?",
        "Confirm Delete",
        JOptionPane.OK_CANCEL_OPTION,
        JOptionPane.WARNING_MESSAGE);

    if (choice != JOptionPane.OK_OPTION) {
      return;
    }

    boolean deleted = service.delete(name);
    if (!deleted) {
      JOptionPane.showMessageDialog(
          this,
          "Could not delete \"" + name + "\".\nThe file may be in use or already removed.",
          "Delete Failed",
          JOptionPane.ERROR_MESSAGE);
    }

    refreshList();
  }

  private void onSelectNew() {
    JFileChooser chooser = new JFileChooser();
    chooser.setDialogTitle("Select YAML File(s)");
    chooser.setMultiSelectionEnabled(true);
    chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
    chooser.setFileFilter(new FileNameExtensionFilter("YAML files (*.yaml, *.yml)", "yaml", "yml"));
    chooser.setAcceptAllFileFilterUsed(false);

    int result = chooser.showOpenDialog(this);
    if (result != JFileChooser.APPROVE_OPTION) {
      return;
    }

    java.io.File[] selected = chooser.getSelectedFiles();

    final var skipped = new ArrayList<File>();
    for (java.io.File file : selected) {
      final var path = file.toPath();
      if (service.exists(path)) {
        skipped.add(file);
      } else {
        service.save(path);
      }
    }

    if (!skipped.isEmpty()) {
      final String description = "The following file(s) are already present:\n"
          + skipped.stream().map(File::getName)
          .map(it -> '"' + it + '"')
          .collect(Collectors.joining(", "))
          + "\nWould you like to overwrite them?";
      final var res = JOptionPane.showConfirmDialog(
          this,
          description,
          "Overwrite existing?",
          JOptionPane.YES_NO_OPTION);
      if (res == JOptionPane.YES_OPTION) {
        for (final var file : skipped) {
          final var path = file.toPath();
          service.save(path);
        }
      }
    }

    refreshList();
  }

}

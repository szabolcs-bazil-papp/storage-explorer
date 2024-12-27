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

package com.aestallon.storageexplorer.swing.ui.inspector;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.URI;
import java.util.Comparator;
import java.util.List;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.aestallon.storageexplorer.core.model.entry.ListEntry;
import com.aestallon.storageexplorer.core.model.entry.StorageEntry;
import com.aestallon.storageexplorer.core.model.entry.UriProperty;
import static java.util.stream.Collectors.toList;

public class CollectionEntryInspectorView extends JPanel implements InspectorView<StorageEntry> {

  private static final Logger log = LoggerFactory.getLogger(CollectionEntryInspectorView.class);

  private JToolBar toolBar;
  private JTable table;
  private StorageCollectionTableModel tableModel;
  private JScrollPane pane;

  private final StorageEntry storageEntry;
  private final StorageEntryInspectorViewFactory factory;

  public CollectionEntryInspectorView(StorageEntry storageEntry,
                                      StorageEntryInspectorViewFactory factory) {
    this.storageEntry = storageEntry;
    this.factory = factory;

    setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
    setBorder(new EmptyBorder(5, 5, 5, 5));

    initToolbar();
    initTable();
  }

  private void initToolbar() {
    final var toolBar = new JToolBar(JToolBar.TOP);
    factory.addRenderAction(storageEntry, toolBar);
    add(toolBar);
  }

  private void initTable() {
    tableModel = new StorageCollectionTableModel(storageEntry);
    table = new JTable(tableModel);
    table.setAutoCreateRowSorter(true);
    table.addMouseListener(new MouseAdapter() {

      @Override
      public void mouseClicked(MouseEvent e) {
        final Point eventLocation = e.getPoint();
        final int row = table.rowAtPoint(eventLocation);
        final int col = table.columnAtPoint(eventLocation);
        if (row < 0 || col != 1) {
          return;
        }

        final Object value = tableModel.getValueAt(row, col);
        if (value instanceof URI) {
          factory.jumpToUri((URI) value);
        }
      }

    });
    pane = new JScrollPane(table);
    table.setFillsViewportHeight(true);

    add(pane);
  }

  @Override
  public StorageEntry storageEntry() {
    return storageEntry;
  }

  private final static class StorageCollectionTableModel extends AbstractTableModel {

    private final StorageEntry storageEntry;
    private final List<UriProperty> uris;

    private StorageCollectionTableModel(StorageEntry storageEntry) {
      this.storageEntry = storageEntry;
      uris = storageEntry.uriProperties().stream()
          .sorted(Comparator.comparing(UriProperty::propertyName)).collect(toList());
    }

    @Override
    public String getColumnName(int column) {
      return column == 0
          ? (storageEntry instanceof ListEntry) ? "index" : "key"
          : column == 1 ? "URI" : "((( UNKNOWN )))";
    }

    @Override
    public int getRowCount() {
      return uris.size();
    }

    @Override
    public int getColumnCount() {
      return 2;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
      return columnIndex == 0
          ? (storageEntry instanceof ListEntry)
            ? Integer.parseInt(uris.get(rowIndex).propertyName())
            : uris.get(rowIndex).propertyName()
          : uris.get(rowIndex).uri();
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
      return columnIndex == 0
          ? (storageEntry instanceof ListEntry) ? Integer.class : String.class
          : columnIndex == 1 ? URI.class : null;
    }

  }

}

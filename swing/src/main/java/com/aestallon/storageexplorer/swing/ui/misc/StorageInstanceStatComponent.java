package com.aestallon.storageexplorer.swing.ui.misc;

import java.awt.*;
import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableModel;
import com.aestallon.storageexplorer.client.storage.stat.SchemaStats;
import com.aestallon.storageexplorer.core.model.instance.StorageInstance;

public class StorageInstanceStatComponent {


  private final TableModel tableModel;

  public StorageInstanceStatComponent(final StorageInstance storageInstance) {
    this.tableModel = new StorageInstanceStatTableModel(storageInstance);
  }

  public JComponent asComponent() {
    final var table = new JTable(tableModel);
    TableDisplayMagic.doMagicTableColumnResizing(table);
    table.setAutoResizeMode(JTable.AUTO_RESIZE_NEXT_COLUMN);
    table.setAlignmentX(Component.LEFT_ALIGNMENT);
    table.setFillsViewportHeight(true);

    final JScrollPane pane = new JScrollPane(table);
    pane.setAlignmentX(Component.LEFT_ALIGNMENT);
    pane.setPreferredSize(new Dimension(-1, table.getPreferredSize().height + 10));
    return pane;
  }

  private static final class StorageInstanceStatTableModel extends AbstractTableModel {

    private final SchemaStats.SchemaStatData data;

    private StorageInstanceStatTableModel(final StorageInstance storageInstance) {
      data = SchemaStats.of(storageInstance);
    }

    @Override
    public String getColumnName(int column) {
      return data.getHeader(column);
    }

    @Override
    public int getRowCount() {
      return data.getRowCount();
    }

    @Override
    public int getColumnCount() {
      return data.getColumnCount();
    }

    @Override
    public Object getValueAt(int row, int column) {
      return data.getValueAt(row, column);
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
      return data.getColumnClass(columnIndex);
    }

  }

}

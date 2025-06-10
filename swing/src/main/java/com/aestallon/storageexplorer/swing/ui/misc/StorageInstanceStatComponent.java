package com.aestallon.storageexplorer.swing.ui.misc;

import java.awt.*;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableModel;
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

    private record EntryStat(String schema, boolean valid) {}


    private static final class SchemaRow {

      private static final NumberFormat PERCENT_FORMAT;

      static {
        NumberFormat temp = DecimalFormat.getPercentInstance(Locale.getDefault());
        temp.setMaximumFractionDigits(2);
        PERCENT_FORMAT = temp;
      }

      private final String schema;
      private long total;
      private long valid;

      private SchemaRow(final String schema) {
        this.schema = schema;
        this.total = 0;
        this.valid = 0;
      }

      private void add(final boolean valid) {
        this.total++;
        if (valid) {
          this.valid++;
        }
      }

      private String schema() {
        return schema;
      }

      private long total() {
        return total;
      }

      private long valid() {
        return valid;
      }

      private long invalid() {
        return total() - valid();
      }

      private double validPercent() {
        return (total == 0L) ? 0L : (valid * 100d) / total();
      }

      private String validPercentString() {
        return PERCENT_FORMAT.format(validPercent());
      }

    }


    private static final String[] COLS =
        { "Schema", "Total", "Valid", "Invalid", "Valid Percentage" };
    private static final Class<?>[] COL_CLASSES =
        { String.class, Long.class, Long.class, Long.class, String.class };


    private final long total;
    private final long valid;
    private final List<SchemaRow> rows;

    private StorageInstanceStatTableModel(final StorageInstance storageInstance) {
      final var stats = storageInstance.entities()
          .map(it -> new EntryStat(it.uri().getScheme(), it.valid()))
          .toList();
      this.total = stats.size();
      this.valid = stats.stream().filter(it -> it.valid).count();

      final Map<String, SchemaRow> schemaMap = new HashMap<>();
      for (final var entry : stats) {
        schemaMap.computeIfAbsent(entry.schema(), SchemaRow::new).add(entry.valid());
      }
      this.rows = new ArrayList<>(schemaMap.values());
      this.rows.sort(Comparator.comparing(SchemaRow::schema));
    }

    @Override
    public String getColumnName(int column) {
      return COLS[column];
    }

    @Override
    public int getRowCount() {
      return rows.size() + 1;
    }

    @Override
    public int getColumnCount() {
      return COLS.length;
    }

    @Override
    public Object getValueAt(int row, int column) {
      return switch (row) {
        case 0 -> switch (column) {
          case 0 -> "TOTAL";
          case 1 -> total;
          case 2 -> valid;
          case 3 -> total - valid;
          case 4 -> total == 0L ? "0" : SchemaRow.PERCENT_FORMAT.format((valid * 100d) / total);
          default -> null;
        };
        default -> switch (column) {
          case 0 -> rows.get(row - 1).schema();
          case 1 -> rows.get(row - 1).total();
          case 2 -> rows.get(row - 1).valid();
          case 3 -> rows.get(row - 1).invalid();
          case 4 -> rows.get(row - 1).validPercentString();
          default -> null;
        };
      };
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
      return COL_CLASSES[columnIndex];
    }
  }

}

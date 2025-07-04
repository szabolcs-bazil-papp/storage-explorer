package com.aestallon.storageexplorer.swing.ui.misc;

import java.awt.*;
import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;

public final class TableDisplayMagic {

  private TableDisplayMagic() {}

  // this is eldritch horror by today's standards, but eternal thanks to Rob Camick!
  // source: https://tips4java.wordpress.com/2008/11/10/table-column-adjuster/
  // full-feature alternative: https://github.com/tips4java/tips4java/blob/main/source/TableColumnAdjuster.java
  public static void doMagicTableColumnResizing(final JTable table) {
    for (int column = 0; column < table.getColumnCount(); column++) {
      TableColumn tableColumn = table.getColumnModel().getColumn(column);
      int preferredWidth = tableColumn.getMinWidth();
      int maxWidth = tableColumn.getMaxWidth();

      for (int row = 0; row < table.getRowCount(); row++) {
        TableCellRenderer cellRenderer = table.getCellRenderer(row, column);
        Component c = table.prepareRenderer(cellRenderer, row, column);
        int width = c.getPreferredSize().width + table.getIntercellSpacing().width;
        preferredWidth = Math.max(preferredWidth, width);

        //  We've exceeded the maximum width, no need to check other rows

        if (preferredWidth >= maxWidth) {
          preferredWidth = maxWidth;
          break;
        }
      }

      tableColumn.setPreferredWidth(preferredWidth);
    }
  }
}

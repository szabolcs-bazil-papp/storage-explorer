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

package com.aestallon.storageexplorer.swing.ui.arcscript.result;

import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.URI;
import java.text.DecimalFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import static java.util.stream.Collectors.joining;
import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import com.aestallon.storageexplorer.arcscript.engine.ArcScriptResult;
import com.aestallon.storageexplorer.client.asexport.ResultSetExporterFactory;
import com.aestallon.storageexplorer.common.util.MsgStrings;
import com.aestallon.storageexplorer.core.model.entry.StorageEntry;
import com.aestallon.storageexplorer.core.model.instance.StorageInstance;
import com.aestallon.storageexplorer.core.model.instance.dto.StorageId;
import com.aestallon.storageexplorer.core.util.Uris;
import com.aestallon.storageexplorer.swing.ui.arcscript.ArcScriptController;
import com.aestallon.storageexplorer.swing.ui.explorer.TabView;
import com.aestallon.storageexplorer.swing.ui.misc.EnumeratorWithUri;
import com.aestallon.storageexplorer.swing.ui.misc.IconProvider;
import com.aestallon.storageexplorer.swing.ui.misc.JumpToUri;
import com.aestallon.storageexplorer.swing.ui.misc.TableDisplayMagic;

public sealed interface ArcScriptResultView extends TabView {

  Logger log = LoggerFactory.getLogger(ArcScriptResultView.class);

  JComponent asComponent();

  @Override
  default List<JTextArea> textAreas() {
    return Collections.emptyList();
  }
  
  final class Initial extends JPanel implements ArcScriptResultView {

    public Initial() {
      setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));
      add(new JLabel("This is where your script results and execution plans are rendered..."));
    }

    @Override
    public JComponent asComponent() {
      return this;
    }

    @Override
    public StorageId storageId() {
      return null;
    }

  }

  final class ResultDisplayDiv extends JScrollPane implements ArcScriptResultView {

    private final ResultDisplay resultDisplay;

    public ResultDisplayDiv(ResultDisplay resultDisplay) {
      super(resultDisplay, VERTICAL_SCROLLBAR_AS_NEEDED, HORIZONTAL_SCROLLBAR_NEVER);
      this.resultDisplay = resultDisplay;
    }

    @Override
    public JComponent asComponent() {
      return null;
    }
    
    public ResultDisplay resultDisplay() {
      return resultDisplay;
    }

    @Override
    public StorageId storageId() {
      return resultDisplay.storageId();
    }
  }

  final class ResultDisplay extends JPanel implements ArcScriptResultView {
    
    private final transient StorageId storageId;
    
    public ResultDisplay(ArcScriptResult.Ok result,
                         StorageInstance storageInstance,
                         ArcScriptController controller) {
      storageId = storageInstance.id();
      setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));
      setAlignmentX(LEFT_ALIGNMENT);

      for (int i = 0; i < result.elements().size(); i++) {
        switch (result.elements().get(i)) {
          case ArcScriptResult.IndexingPerformed ip -> add(new IndexResultPanel(i, ip));
          case ArcScriptResult.QueryPerformed qp -> add(new QueryResultPanel(
              controller,
              i,
              qp,
              storageInstance));
        }
      }
    }

    @Override
    public JComponent asComponent() {
      return this;
    }
    
    @Override
    public StorageId storageId() {
      return storageId;
    }

    private static final class IndexResultPanel extends JPanel {
      public IndexResultPanel(int idx, ArcScriptResult.IndexingPerformed i) {
        setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));
        setAlignmentX(LEFT_ALIGNMENT);

        final var label = new JLabel((idx + 1) + ". Performed"
                                     + ((i.implicit() ? " implicit " : " "))
                                     + "indexing: ");
        label.putClientProperty("FlatLaf.styleClass", "h2");
        label.setAlignmentX(LEFT_ALIGNMENT);
        label.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 0));
        add(label);
        add(createOperationResultTable(i, false));
      }
    }

    private static JScrollPane createOperationResultTable(ArcScriptResult.InstructionResult res,
        /* TODO: DELETE THIS PARAM! */ final boolean render) {
      final var tableModel = switch (res) {
        case ArcScriptResult.IndexingPerformed i -> new IndexingOperationResultTableModel(i);
        case ArcScriptResult.QueryPerformed q when render -> new RenderOperationResultTableModel(q);
        case ArcScriptResult.QueryPerformed q -> new QueryOperationResultTableModel(q);
      };

      final var table = new JTable(tableModel);
      TableDisplayMagic.doMagicTableColumnResizing(table);
      table.setAutoResizeMode(JTable.AUTO_RESIZE_NEXT_COLUMN);
      table.setAlignmentX(LEFT_ALIGNMENT);
      table.setFillsViewportHeight(true);

      final JScrollPane pane = new JScrollPane(table);
      pane.setAlignmentX(LEFT_ALIGNMENT);
      pane.setPreferredSize(new Dimension(-1, table.getPreferredSize().height * 3));
      return pane;
    }


    private abstract static class OperationResultTableModel extends DefaultTableModel {

      private static final String[] COLS = { "Operation performed", "Entries found", "Time taken" };
      private static final Class<?>[] COL_CLASSES = { String.class, Integer.class, String.class };
      private static final DecimalFormat MS_FORMAT = new DecimalFormat("000.0");

      protected abstract String getOperationPerformed();

      protected abstract long getEntryCount();

      protected abstract long getTimeTaken();

      @Override
      public String getColumnName(int column) {
        return COLS[column];
      }

      @Override
      public int getRowCount() {
        return 1;
      }

      @Override
      public int getColumnCount() {
        return COLS.length;
      }

      @Override
      public Object getValueAt(int rowIndex, int columnIndex) {
        return switch (columnIndex) {
          case 0 -> getOperationPerformed();
          case 1 -> getEntryCount();
          case 2 -> {
            final var duration = Duration.ofNanos(getTimeTaken());
            yield "%ds %sms".formatted(duration.getSeconds(),
                MS_FORMAT.format(duration.getNano() / 1_000_000d));
          }
          default -> null;
        };
      }

      @Override
      public Class<?> getColumnClass(int columnIndex) {
        return COL_CLASSES[columnIndex];
      }
    }


    private static final class IndexingOperationResultTableModel extends OperationResultTableModel {
      private final ArcScriptResult.IndexingPerformed indexingPerformed;

      private IndexingOperationResultTableModel(
          final ArcScriptResult.IndexingPerformed indexingPerformed) {
        this.indexingPerformed = indexingPerformed;
      }

      @Override
      protected String getOperationPerformed() {
        return indexingPerformed.prettyPrint();
      }

      @Override
      protected long getEntryCount() {
        return indexingPerformed.entriesFound();
      }

      @Override
      protected long getTimeTaken() {
        return indexingPerformed.timeTaken();
      }

    }


    private static final class QueryOperationResultTableModel extends OperationResultTableModel {

      private final ArcScriptResult.QueryPerformed queryPerformed;

      private QueryOperationResultTableModel(final ArcScriptResult.QueryPerformed queryPerformed) {
        this.queryPerformed = queryPerformed;
      }

      @Override
      protected String getOperationPerformed() {
        return queryPerformed.prettyPrint();
      }

      @Override
      protected long getEntryCount() {
        return queryPerformed.resultSet().size();
      }

      @Override
      protected long getTimeTaken() {
        return queryPerformed.timeTaken();
      }

    }


    private static final class RenderOperationResultTableModel extends OperationResultTableModel {

      private final ArcScriptResult.QueryPerformed queryPerformed;

      private RenderOperationResultTableModel(final ArcScriptResult.QueryPerformed queryPerformed) {
        this.queryPerformed = queryPerformed;
      }

      @Override
      protected String getOperationPerformed() {
        return "render " + queryPerformed
            .resultSet().meta()
            .columns().stream()
            .map(it -> "%s as \"%s\"".formatted(it.prop(), it.title()))
            .collect(joining(","));
      }

      @Override
      protected long getEntryCount() {
        return queryPerformed.resultSet().size();
      }

      @Override
      protected long getTimeTaken() {
        return queryPerformed.resultSet().meta().timeTaken();
      }

    }


    private static final class QueryResultPanel extends JPanel {
      public QueryResultPanel(ArcScriptController controller,
                              int idx,
                              ArcScriptResult.QueryPerformed q,
                              StorageInstance storageInstance) {
        setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));
        setAlignmentX(LEFT_ALIGNMENT);

        final var resultSet = q.resultSet();
        final boolean customRender = !resultSet.meta().columns().isEmpty();

        final var label = new JLabel(getQueryPerformedLabel(idx, customRender));
        label.putClientProperty("FlatLaf.styleClass", "h2");
        label.setAlignmentX(LEFT_ALIGNMENT);
        label.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 0));
        add(label);

        add(createOperationResultTable(q, false));

        if (customRender) {
          final var renderLabel = new JLabel((idx + 1) + "/B. Retrieved columns:");
          renderLabel.putClientProperty("FlatLaf.styleClass", "h2");
          renderLabel.setAlignmentX(LEFT_ALIGNMENT);
          renderLabel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 0));
          add(renderLabel);
          add(createOperationResultTable(q, true));

          addExportToolbar(controller, resultSet);
        }

        final var tableModel = customRender
            ? new CustomisedQueryResultTableModel(resultSet)
            : new DefaultQueryResultTableModel(resultSet);
        final var table = new JTable(tableModel);
        TableDisplayMagic.doMagicTableColumnResizing(table);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_NEXT_COLUMN);
        table.setAlignmentX(LEFT_ALIGNMENT);
        table.setFillsViewportHeight(true);
        table.addMouseListener(new MouseAdapter() {

          @Override
          public void mouseClicked(MouseEvent e) {
            final Point eventLocation = e.getPoint();
            final int row = table.rowAtPoint(eventLocation);
            if (row < 0) {
              return;
            }

            if (e.getClickCount() > 1 && e.getButton() == MouseEvent.BUTTON1) {

              final URI uri = tableModel.uriAt(row);
              JumpToUri.jump(controller.eventPublisher(), uri, storageInstance);

            } else if (e.getButton() == MouseEvent.BUTTON3) {

              final int col = table.columnAtPoint(eventLocation);
              if (col < 1) { // we don't care about the first column, as it's a row number...
                return;
              }

              final var o = tableModel.getValueAt(row, col);
              if (o instanceof Icon) { // we don't care about icons either...
                return;
              }

              // this is kinda idiotic, but we clip the enveloping quotes here, this doesn't handle
              // lists and stuff:
              final String v = (o instanceof String s && s.startsWith("\"") && s.endsWith("\""))
                  ? s.substring(1, s.length() - 1)
                  : String.valueOf(o);
              new CellPopUpMenu(controller.eventPublisher(), storageInstance, v).show(
                  table,
                  eventLocation.x,
                  eventLocation.y);

            }
          }

        });

        final JScrollPane pane = new JScrollPane(table);
        pane.setAlignmentX(LEFT_ALIGNMENT);
        add(pane);
      }

      private void addExportToolbar(ArcScriptController controller,
                                    ArcScriptResult.ResultSet resultSet) {
        final var exportToolbar = new JToolBar(SwingConstants.HORIZONTAL);
        exportToolbar.setAlignmentX(LEFT_ALIGNMENT);
        exportToolbar.add(new AbstractAction(null, IconProvider.CSV) {

          @Override
          public void actionPerformed(ActionEvent e) {
            controller.export(resultSet, ResultSetExporterFactory.Target.CSV);
          }

        }).setToolTipText("Export results to CSV...");
        exportToolbar.add(new AbstractAction(null, IconProvider.JSON) {

          @Override
          public void actionPerformed(ActionEvent e) {
            controller.export(resultSet, ResultSetExporterFactory.Target.JSON);
          }

        }).setToolTipText("Export results to JSON...");

        exportToolbar.add(Box.createHorizontalGlue());
        add(exportToolbar);
      }

      private String getQueryPerformedLabel(final int idx, final boolean customRender) {
        final StringBuilder sb = new StringBuilder();
        sb.append(idx + 1);
        if (customRender) {
          sb.append("/A");
        }
        return sb.append(". Performed query:").toString();
      }

    }


    private static final class CellPopUpMenu extends JPopupMenu {

      private final ApplicationEventPublisher eventPublisher;
      private final StorageInstance storageInstance;

      private CellPopUpMenu(ApplicationEventPublisher eventPublisher,
                            StorageInstance storageInstance,
                            String cellValue) {
        super(cellValue);

        this.eventPublisher = eventPublisher;
        this.storageInstance = storageInstance;

        add(title());
        addSeparator();
        add(copyMenuItem());
        Uris.parse(cellValue).ifPresent(it -> add(jumpMenuItem(it)));
      }

      private JLabel title() {
        final var item = new JLabel("<html><strong>%s</strong></html>".formatted(
            MsgStrings.trim(getLabel(), 20)));
        item.setHorizontalAlignment(SwingConstants.CENTER);
        return item;
      }

      private JMenuItem copyMenuItem() {
        final var item = new JMenuItem("Copy");
        item.addActionListener(e -> Toolkit.getDefaultToolkit()
            .getSystemClipboard()
            .setContents(new StringSelection(getLabel()), null));
        return item;
      }

      private JMenuItem jumpMenuItem(final URI uri) {
        final var item = new JMenuItem("Jump");
        item.addActionListener(e -> JumpToUri.jump(eventPublisher, uri, storageInstance));
        return item;
      }


    }


    private static final class DefaultQueryResultTableModel
        extends AbstractTableModel
        implements EnumeratorWithUri {
      private static final String[] COLS = { "#", "Entry type", "URI (click to load)" };
      private static final Class<?>[] COL_CLASSES = { Integer.class, Icon.class, URI.class };

      private final java.util.List<StorageEntry> storageEntries;

      private DefaultQueryResultTableModel(ArcScriptResult.ResultSet resultSet) {
        this.storageEntries = new ArrayList<>(resultSet.entries());
      }

      @Override
      public String getColumnName(int column) {
        return COLS[column];
      }

      @Override
      public int getRowCount() {
        return storageEntries.size();
      }

      @Override
      public int getColumnCount() {
        return COLS.length;
      }

      @Override
      public Object getValueAt(int rowIndex, int columnIndex) {
        final var e = storageEntries.get(rowIndex);
        return switch (columnIndex) {
          case 0 -> rowIndex + 1;
          case 1 -> IconProvider.getIconForStorageEntry(e);
          case 2 -> e.uri();
          default -> null;
        };
      }

      @Override
      public Class<?> getColumnClass(int columnIndex) {
        return COL_CLASSES[columnIndex];
      }

      @Override
      public URI uriAt(int idx) {
        return storageEntries.get(idx).uri();
      }
    }

  }


  final class CustomisedQueryResultTableModel
      extends AbstractTableModel
      implements EnumeratorWithUri {

    private final java.util.List<ArcScriptResult.ColumnDescriptor> columnDescriptors;
    private final List<ArcScriptResult.QueryResultRow> rows;

    private CustomisedQueryResultTableModel(ArcScriptResult.ResultSet resultSet) {
      columnDescriptors = resultSet.meta().columns();
      rows = resultSet.rows();
    }

    @Override
    public String getColumnName(int column) {
      if (column == 0) {
        return "#";
      }

      return columnDescriptors.get(column - 1).title();
    }

    @Override
    public int getRowCount() {
      return rows.size();
    }

    @Override
    public int getColumnCount() {
      return columnDescriptors.size() + 1;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
      if (columnIndex == 0) {
        return rowIndex + 1;
      }

      final String prop = columnDescriptors.get(columnIndex - 1).prop();
      final var cell = rows.get(rowIndex).cells().get(prop);
      if (cell == null) {
        log.error("No cell found for property '{}' in row {}", prop, rows.get(rowIndex));
        return "";
      }

      return cell.displayString();
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
      return (columnIndex == 0) ? Integer.class : String.class;
    }

    @Override
    public URI uriAt(int idx) {
      return rows.get(idx).entry().uri();
    }
  }
}


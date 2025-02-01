package com.aestallon.storageexplorer.swing.ui.commander.arcscript;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.URI;
import java.text.DecimalFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import org.fife.ui.rsyntaxtextarea.RSyntaxDocument;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.parser.AbstractParser;
import org.fife.ui.rsyntaxtextarea.parser.DefaultParseResult;
import org.fife.ui.rsyntaxtextarea.parser.DefaultParserNotice;
import org.fife.ui.rsyntaxtextarea.parser.ParseResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import com.aestallon.storageexplorer.arcscript.api.Arc;
import com.aestallon.storageexplorer.arcscript.engine.ArcScriptResult;
import com.aestallon.storageexplorer.common.event.bgwork.BackgroundWorkCompletedEvent;
import com.aestallon.storageexplorer.common.event.bgwork.BackgroundWorkStartedEvent;
import com.aestallon.storageexplorer.core.event.StorageReindexed;
import com.aestallon.storageexplorer.core.model.entry.StorageEntry;
import com.aestallon.storageexplorer.core.model.instance.StorageInstance;
import com.aestallon.storageexplorer.core.userconfig.service.StoredArcScript;
import com.aestallon.storageexplorer.swing.ui.misc.EnumeratorWithUri;
import com.aestallon.storageexplorer.swing.ui.misc.IconProvider;
import com.aestallon.storageexplorer.swing.ui.misc.JumpToUri;

public class ArcScriptView extends JPanel {

  private static final Logger log = LoggerFactory.getLogger(ArcScriptView.class);
  private final ArcScriptController controller;
  private final StorageInstance storageInstance;
  private StoredArcScript storedArcScript;

  private final JSplitPane content;
  private ScriptResultView scriptResultView;
  private final JTextArea editor;

  private final AbstractAction saveAction;
  private final AbstractAction playAction;

  private ErrorMarker compilationError;

  ArcScriptView(ArcScriptController controller,
                StorageInstance storageInstance,
                StoredArcScript storedArcScript) {
    this.controller = controller;
    this.storageInstance = storageInstance;
    this.storedArcScript = storedArcScript;

    BoxLayout mgr = new BoxLayout(this, BoxLayout.PAGE_AXIS);
    setLayout(mgr);

    final var arcScriptTextArea = controller
        .arcScriptTextareaFactory()
        .create(storedArcScript.script());
    editor = arcScriptTextArea.textArea();
    installPlayAction();
    installSaveAction();

    final var toolbar = new JToolBar(SwingConstants.HORIZONTAL);
    saveAction = new AbstractAction(null, IconProvider.SAVE) {
      @Override
      public void actionPerformed(ActionEvent e) {
        save();
      }
    };
    disableSave();
    toolbar.add(saveAction);

    playAction = new AbstractAction(null, IconProvider.PLAY) {

      @Override
      public void actionPerformed(ActionEvent e) {
        play();
      }
    };
    toolbar.add(playAction);

    final var renameAction = new AbstractAction(null, IconProvider.EDIT) {

      @Override
      public void actionPerformed(ActionEvent e) {
        final var oldTitle = ArcScriptView.this.storedArcScript.title();
        final var newTitle = JOptionPane.showInputDialog(
            ArcScriptView.this,
            "Enter title for this script:",
            oldTitle);
        if (Objects.equals(oldTitle, newTitle)) {
          return;
        }
        controller.rename(ArcScriptView.this, newTitle);
      }
    };
    toolbar.add(renameAction);

    Box b1 = new Box(BoxLayout.X_AXIS);
    b1.add(toolbar);
    b1.add(Box.createGlue());
    add(b1);

    Box b2 = new Box(BoxLayout.X_AXIS);
    content = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
    content.setLeftComponent(arcScriptTextArea.scrollPane());

    scriptResultView = new Initial();
    content.setRightComponent(scriptResultView.asComponent());
    content.setDividerLocation(0.5);
    b2.add(content);
    b2.add(Box.createGlue());
    add(b2);
    updateUI();
  }

  StoredArcScript storedArcScript() {
    return storedArcScript;
  }

  void storedArcScript(StoredArcScript storedArcScript) {
    this.storedArcScript = storedArcScript;
  }

  private void installPlayAction() {
    final var ctrlEnter = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, KeyEvent.CTRL_DOWN_MASK);
    editor.getInputMap().put(ctrlEnter, "play");
    editor.getActionMap().put("play", new AbstractAction() {

      @Override
      public void actionPerformed(ActionEvent e) {
        play();
      }

    });
  }

  private void installSaveAction() {
    final var ctrlS = KeyStroke.getKeyStroke(KeyEvent.VK_S, KeyEvent.CTRL_DOWN_MASK);
    editor.getInputMap().put(ctrlS, "save");
    editor.getActionMap().put("save", new AbstractAction() {

      @Override
      public void actionPerformed(ActionEvent e) {
        save();
      }

    });
    editor.getDocument().addDocumentListener(new DocumentListener() {

      @Override
      public void insertUpdate(DocumentEvent e) {
        checkChanges();
      }

      @Override
      public void removeUpdate(DocumentEvent e) {
        checkChanges();
      }

      @Override
      public void changedUpdate(DocumentEvent e) {
        checkChanges();
      }

      private void checkChanges() {
        String text = editor.getText();
        text = (text == null) ? "" : text;

        final String oldText = storedArcScript().script();
        saveAction.setEnabled(!text.equals(oldText));
      }
    });
  }

  private void play() {
    editor.setEnabled(false);
    playAction.setEnabled(false);
    removeCompilationError();

    CompletableFuture.runAsync(() -> {
      controller.eventPublisher().publishEvent(
          new BackgroundWorkStartedEvent("Running ArcScript on " + storageInstance.name()));
      switch (Arc.evaluate(editor.getText(), storageInstance)) {
        case ArcScriptResult.CompilationError cErr -> showCompilationError(cErr);
        case ArcScriptResult.ImpermissibleInstruction iErr ->
            showErr("Impermissible instruction", iErr.msg());
        case ArcScriptResult.UnknownError uErr -> showErr("Unknown error", uErr.msg());
        case ArcScriptResult.Ok ok -> {
          if (ok.elements().stream()
              .anyMatch(it -> it instanceof ArcScriptResult.IndexingPerformed)) {
            controller.eventPublisher().publishEvent(new StorageReindexed(storageInstance));
          }
          showOk(ok);
        }
      }
    });
  }

  private void showCompilationError(ArcScriptResult.CompilationError error) {
    editor.setEnabled(true);
    playAction.setEnabled(true);
    controller.eventPublisher().publishEvent(new BackgroundWorkCompletedEvent(
        BackgroundWorkCompletedEvent.BackgroundWorkResult.ERR));
    SwingUtilities.invokeLater(() -> {
      if (!(editor instanceof RSyntaxTextArea r)) {
        return;
      }

      compilationError = new ErrorMarker(error);
      r.addParser(compilationError);
    });

  }

  private void removeCompilationError() {
    if (compilationError == null) {
      return;
    }

    if (!(editor instanceof RSyntaxTextArea r)) {
      return;
    }

    r.removeParser(compilationError);
  }

  private void showErr(String title, String msg) {
    controller.eventPublisher().publishEvent(new BackgroundWorkCompletedEvent(
        BackgroundWorkCompletedEvent.BackgroundWorkResult.ERR));
    SwingUtilities.invokeLater(() -> {
      JOptionPane.showMessageDialog(
          this,
          msg,
          title,
          JOptionPane.ERROR_MESSAGE);
      editor.setEnabled(true);
      playAction.setEnabled(true);
    });
  }

  private void showOk(ArcScriptResult.Ok ok) {
    controller.eventPublisher().publishEvent(new BackgroundWorkCompletedEvent(
        BackgroundWorkCompletedEvent.BackgroundWorkResult.OK));
    SwingUtilities.invokeLater(() -> {
      int dividerLocation = content.getDividerLocation();
      scriptResultView = new ResultDisplay(ok, controller.eventPublisher(), storageInstance);
      final var scrollPlane = new JScrollPane(
          scriptResultView.asComponent(),
          ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
          ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
      content.setRightComponent(scrollPlane);
      content.setDividerLocation(dividerLocation);
      editor.setEnabled(true);
      playAction.setEnabled(true);
    });
  }

  private void save() {
    controller.save(this, editor.getText());
  }

  void disableSave() {
    saveAction.setEnabled(false);
  }

  public JTextArea editor() {
    return editor;
  }


  protected sealed interface ScriptResultView {

    JComponent asComponent();

  }


  private final static class Initial extends JPanel implements ScriptResultView {

    public Initial() {
      setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));
      add(new JLabel("This is where your script results and execution plans are rendered..."));
    }

    @Override
    public JComponent asComponent() {
      return this;
    }

  }


  private final static class ResultDisplay extends JPanel implements ScriptResultView {
    public ResultDisplay(ArcScriptResult.Ok result,
                         ApplicationEventPublisher eventPublisher,
                         StorageInstance storageInstance) {
      setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));
      setAlignmentX(LEFT_ALIGNMENT);

      for (int i = 0; i < result.elements().size(); i++) {
        switch (result.elements().get(i)) {
          case ArcScriptResult.IndexingPerformed ip -> add(new IndexResultPanel(i, ip));
          case ArcScriptResult.QueryPerformed qp -> add(new QueryResultPanel(
              i,
              qp,
              eventPublisher,
              storageInstance));
        }
      }
    }

    @Override
    public JComponent asComponent() {
      return this;
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
        add(createOperationResultTable(i));
      }
    }

    private static JScrollPane createOperationResultTable(ArcScriptResult.InstructionResult res) {
      final var tableModel = switch (res) {
        case ArcScriptResult.IndexingPerformed i -> new OperationResultTableModel(i);
        case ArcScriptResult.QueryPerformed q -> new OperationResultTableModel(q);
      };

      final var table = new JTable(tableModel);
      doMagicTableColumnResizing(table);
      table.setAutoResizeMode(JTable.AUTO_RESIZE_NEXT_COLUMN);
      table.setAlignmentX(LEFT_ALIGNMENT);
      table.setFillsViewportHeight(true);

      final JScrollPane pane = new JScrollPane(table);
      pane.setAlignmentX(LEFT_ALIGNMENT);
      pane.setPreferredSize(new Dimension(-1, table.getPreferredSize().height * 3));
      return pane;
    }


    private static final class OperationResultTableModel extends DefaultTableModel {

      private static final String[] COLS = { "Operation performed", "Entries found", "Time taken" };
      private static final Class<?>[] COL_CLASSES = { String.class, Integer.class, String.class };
      private static final DecimalFormat MS_FORMAT = new DecimalFormat("000.0");

      private final ArcScriptResult.IndexingPerformed indexingPerformed;
      private final ArcScriptResult.QueryPerformed queryPerformed;

      private OperationResultTableModel(ArcScriptResult.IndexingPerformed result) {
        this(result, null);
      }

      private OperationResultTableModel(ArcScriptResult.QueryPerformed result) {
        this(null, result);
      }

      private OperationResultTableModel(ArcScriptResult.IndexingPerformed indexingPerformed,
                                        ArcScriptResult.QueryPerformed queryPerformed) {
        this.indexingPerformed = indexingPerformed;
        this.queryPerformed = queryPerformed;
      }

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
        if (indexingPerformed != null) {
          return switch (columnIndex) {
            case 0 -> indexingPerformed.prettyPrint();
            case 1 -> indexingPerformed.entriesFound();
            case 2 -> {
              final var duration = Duration.ofNanos(indexingPerformed.timeTaken());
              yield "%ds %sms".formatted(duration.getSeconds(),
                  MS_FORMAT.format(duration.getNano() / 1_000_000d));
            }
            default -> null;
          };
        } else {
          return switch (columnIndex) {
            case 0 -> queryPerformed.prettyPrint();
            case 1 -> queryPerformed.resultSet().size();
            case 2 -> {
              final var duration = Duration.ofNanos(queryPerformed.timeTaken());
              yield "%ds %sms".formatted(duration.getSeconds(),
                  MS_FORMAT.format(duration.getNano() / 1_000_000d));
            }
            default -> null;
          };
        }

      }

      @Override
      public Class<?> getColumnClass(int columnIndex) {
        return COL_CLASSES[columnIndex];
      }
    }


    private static final class QueryResultPanel extends JPanel {
      public QueryResultPanel(int idx,
                              ArcScriptResult.QueryPerformed q,
                              ApplicationEventPublisher eventPublisher,
                              StorageInstance storageInstance) {
        setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));
        setAlignmentX(LEFT_ALIGNMENT);

        final var label = new JLabel((idx + 1) + ". Performed query:");
        label.putClientProperty("FlatLaf.styleClass", "h2");
        label.setAlignmentX(LEFT_ALIGNMENT);
        label.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 0));
        add(label);
        add(createOperationResultTable(q));

        final var resultSet = q.resultSet();
        final var tableModel = resultSet.meta().columns().isEmpty()
            ? new DefaultQueryResultTableModel(resultSet)
            : new CustomisedQueryResultTableModel(resultSet);
        final var table = new JTable(tableModel);
        doMagicTableColumnResizing(table);
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

            final URI uri = tableModel.uriAt(row);
            JumpToUri.jump(eventPublisher, uri, storageInstance);
          }

        });

        final JScrollPane pane = new JScrollPane(table);
        pane.setAlignmentX(LEFT_ALIGNMENT);
        add(pane);
      }
    }


    private static final class DefaultQueryResultTableModel
        extends AbstractTableModel
        implements EnumeratorWithUri {
      private static final String[] COLS = { "#", "Entry type", "URI (click to load)" };
      private static final Class<?>[] COL_CLASSES = { Integer.class, Icon.class, URI.class };

      private final List<StorageEntry> storageEntries;

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


  private static final class CustomisedQueryResultTableModel
      extends AbstractTableModel
      implements EnumeratorWithUri {

    private final List<ArcScriptResult.ColumnDescriptor> columnDescriptors;
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

      return cell.value();
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


  private static final class ErrorMarker extends AbstractParser {

    private final ArcScriptResult.CompilationError error;

    private ErrorMarker(ArcScriptResult.CompilationError error) {
      this.error = error;
    }

    @Override
    public ParseResult parse(RSyntaxDocument doc, String style) {
      DefaultParseResult result = new DefaultParseResult(this);
      final var line = error.line() < 1 ? 0 : error.line() - 1;
      result.addNotice(new DefaultParserNotice(this, error.msg(), line));
      return result;
    }

  }

  // this is eldritch horror by today's standards, but eternal thanks to Rob Camick!
  // source: https://tips4java.wordpress.com/2008/11/10/table-column-adjuster/
  // full-feature alternative: https://github.com/tips4java/tips4java/blob/main/source/TableColumnAdjuster.java
  private static void doMagicTableColumnResizing(final JTable table) {
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

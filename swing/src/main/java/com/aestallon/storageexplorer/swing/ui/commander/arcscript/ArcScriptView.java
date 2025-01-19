package com.aestallon.storageexplorer.swing.ui.commander.arcscript;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.URI;
import java.text.DecimalFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableModel;
import org.springframework.context.ApplicationEventPublisher;
import com.aestallon.storageexplorer.arcscript.api.Arc;
import com.aestallon.storageexplorer.arcscript.engine.ArcScriptResult;
import com.aestallon.storageexplorer.arcscript.internal.query.QueryElement;
import com.aestallon.storageexplorer.common.event.bgwork.BackgroundWorkCompletedEvent;
import com.aestallon.storageexplorer.common.event.bgwork.BackgroundWorkStartedEvent;
import com.aestallon.storageexplorer.common.util.Uris;
import com.aestallon.storageexplorer.core.event.StorageReindexed;
import com.aestallon.storageexplorer.core.model.entry.StorageEntry;
import com.aestallon.storageexplorer.core.model.instance.StorageInstance;
import com.aestallon.storageexplorer.core.userconfig.service.UserConfigService;
import com.aestallon.storageexplorer.swing.ui.misc.IconProvider;
import com.aestallon.storageexplorer.swing.ui.misc.JumpToUri;
import com.aestallon.storageexplorer.swing.ui.misc.MonospaceFontProvider;

public class ArcScriptView extends JPanel {

  private final ApplicationEventPublisher applicationEventPublisher;
  private final UserConfigService userConfigService;
  private final MonospaceFontProvider monospaceFontProvider;
  private final StorageInstance storageInstance;

  private final JSplitPane content;
  private ScriptResultView scriptResultView;
  private final ArcScriptEditor editor;

  private AbstractAction saveAction;
  private AbstractAction playAction;

  public ArcScriptView(ApplicationEventPublisher applicationEventPublisher,
                       UserConfigService userConfigService,
                       MonospaceFontProvider monospaceFontProvider,
                       StorageInstance storageInstance) {
    this.applicationEventPublisher = applicationEventPublisher;
    this.userConfigService = userConfigService;
    this.monospaceFontProvider = monospaceFontProvider;
    this.storageInstance = storageInstance;

    BoxLayout mgr = new BoxLayout(this, BoxLayout.PAGE_AXIS);
    setLayout(mgr);

    editor = new ArcScriptEditor();
    editor.setFont(monospaceFontProvider.getFont());
    monospaceFontProvider.applyFontSizeChangeAction(editor);
    final var editorView = new ArcScriptEditorView(editor);

    final var toolbar = new JToolBar(SwingConstants.HORIZONTAL);
    saveAction = new AbstractAction(null, IconProvider.SAVE) {
      @Override
      public void actionPerformed(ActionEvent e) {
        save();
      }
    };

    toolbar.add(saveAction);
    playAction = new AbstractAction(null, IconProvider.PLAY) {

      @Override
      public void actionPerformed(ActionEvent e) {
        play();
      }
    };

    toolbar.add(playAction);
    Box b1 = new Box(BoxLayout.X_AXIS);
    b1.add(toolbar);
    b1.add(Box.createGlue());
    add(b1);

    Box b2 = new Box(BoxLayout.X_AXIS);
    content = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
    content.setLeftComponent(editorView);

    scriptResultView = new Initial();
    content.setRightComponent(scriptResultView.asComponent());
    content.setDividerLocation(0.5);
    b2.add(content);
    b2.add(Box.createGlue());
    add(b2);
    updateUI();
  }

  private void play() {
    editor.setEnabled(false);
    playAction.setEnabled(false);

    CompletableFuture.runAsync(() -> {
      applicationEventPublisher.publishEvent(
          new BackgroundWorkStartedEvent("Running ArcScript on " + storageInstance.name()));
      switch (Arc.evaluate(editor.getText(), storageInstance)) {
        case ArcScriptResult.CompilationError cErr -> showErr("Compilation error", cErr.msg());
        case ArcScriptResult.ImpermissibleInstruction iErr ->
            showErr("Impermissible instruction", iErr.msg());
        case ArcScriptResult.UnknownError uErr -> showErr("Unknown error", uErr.msg());
        case ArcScriptResult.Ok ok -> {
          if (ok.elements().stream()
              .anyMatch(it -> it instanceof ArcScriptResult.IndexingPerformed)) {
            applicationEventPublisher.publishEvent(new StorageReindexed(storageInstance));
          }
          showOk(ok);
        }
      }
    });
  }

  private void showErr(String title, String msg) {
    applicationEventPublisher.publishEvent(new BackgroundWorkCompletedEvent(
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
    applicationEventPublisher.publishEvent(new BackgroundWorkCompletedEvent(
        BackgroundWorkCompletedEvent.BackgroundWorkResult.OK));
    SwingUtilities.invokeLater(() -> {
      int dividerLocation = content.getDividerLocation();
      scriptResultView = new ResultDisplay(ok, applicationEventPublisher, storageInstance);
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

  }

  private static final class ArcScriptEditor extends JTextArea {
    public ArcScriptEditor() {
    }
  }


  private static final class ArcScriptEditorView extends JScrollPane {
    public ArcScriptEditorView(ArcScriptEditor editor) {
      super(editor, VERTICAL_SCROLLBAR_AS_NEEDED, HORIZONTAL_SCROLLBAR_AS_NEEDED);
    }
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
      table.setAutoResizeMode(JTable.AUTO_RESIZE_NEXT_COLUMN);
      table.setAlignmentX(LEFT_ALIGNMENT);
      table.setFillsViewportHeight(true);

      final JScrollPane pane = new JScrollPane(table);
      pane.setAlignmentX(LEFT_ALIGNMENT);
      pane.setPreferredSize(new Dimension(-1, 100));
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
        add(label);
        add(createOperationResultTable(q));
        
        final var tableModel = new QueryResultTableModel(q);
        final var table = new JTable(tableModel);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_NEXT_COLUMN);
        table.setAlignmentX(LEFT_ALIGNMENT);
        table.setFillsViewportHeight(true);
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
            if (value instanceof URI uri) {
              JumpToUri.jump(eventPublisher, uri, storageInstance);
            }
          }

        });

        final JScrollPane pane = new JScrollPane(table);
        pane.setAlignmentX(LEFT_ALIGNMENT);
        add(pane);
      }
    }


    private static final class QueryResultTableModel extends AbstractTableModel {
      private static final String[] COLS = { "Entry type", "URI (click to load)" };
      private static final Class<?>[] COL_CLASSES = { Icon.class, URI.class };

      private final List<StorageEntry> storageEntries;

      private QueryResultTableModel(ArcScriptResult.QueryPerformed result) {
        this.storageEntries = new ArrayList<>(result.resultSet());
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
          case 0 -> IconProvider.getIconForStorageEntry(e);
          case 1 -> e.uri();
          default -> null;
        };
      }

      @Override
      public Class<?> getColumnClass(int columnIndex) {
        return COL_CLASSES[columnIndex];
      }
    }

  }

}

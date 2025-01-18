package com.aestallon.storageexplorer.swing.ui.commander.arcscript;

import java.awt.event.ActionEvent;
import javax.swing.*;
import org.springframework.context.ApplicationEventPublisher;
import com.aestallon.storageexplorer.arcscript.api.Arc;
import com.aestallon.storageexplorer.arcscript.engine.ArcScriptResult;
import com.aestallon.storageexplorer.core.event.StorageReindexed;
import com.aestallon.storageexplorer.core.model.instance.StorageInstance;
import com.aestallon.storageexplorer.core.userconfig.service.UserConfigService;
import com.aestallon.storageexplorer.swing.ui.misc.IconProvider;
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
    content.setRightComponent(scriptResultView);
    content.setDividerLocation(0.5);
    b2.add(content);
    b2.add(Box.createGlue());
    add(b2);
    updateUI();
  }

  private void play() {
    editor.setEnabled(false);
    playAction.setEnabled(false);

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

    editor.setEnabled(true);
    playAction.setEnabled(true);
  }

  private void showErr(String title, String msg) {
    JOptionPane.showMessageDialog(
        this,
        msg,
        title,
        JOptionPane.ERROR_MESSAGE);
  }

  private void showOk(ArcScriptResult.Ok ok) {
    scriptResultView = new ResultDisplay(ok);
    content.setRightComponent(scriptResultView);
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


  protected abstract sealed static class ScriptResultView extends JPanel {

  }


  private final static class Initial extends ScriptResultView {

    public Initial() {
      setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));
      add(new JLabel("This is where your script results and execution plans are rendered..."));
    }
  }


  private final static class ResultDisplay extends ScriptResultView {
    public ResultDisplay(ArcScriptResult.Ok result) {
      setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));

      for (final var action : result.elements()) {
        switch (action) {
          case ArcScriptResult.IndexingPerformed i -> {
            final var label =
                new JLabel("Performed" + ((i.implicit() ? " implicit " : " ")) +
                           "indexing: " + i.prettyPrint() + " (Entries found: "
                           + i.entriesFound() + ")");
            add(label);
          }
          case ArcScriptResult.QueryPerformed q -> {
            final var label = new JLabel("Performed query: " + q.prettyPrint());
            add(label);
            for (final var e : q.resultSet()) {
              add(new JLabel(e.uri().toString()));
            }
          }
        }
      }
    }
  }

}

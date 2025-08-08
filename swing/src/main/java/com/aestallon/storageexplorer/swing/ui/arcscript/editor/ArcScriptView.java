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

package com.aestallon.storageexplorer.swing.ui.arcscript.editor;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import org.fife.ui.rsyntaxtextarea.RSyntaxDocument;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.parser.AbstractParser;
import org.fife.ui.rsyntaxtextarea.parser.DefaultParseResult;
import org.fife.ui.rsyntaxtextarea.parser.DefaultParserNotice;
import org.fife.ui.rsyntaxtextarea.parser.ParseResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.aestallon.storageexplorer.arcscript.api.Arc;
import com.aestallon.storageexplorer.arcscript.engine.ArcScriptResult;
import com.aestallon.storageexplorer.client.userconfig.service.StoredArcScript;
import com.aestallon.storageexplorer.common.event.bgwork.BackgroundWorkCompletedEvent;
import com.aestallon.storageexplorer.common.event.bgwork.BackgroundWorkStartedEvent;
import com.aestallon.storageexplorer.core.model.instance.StorageInstance;
import com.aestallon.storageexplorer.core.model.instance.dto.StorageId;
import com.aestallon.storageexplorer.swing.ui.arcscript.ArcScriptController;
import com.aestallon.storageexplorer.swing.ui.arcscript.tree.ArcScriptSelectorTree;
import com.aestallon.storageexplorer.swing.ui.explorer.TabView;
import com.aestallon.storageexplorer.swing.ui.explorer.TabViewThumbnail;
import com.aestallon.storageexplorer.swing.ui.misc.IconProvider;
import com.aestallon.storageexplorer.swing.ui.tree.TreeEntityLocator;

public class ArcScriptView extends JPanel implements TabView {

  private static final Logger log = LoggerFactory.getLogger(ArcScriptView.class);

  private final ArcScriptController controller;
  final StorageInstance storageInstance;
  private StoredArcScript storedArcScript;

  private final JTextArea editor;

  private final AbstractAction saveAction;
  private final AbstractAction playAction;

  private transient ErrorMarker compilationError;

  public ArcScriptView(ArcScriptController controller,
                       StorageInstance storageInstance,
                       StoredArcScript storedArcScript) {
    this.controller = controller;
    this.storageInstance = storageInstance;
    this.storedArcScript = storedArcScript;

    BorderLayout mgr = new BorderLayout();
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
    toolbar.add(saveAction).setToolTipText("Save your changes (Ctrl+S)");

    playAction = new AbstractAction(null, IconProvider.PLAY) {

      @Override
      public void actionPerformed(ActionEvent e) {
        play();
      }
    };
    toolbar.add(playAction).setToolTipText("Run (Ctrl+Enter)");

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
    toolbar.add(renameAction).setToolTipText("Rename script...");

    final var deleteAction = new AbstractAction(null, IconProvider.DELETE) {

      @Override
      public void actionPerformed(ActionEvent e) {
        final int answer = JOptionPane.showConfirmDialog(
            ArcScriptView.this,
            "Are you sure you want to delete " + storedArcScript.title() + "?",
            "Delete " + storedArcScript.title(),
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE);
        if (answer == JOptionPane.YES_OPTION) {
          delete();
        }
      }

    };
    toolbar.add(deleteAction).setToolTipText("Permanently delete this script...");

    Box b1 = new Box(BoxLayout.X_AXIS);
    b1.add(toolbar);
    b1.add(Box.createGlue());
    add(b1, BorderLayout.NORTH);
    add(arcScriptTextArea.scrollPane(), BorderLayout.CENTER);
    // updateUI();
  }

  @Override
  public StorageId storageId() {
    return storageInstance.id();
  }

  @Override
  public List<JTextArea> textAreas() {
    return List.of(editor);
  }

  @Override
  public JComponent asComponent() {
    return this;
  }

  @Override
  public TabViewThumbnail thumbnail() {
    return new TabViewThumbnail(
        IconProvider.ARC_SCRIPT,
        storedArcScript.title(),
        storageInstance.name(),
        new TreeEntityLocator(
            "ArcScript Tree",
            new ArcScriptSelectorTree.ArcScriptNodeLocator(storageId(), storedArcScript.title())));
  }

  public StoredArcScript storedArcScript() {
    return storedArcScript;
  }

  public void storedArcScript(StoredArcScript storedArcScript) {
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
          controller.eventPublisher().publishEvent(BackgroundWorkCompletedEvent.ok());
          controller.renderResult(storedArcScript.title(), storageInstance, ok);
          SwingUtilities.invokeLater(() -> {
            editor.setEnabled(true);
            playAction.setEnabled(true);
          });
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

  private void save() {
    controller.save(this, editor.getText());
  }

  public void disableSave() {
    saveAction.setEnabled(false);
  }

  private void delete() {
    controller.delete(this);
  }

  public JTextArea editor() {
    return editor;
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

}

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

package com.aestallon.storageexplorer.swing.ui.commander.arcscript;

import java.awt.*;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import org.apache.groovy.parser.antlr4.GroovyParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import com.aestallon.storageexplorer.core.event.StorageImportEvent;
import com.aestallon.storageexplorer.core.event.StorageIndexDiscardedEvent;
import com.aestallon.storageexplorer.core.model.instance.StorageInstance;
import com.aestallon.storageexplorer.core.model.instance.dto.StorageId;
import com.aestallon.storageexplorer.core.service.StorageInstanceProvider;
import com.aestallon.storageexplorer.core.userconfig.service.ArcScriptFileService;
import com.aestallon.storageexplorer.core.userconfig.service.StoredArcScript;
import com.aestallon.storageexplorer.core.userconfig.service.UserConfigService;
import com.aestallon.storageexplorer.swing.ui.event.LafChanged;
import com.aestallon.storageexplorer.swing.ui.event.StorageInstanceRenamed;
import com.aestallon.storageexplorer.swing.ui.misc.IconProvider;
import com.aestallon.storageexplorer.swing.ui.misc.MonospaceFontProvider;
import com.aestallon.storageexplorer.swing.ui.misc.RSyntaxTextAreaThemeProvider;

@Service
public class ArcScriptController {

  private static final Logger log = LoggerFactory.getLogger(ArcScriptController.class);

  private final StorageInstanceProvider storageInstanceProvider;
  private final ApplicationEventPublisher applicationEventPublisher;
  private final UserConfigService userConfigService;
  private final RSyntaxTextAreaThemeProvider themeProvider;
  private final MonospaceFontProvider monospaceFontProvider;
  private final ArcScriptTextareaFactory arcScriptTextareaFactory;
  private final List<ArcScriptView> arcScriptViews = new ArrayList<>();

  private ArcScriptContainerView containerView;

  public ArcScriptController(final StorageInstanceProvider storageInstanceProvider,
                             final ApplicationEventPublisher applicationEventPublisher,
                             final UserConfigService userConfigService,
                             final RSyntaxTextAreaThemeProvider themeProvider,
                             final MonospaceFontProvider monospaceFontProvider) {
    this.storageInstanceProvider = storageInstanceProvider;
    this.applicationEventPublisher = applicationEventPublisher;
    this.userConfigService = userConfigService;
    this.themeProvider = themeProvider;
    this.monospaceFontProvider = monospaceFontProvider;

    arcScriptTextareaFactory = new ArcScriptTextareaFactory(themeProvider, monospaceFontProvider);
  }

  void containerView(ArcScriptContainerView containerView) {
    this.containerView = containerView;
  }

  UserConfigService userConfigService() {
    return userConfigService;
  }

  RSyntaxTextAreaThemeProvider themeProvider() {
    return themeProvider;
  }

  MonospaceFontProvider monospaceFontProvider() {
    return monospaceFontProvider;
  }

  ArcScriptTextareaFactory arcScriptTextareaFactory() {
    return arcScriptTextareaFactory;
  }

  ApplicationEventPublisher eventPublisher() {
    return applicationEventPublisher;
  }

  void add(ArcScriptView arcScriptView) {
    arcScriptViews.add(arcScriptView);
  }

  @EventListener
  public void onFontSizeChanged(
      @SuppressWarnings("unused") MonospaceFontProvider.FontSizeChange fontSizeChange) {
    SwingUtilities.invokeLater(() -> {
      final Font font = monospaceFontProvider.getFont();
      arcScriptViews.forEach(it -> it.editor().setFont(font));
    });
  }

  @EventListener
  public void onLafChanged(final LafChanged e) {
    SwingUtilities.invokeLater(() -> {
      themeProvider.setCurrentTheme(e.laf());
      final var font = monospaceFontProvider.getFont();
      arcScriptViews.forEach(it -> {
        themeProvider.applyCurrentTheme(it.editor());
        it.editor().setFont(font);
      });
    });
  }

  @EventListener
  public void onStorageImported(StorageImportEvent event) {
    SwingUtilities.invokeLater(() -> {

      final StorageInstance storageInstance = event.storageInstance();
      final List<String> loadableScripts = userConfigService
          .arcScriptFileService()
          .checkAllAvailable()
          .getOrDefault(storageInstance.id(), new ArrayList<>());
      containerView.newScriptView.content.addTree();
      containerView.newScriptView.content.tree.addStorage(
          storageInstance,
          loadableScripts);
      containerView.newScriptView.revalidate();
      containerView.newScriptView.repaint();
    });
  }

  @EventListener
  public void onStorageDeleted(StorageIndexDiscardedEvent event) {
    SwingUtilities.invokeLater(() -> {

      final StorageInstance storageInstance = event.storageInstance();
      containerView.newScriptView.content.tree.removeStorage(storageInstance.id());
      if (containerView.newScriptView.content.tree.isEmpty()) {
        containerView.newScriptView.content.addEmptyMessage();
        containerView.newScriptView.revalidate();
        containerView.newScriptView.repaint();
      }

      List<ArcScriptView> viewsToRemove = arcScriptViews.stream()
          .filter(it -> it.storageInstance.equals(storageInstance))
          .toList();
      viewsToRemove.forEach(it -> {
        arcScriptViews.remove(it);
        containerView.remove(it);
      });
    });
  }

  @EventListener
  public void onStorageInstanceRenamed(final StorageInstanceRenamed event) {
    SwingUtilities.invokeLater(() -> containerView
        .newScriptView
        .content
        .tree
        .storageRenamed(event.storageInstance().id()));
  }

  List<StorageInstance> availableStorageInstances() {
    return storageInstanceProvider.provide().toList();
  }

  void rename(ArcScriptView arcScriptView, String title) {
    final StoredArcScript storedArcScript = arcScriptView.storedArcScript();
    final var result = userConfigService.arcScriptFileService().rename(storedArcScript, title);
    switch (result) {
      case ArcScriptFileService.ArcScriptIoResult.Ok(StoredArcScript arcScript) -> {
        arcScriptView.storedArcScript(arcScript);
        final int idx = containerView.indexOfComponent(arcScriptView);
        containerView.setTitleAt(idx, arcScript.title());
      }
      case ArcScriptFileService.ArcScriptIoResult.Err(String msg) -> JOptionPane.showMessageDialog(
          arcScriptView,
          msg,
          "Renaming failed",
          JOptionPane.ERROR_MESSAGE,
          IconProvider.ERROR);
    }
  }

  void save(ArcScriptView arcScriptView, String text) {
    final StoredArcScript storedArcScript = arcScriptView.storedArcScript();
    final var result = userConfigService.arcScriptFileService().save(storedArcScript, text);
    switch (result) {
      case ArcScriptFileService.ArcScriptIoResult.Ok(StoredArcScript arcScript) -> {
        arcScriptView.storedArcScript(arcScript);
        arcScriptView.disableSave();
      }
      case ArcScriptFileService.ArcScriptIoResult.Err(String msg) -> JOptionPane.showMessageDialog(
          arcScriptView,
          msg,
          "Save failed",
          JOptionPane.ERROR_MESSAGE,
          IconProvider.ERROR
      );
    }
  }

  ArcScriptSelectorTree.SelectionChangeListener treeListener() {
    return it -> {
      if (loadBtn == null || createBtn == null) {
        return;
      }

      switch (it) {
        case ArcScriptSelectorTree.Selection.None none -> {
          loadBtn.setEnabled(false);
          createBtn.setEnabled(false);
        }
        case ArcScriptSelectorTree.Selection.HasStorage hasStorage -> {
          createBtn.setEnabled(true);
          if (createActionListener != null) {
            createBtn.removeActionListener(createActionListener);
          }

          createActionListener =
              e -> newScript(storageInstanceProvider.get(hasStorage.storageId()));
          createBtn.addActionListener(createActionListener);
          if (hasStorage instanceof ArcScriptSelectorTree.Selection.ScriptFile(
              StorageId id, String title
          )) {
            loadBtn.setEnabled(true);
            if (loadActionListener != null) {
              loadBtn.removeActionListener(loadActionListener);
            }

            loadActionListener = e -> {
              final boolean success = loadScript(
                  storageInstanceProvider.get(hasStorage.storageId()),
                  title);
              if (success) {
                containerView.newScriptView.content.tree.removeScript(id, title);
              }
            };
            loadBtn.addActionListener(loadActionListener);
          }
        }
      }
    };
  }

  private JButton loadBtn;
  private ActionListener loadActionListener;
  private JButton createBtn;
  private ActionListener createActionListener;

  void setControlButtons(final JButton loadBtn, final JButton createBtn) {
    this.loadBtn = loadBtn;
    this.createBtn = createBtn;
  }

  private void newScript(final StorageInstance storageInstance) {
    final String titleSuggestion = "(%s) New Script-%02d".formatted(
        storageInstance.name(),
        containerView.getTabCount());
    final var result = userConfigService
        .arcScriptFileService()
        .saveAsNew(storageInstance.id(), titleSuggestion, "");
    showScript(storageInstance, result);
  }

  private boolean loadScript(final StorageInstance storageInstance, final String title) {
    final var result = userConfigService
        .arcScriptFileService()
        .load(storageInstance.id(), title);
    return showScript(storageInstance, result);
  }

  private boolean showScript(final StorageInstance storageInstance,
                             final ArcScriptFileService.ArcScriptIoResult result) {
    return switch (result) {
      case ArcScriptFileService.ArcScriptIoResult.Ok ok -> {
        final var view = new ArcScriptView(
            this,
            storageInstance,
            ok.storedArcScript());
        add(view);
        containerView.insertTab(
            ok.storedArcScript().title(),
            null,
            view,
            "Hello World!",
            containerView.getTabCount() - 1);
        containerView.setSelectedIndex(containerView.getTabCount() - 2);
        yield true;
      }
      case ArcScriptFileService.ArcScriptIoResult.Err err -> {
        JOptionPane.showMessageDialog(
            containerView,
            "Could not create new ArcScript file: " + err.msg(),
            "ArcScript Editor init failure",
            JOptionPane.ERROR_MESSAGE,
            IconProvider.ERROR);
        yield false;
      }
    };
  }

}

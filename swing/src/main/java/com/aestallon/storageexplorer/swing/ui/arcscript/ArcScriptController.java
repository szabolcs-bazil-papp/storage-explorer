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

package com.aestallon.storageexplorer.swing.ui.arcscript;

import java.awt.*;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.swing.*;
import javax.swing.filechooser.FileSystemView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import com.aestallon.storageexplorer.arcscript.engine.ArcScriptResult;
import com.aestallon.storageexplorer.client.asexport.ResultSetExporter;
import com.aestallon.storageexplorer.client.asexport.ResultSetExporterFactory;
import com.aestallon.storageexplorer.client.storage.StorageInstanceProvider;
import com.aestallon.storageexplorer.client.userconfig.service.ArcScriptFileService;
import com.aestallon.storageexplorer.client.userconfig.service.StoredArcScript;
import com.aestallon.storageexplorer.client.userconfig.service.UserConfigService;
import com.aestallon.storageexplorer.common.event.msg.Msg;
import com.aestallon.storageexplorer.core.event.StorageImportEvent;
import com.aestallon.storageexplorer.core.event.StorageIndexDiscardedEvent;
import com.aestallon.storageexplorer.core.model.instance.StorageInstance;
import com.aestallon.storageexplorer.core.model.instance.dto.StorageId;
import com.aestallon.storageexplorer.swing.ui.arcscript.editor.ArcScriptTextareaFactory;
import com.aestallon.storageexplorer.swing.ui.arcscript.editor.ArcScriptView;
import com.aestallon.storageexplorer.swing.ui.arcscript.result.ArcScriptResultContainerView;
import com.aestallon.storageexplorer.swing.ui.arcscript.result.ArcScriptResultView;
import com.aestallon.storageexplorer.swing.ui.arcscript.tree.ArcScriptSelectorTree;
import com.aestallon.storageexplorer.swing.ui.arcscript.tree.ArcScriptTreeView;
import com.aestallon.storageexplorer.swing.ui.event.ArcScriptViewRenamed;
import com.aestallon.storageexplorer.swing.ui.event.LafChanged;
import com.aestallon.storageexplorer.swing.ui.event.StorageInstanceRenamed;
import com.aestallon.storageexplorer.swing.ui.misc.IconProvider;
import com.aestallon.storageexplorer.swing.ui.misc.MonospaceFontProvider;
import com.aestallon.storageexplorer.swing.ui.misc.RSyntaxTextAreaThemeProvider;

@Service
public class ArcScriptController {

  private static final Logger log = LoggerFactory.getLogger(ArcScriptController.class);

  private static int fCounter = 0;

  private final StorageInstanceProvider storageInstanceProvider;
  private final ApplicationEventPublisher applicationEventPublisher;
  private final UserConfigService userConfigService;
  private final RSyntaxTextAreaThemeProvider themeProvider;
  private final MonospaceFontProvider monospaceFontProvider;
  private final ArcScriptTextareaFactory arcScriptTextareaFactory;
  private final ResultSetExporterFactory resultSetExporterFactory;
  private final ArcScriptResultContainerView resultContainerView;
  private final ArcScriptTreeView arcScriptTreeView;
  private final List<ArcScriptView> arcScriptViews = new ArrayList<>();

  public ArcScriptController(final StorageInstanceProvider storageInstanceProvider,
                             final ApplicationEventPublisher applicationEventPublisher,
                             final UserConfigService userConfigService,
                             final RSyntaxTextAreaThemeProvider themeProvider,
                             final MonospaceFontProvider monospaceFontProvider,
                             ArcScriptResultContainerView resultContainerView,
                             ArcScriptTreeView arcScriptTreeView) {
    this.storageInstanceProvider = storageInstanceProvider;
    this.applicationEventPublisher = applicationEventPublisher;
    this.userConfigService = userConfigService;
    this.themeProvider = themeProvider;
    this.monospaceFontProvider = monospaceFontProvider;
    this.resultContainerView = resultContainerView;
    this.arcScriptTreeView = arcScriptTreeView;
    this.resultSetExporterFactory = new ResultSetExporterFactory();

    arcScriptTextareaFactory = new ArcScriptTextareaFactory(themeProvider, monospaceFontProvider);
  }

  public ArcScriptTextareaFactory arcScriptTextareaFactory() {
    return arcScriptTextareaFactory;
  }

  public ApplicationEventPublisher eventPublisher() {
    return applicationEventPublisher;
  }

  ArcScriptView add(ArcScriptView arcScriptView) {
    if (arcScriptView == null) {
      return null;
    }

    arcScriptViews.add(arcScriptView);
    return arcScriptView;
  }

  public Optional<ArcScriptView> find(ArcScriptSelectorTree.ArcScriptNodeLocator locator) {
    return arcScriptViews.stream()
        .filter(it -> it.storageId().equals(locator.storageId())
                      && it.storedArcScript().title().equals(locator.path()))
        .findFirst();
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
    SwingUtilities.invokeLater(() -> arcScriptTreeView.importStorage(event.storageInstance()));
  }

  @EventListener
  public void onStorageDeleted(StorageIndexDiscardedEvent event) {
    SwingUtilities.invokeLater(() -> arcScriptTreeView.removeStorage(event.storageInstance()));
  }

  @EventListener
  public void onStorageInstanceRenamed(final StorageInstanceRenamed e) {
    SwingUtilities.invokeLater(() -> arcScriptTreeView.storageRenamed(e.storageInstance().id()));
  }

  public record ArcScriptTreeTouchRequest(StoredArcScript sas) {}

  @EventListener
  public void onArcScriptTreeTouchRequested(ArcScriptTreeTouchRequest e) {
    arcScriptTreeView.selectNodeSoft(new ArcScriptSelectorTree.ArcScriptNodeLocator(
        e.sas().storageId(),
        e.sas().title()));
    arcScriptTreeView.requestVisibility();
  }

  @EventListener
  public void onNewScriptRequest(final ArcScriptTreeView.NewScriptRequest event) {
    newScript(storageInstanceProvider.get(event.storageId()));
  }

  public void rename(ArcScriptView arcScriptView, String title) {
    final StoredArcScript storedArcScript = arcScriptView.storedArcScript();
    final var result = userConfigService.arcScriptFileService().rename(storedArcScript, title);
    switch (result) {
      case ArcScriptFileService.ArcScriptIoResult.Ok(StoredArcScript arcScript) -> {
        arcScriptView.storedArcScript(arcScript);
        final var event = new ArcScriptViewRenamed(
            arcScriptView,
            storedArcScript.title(),
            arcScript.title());
        applicationEventPublisher.publishEvent(event);
        arcScriptTreeView.onUserDataChanged(event);
      }
      case ArcScriptFileService.ArcScriptIoResult.Err(String msg) -> JOptionPane.showMessageDialog(
          arcScriptView,
          msg,
          "Renaming failed",
          JOptionPane.ERROR_MESSAGE,
          IconProvider.ERROR);
    }
  }

  public void save(ArcScriptView arcScriptView, String text) {
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

  public record ArcScriptViewDropped(ArcScriptView view) {}

  public void delete(ArcScriptView view) {
    userConfigService.arcScriptFileService().delete(
        view.storedArcScript().storageId(),
        view.storedArcScript().title());
    SwingUtilities.invokeLater(() -> {
      final var storageId = view.storageId();
      final var title = view.storedArcScript().title();
      arcScriptTreeView.removeScript(storageId, title);
      resultContainerView.discardResultOf(storageId, title);
    });
    applicationEventPublisher.publishEvent(new ArcScriptViewDropped(view));
  }

  public void renderResult(final String title,
                           final StorageInstance storageInstance,
                           final ArcScriptResult.Ok result) {
    SwingUtilities.invokeLater(() -> {
      resultContainerView.showResult(
          title,
          new ArcScriptResultView.ResultDisplay(result, storageInstance, this));
      resultContainerView.requestVisibility();
    });
  }

  public void newScript(final StorageInstance storageInstance) {
    final String titleSuggestion = "(%s) New Script-%02d".formatted(
        storageInstance.name(),
        ++fCounter);
    final var result = userConfigService
        .arcScriptFileService()
        .saveAsNew(storageInstance.id(), titleSuggestion, "");
    final var view = showScript(storageInstance, result);
    if (view == null) {
      // if an error occurred, we already signalled it...
      return;
    }

    SwingUtilities.invokeLater(() -> {
      final var locator = ArcScriptSelectorTree.ArcScriptNodeLocator.of(view.storedArcScript());
      arcScriptTreeView.incorporateNode(locator);
      arcScriptTreeView.selectNode(locator);
    });

    userConfigService.setMostRecentStorageInstanceLoad(storageInstance.id());
  }

  public ArcScriptView loadScript(final StorageId storageId, final String title) {
    StorageInstance storageInstance = storageInstanceProvider.get(storageId);
    final var result = userConfigService
        .arcScriptFileService()
        .load(storageInstance.id(), title);
    return add(showScript(storageInstance, result));
  }

  private ArcScriptView showScript(final StorageInstance storageInstance,
                                   final ArcScriptFileService.ArcScriptIoResult result) {
    return switch (result) {
      case ArcScriptFileService.ArcScriptIoResult.Ok(var sas) -> new ArcScriptView(
          this,
          storageInstance,
          sas);
      case ArcScriptFileService.ArcScriptIoResult.Err(String msg) -> {
        applicationEventPublisher.publishEvent(Msg.err("Could not create ArcScript file", msg));
        yield null;
      }
    };
  }

  public void drop(ArcScriptView arcScriptView) {
    arcScriptViews.remove(arcScriptView);
    log.info("Dropped ArcScriptView {}, size remaining: {}",
        arcScriptView.storedArcScript().title(),
        arcScriptViews.size());
  }


  public void export(ArcScriptResult.ResultSet resultSet, ResultSetExporterFactory.Target target) {
    final var fileChooser = new JFileChooser(FileSystemView.getFileSystemView());
    fileChooser.setDialogType(JFileChooser.SAVE_DIALOG);
    fileChooser.setDialogTitle("Export as " + target);

    final int result = fileChooser.showDialog(null, "Export");
    if (JFileChooser.APPROVE_OPTION == result) {
      final File selectedFile = fileChooser.getSelectedFile();
      if (selectedFile.isDirectory()) {
        System.err.println("REEEE");
        return;
      }

      String filePath = selectedFile.getPath();
      final var ext = "." + target.toString().toLowerCase();
      if (!filePath.endsWith(ext)) {
        filePath = filePath + ext;
      }

      final var r = resultSetExporterFactory.get(target).export(resultSet, Path.of(filePath));
      if (r instanceof ResultSetExporter.Result.Error(String msg)) {
        JOptionPane.showMessageDialog(
            null,
            msg,
            "Could not export result set to " + target,
            JOptionPane.ERROR_MESSAGE,
            IconProvider.ERROR);
      }
    }
  }

}

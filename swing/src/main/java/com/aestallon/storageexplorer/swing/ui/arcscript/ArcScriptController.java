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
import com.aestallon.storageexplorer.swing.ui.event.ArcScriptViewRenamed;
import com.aestallon.storageexplorer.swing.ui.event.LafChanged;
import com.aestallon.storageexplorer.swing.ui.event.StorageInstanceRenamed;
import com.aestallon.storageexplorer.swing.ui.misc.IconProvider;
import com.aestallon.storageexplorer.swing.ui.misc.MonospaceFontProvider;
import com.aestallon.storageexplorer.swing.ui.misc.RSyntaxTextAreaThemeProvider;
import com.aestallon.storageexplorer.swing.ui.storagetree.StorageTreeView;

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
  private final List<ArcScriptView> arcScriptViews = new ArrayList<>();
  private final StorageTreeView storageTreeView;

  public ArcScriptController(final StorageInstanceProvider storageInstanceProvider,
                             final ApplicationEventPublisher applicationEventPublisher,
                             final UserConfigService userConfigService,
                             final RSyntaxTextAreaThemeProvider themeProvider,
                             final MonospaceFontProvider monospaceFontProvider,
                             StorageTreeView storageTreeView) {
    this.storageInstanceProvider = storageInstanceProvider;
    this.applicationEventPublisher = applicationEventPublisher;
    this.userConfigService = userConfigService;
    this.themeProvider = themeProvider;
    this.monospaceFontProvider = monospaceFontProvider;
    this.storageTreeView = storageTreeView;
    this.resultSetExporterFactory = new ResultSetExporterFactory();

    arcScriptTextareaFactory = new ArcScriptTextareaFactory(themeProvider, monospaceFontProvider);
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

     
      //mainTreeView.arcScriptSelectorTree().addStorage(storageInstance, loadableScripts);
    });
  }

  @EventListener
  public void onStorageDeleted(StorageIndexDiscardedEvent event) {
    SwingUtilities.invokeLater(() -> {
      final StorageInstance storageInstance = event.storageInstance();
      //mainTreeView.arcScriptSelectorTree().removeStorage(storageInstance.id());
      // cleanup of views are done together elsewhere
    });
  }

  @EventListener
  public void onStorageInstanceRenamed(final StorageInstanceRenamed event) {
//    SwingUtilities.invokeLater(() -> mainTreeView
//        .arcScriptSelectorTree()
//        .storageRenamed(event.storageInstance().id()));
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
        applicationEventPublisher.publishEvent(new ArcScriptViewRenamed(arcScriptView, title));
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

  void delete(ArcScriptView arcScriptView) {
    userConfigService.arcScriptFileService().delete(
        arcScriptView.storedArcScript().storageId(),
        arcScriptView.storedArcScript().title());
  }

  public ArcScriptView newScript(final StorageInstance storageInstance) {
    final String titleSuggestion = "(%s) New Script-%02d".formatted(
        storageInstance.name(),
        ++fCounter);
    final var result = userConfigService
        .arcScriptFileService()
        .saveAsNew(storageInstance.id(), titleSuggestion, "");
    return showScript(storageInstance, result);
  }

  public ArcScriptView loadScript(final StorageId storageId, final String title) {
    StorageInstance storageInstance = storageInstanceProvider.get(storageId);
    final var result = userConfigService
        .arcScriptFileService()
        .load(storageInstance.id(), title);
    return showScript(storageInstance, result);
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
    log.info("Drop requested...");
  }


  void export(ArcScriptResult.ResultSet resultSet, ResultSetExporterFactory.Target target) {
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

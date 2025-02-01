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
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import com.aestallon.storageexplorer.core.model.instance.StorageInstance;
import com.aestallon.storageexplorer.core.service.StorageInstanceProvider;
import com.aestallon.storageexplorer.core.userconfig.service.ArcScriptFileService;
import com.aestallon.storageexplorer.core.userconfig.service.StoredArcScript;
import com.aestallon.storageexplorer.core.userconfig.service.UserConfigService;
import com.aestallon.storageexplorer.swing.ui.event.LafChanged;
import com.aestallon.storageexplorer.swing.ui.misc.IconProvider;
import com.aestallon.storageexplorer.swing.ui.misc.MonospaceFontProvider;
import com.aestallon.storageexplorer.swing.ui.misc.RSyntaxTextAreaThemeProvider;

@Service
public class ArcScriptController {

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

}

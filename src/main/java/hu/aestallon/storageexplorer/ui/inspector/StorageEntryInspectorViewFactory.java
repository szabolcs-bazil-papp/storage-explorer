/*
 * Copyright (C) 2024 it4all Hungary Kft.
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

package hu.aestallon.storageexplorer.ui.inspector;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import javax.swing.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import hu.aestallon.storageexplorer.domain.storage.model.ListEntry;
import hu.aestallon.storageexplorer.domain.storage.model.MapEntry;
import hu.aestallon.storageexplorer.domain.storage.model.ObjectEntry;
import hu.aestallon.storageexplorer.domain.storage.model.StorageEntry;
import hu.aestallon.storageexplorer.domain.storage.service.StorageIndex;
import hu.aestallon.storageexplorer.ui.controller.ViewController;
import hu.aestallon.storageexplorer.ui.misc.MonospaceFontProvider;
import hu.aestallon.storageexplorer.util.Uris;

@Service
public class StorageEntryInspectorViewFactory {

  private final ApplicationEventPublisher eventPublisher;
  private final ObjectMapper objectMapper;
  private final StorageIndex storageIndex;
  private final MonospaceFontProvider monospaceFontProvider;
  private final Map<StorageEntry, InspectorView<? extends StorageEntry>> openedInspectors;
  private final Map<StorageEntry, InspectorDialog> openedDialogs;
  private final Map<StorageEntry, List<JTextArea>> textAreas;

  public StorageEntryInspectorViewFactory(ApplicationEventPublisher eventPublisher,
                                          ObjectMapper objectMapper, StorageIndex storageIndex,
                                          MonospaceFontProvider monospaceFontProvider) {
    this.eventPublisher = eventPublisher;
    this.objectMapper = objectMapper;
    this.storageIndex = storageIndex;
    this.monospaceFontProvider = monospaceFontProvider;

    openedInspectors = new HashMap<>();
    openedDialogs = new HashMap<>();
    textAreas = new ConcurrentHashMap<>();
  }

  MonospaceFontProvider monospaceFontProvider() {
    return monospaceFontProvider;
  }

  void submitTextArea(StorageEntry storageEntry, JTextArea textArea) {
    textAreas.computeIfAbsent(storageEntry, k -> new ArrayList<>()).add(textArea);
  }

  public void dropInspector(final InspectorView<? extends StorageEntry> inspector) {
    final var storageEntry = inspector.storageEntry();
    dropInspector(storageEntry);
  }

  private void dropInspector(final StorageEntry storageEntry) {
    openedDialogs.remove(storageEntry);
    openedInspectors.remove(storageEntry);
    textAreas.remove(storageEntry);
  }

  public enum InspectorRendering { TAB, DIALOG, NONE }

  public InspectorRendering inspectorRendering(StorageEntry storageEntry) {
    if (openedDialogs.containsKey(storageEntry)) {
      return InspectorRendering.DIALOG;
    }

    if (openedInspectors.containsKey(storageEntry)) {
      return InspectorRendering.TAB;
    }

    return InspectorRendering.NONE;
  }

  public InspectorView<? extends StorageEntry> createInspector(StorageEntry storageEntry) {
    if (openedInspectors.containsKey(storageEntry)) {
      return null;
    }

    final InspectorView<? extends StorageEntry> inspector;
    if (storageEntry instanceof ObjectEntry) {
      inspector = new ObjectEntryInspectorView((ObjectEntry) storageEntry, objectMapper, this);
    } else if (storageEntry instanceof ListEntry || storageEntry instanceof MapEntry) {
      inspector = new CollectionEntryInspectorView(storageEntry, this);
    } else {
      throw new AssertionError(storageEntry + " is not interpreted - TYPE ERROR!");
    }

    openedInspectors.put(storageEntry, inspector);
    return inspector;
  }

  public void showDialog(StorageEntry storageEntry, Component parent) {
    InspectorView<? extends StorageEntry> inspector = createInspector(storageEntry);
    if (inspector == null) {
      return;
    }

    final var dialog = new InspectorDialog(inspector);
    openedDialogs.put(storageEntry, dialog);

    dialog.addWindowListener(new WindowAdapter() {

      @Override
      public void windowClosing(WindowEvent e) {
        super.windowClosing(e);
        dropInspector(storageEntry);
      }

    });
    dialog.setLocationRelativeTo(parent);
    dialog.setVisible(true);
  }

  public void focusDialog(final StorageEntry storageEntry) {
    Optional.ofNullable(openedDialogs.get(storageEntry))
        .ifPresent(dialog -> SwingUtilities.invokeLater(() -> {
          if (dialog.getState() != Frame.NORMAL) {
            dialog.setState(Frame.NORMAL);
          }

          dialog.toFront();
          dialog.repaint();
        }));
  }

  public Optional<InspectorView<? extends  StorageEntry>> getTab(final StorageEntry storageEntry) {
    if (openedDialogs.containsKey(storageEntry)) {
      return Optional.empty();
    }

    return Optional.ofNullable(openedInspectors.get(storageEntry));
  }

  @EventListener
  void onFontSizeChanged(
      @SuppressWarnings("unused") MonospaceFontProvider.FontSizeChange fontSizeChange) {
    SwingUtilities.invokeLater(() -> {
      final Font font = monospaceFontProvider.getFont();
      textAreas.values().stream().flatMap(List::stream).forEach(it -> it.setFont(font));
    });
  }

  void addJumpAction(final JTextArea component) {
    final var ctrlShiftI = KeyStroke.getKeyStroke(
        KeyEvent.VK_I,
        KeyEvent.CTRL_DOWN_MASK | KeyEvent.SHIFT_DOWN_MASK);
    component.getInputMap().put(ctrlShiftI, "jumpToRef");
    component.getActionMap().put("jumpToRef", new AbstractAction() {

      @Override
      public void actionPerformed(ActionEvent e) {
        final JTextArea textArea = (JTextArea) e.getSource();
        final String selectedText = textArea.getSelectedText();
        if (Strings.isNullOrEmpty(selectedText)) {
          return;
        }
        jumpToUriBasedOnText(selectedText);
      }

    });
  }

  private void jumpToUriBasedOnText(String selectedText) {
    Uris.parse(selectedText).ifPresent(this::jumpToUri);
  }

  void jumpToUri(final URI uri) {
    storageIndex.get(uri).ifPresentOrElse(
        it -> eventPublisher.publishEvent(new ViewController.EntryInspectionEvent(it)),
        () -> JOptionPane.showMessageDialog(
            null,
            "Cannot show URI: " + uri,
            "Unreachable URI",
            JOptionPane.ERROR_MESSAGE));
  }

  void addRenderAction(final StorageEntry storageEntry, final JToolBar toolbar) {
    toolbar.add(new AbstractAction("Render") {
      @Override
      public void actionPerformed(ActionEvent e) {
        eventPublisher.publishEvent(new ViewController.GraphRenderingRequest(storageEntry));
      }
    });
  }

}

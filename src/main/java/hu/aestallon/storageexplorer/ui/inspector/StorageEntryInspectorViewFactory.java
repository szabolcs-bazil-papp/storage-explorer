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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.swing.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import hu.aestallon.storageexplorer.domain.storage.model.ObjectEntry;
import hu.aestallon.storageexplorer.domain.storage.model.StorageEntry;
import hu.aestallon.storageexplorer.domain.storage.service.StorageIndex;
import hu.aestallon.storageexplorer.ui.controller.ViewController;
import hu.aestallon.storageexplorer.ui.dialog.entryinspector.ObjectEntryInspectorDialog;
import hu.aestallon.storageexplorer.ui.misc.MonospaceFontProvider;
import hu.aestallon.storageexplorer.util.Uris;

@Service
public class StorageEntryInspectorViewFactory {

  private final ApplicationEventPublisher eventPublisher;
  private final ObjectMapper objectMapper;
  private final StorageIndex storageIndex;
  private final MonospaceFontProvider monospaceFontProvider;
  private final Set<StorageEntry> openedDialogs;
  private final Map<StorageEntry, List<JTextArea>> textAreas;

  public StorageEntryInspectorViewFactory(ApplicationEventPublisher eventPublisher,
                                          ObjectMapper objectMapper, StorageIndex storageIndex,
                                          MonospaceFontProvider monospaceFontProvider) {
    this.eventPublisher = eventPublisher;
    this.objectMapper = objectMapper;
    this.storageIndex = storageIndex;
    this.monospaceFontProvider = monospaceFontProvider;
    this.openedDialogs = new HashSet<>();
    textAreas = new ConcurrentHashMap<>();
  }

  MonospaceFontProvider monospaceFontProvider() {
    return monospaceFontProvider;
  }

  void submitTextArea(StorageEntry storageEntry, JTextArea textArea) {
    textAreas.computeIfAbsent(storageEntry, k -> new ArrayList<>()).add(textArea);
  }

  public ObjectEntryInspectorView createInspector(StorageEntry storageEntry) {
    if (openedDialogs.contains(storageEntry)) {
      return null;
    }
    if (storageEntry instanceof ObjectEntry) {
      openedDialogs.add(storageEntry);
      return new ObjectEntryInspectorView((ObjectEntry) storageEntry, objectMapper, this);
    } else {
      return null;
    }
  }

  public void showDialog(StorageEntry storageEntry, Component parent) {
    ObjectEntryInspectorView inspector = createInspector(storageEntry);
    if (inspector == null) {
      return;
    }

    final var dialog = new ObjectEntryInspectorDialog(inspector);
    dialog.setLocationRelativeTo(parent);
    dialog.setVisible(true);
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
        Uris
            .parse(selectedText)
            .map(storageIndex::get) // we are not flatMapping, that's different behaviour.
            .ifPresent(
                u -> u.ifPresentOrElse(
                    it -> eventPublisher.publishEvent(new ViewController.EntryInspectionEvent(it)),
                    () -> JOptionPane.showMessageDialog(
                        null,
                        "Cannot show URI: " + selectedText,
                        "Unreachable URI",
                        JOptionPane.ERROR_MESSAGE)));
      }

    });
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

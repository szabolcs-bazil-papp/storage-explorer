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

package com.aestallon.storageexplorer.swing.ui.inspector;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import javax.swing.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import com.aestallon.storageexplorer.core.model.entry.ListEntry;
import com.aestallon.storageexplorer.core.model.entry.MapEntry;
import com.aestallon.storageexplorer.core.model.entry.ObjectEntry;
import com.aestallon.storageexplorer.core.model.entry.SequenceEntry;
import com.aestallon.storageexplorer.core.model.entry.StorageEntry;
import com.aestallon.storageexplorer.core.service.StorageInstanceProvider;
import com.aestallon.storageexplorer.core.event.EntryInspectionEvent;
import com.aestallon.storageexplorer.graph.event.GraphRenderingRequest;
import com.aestallon.storageexplorer.core.event.StorageIndexDiscardedEvent;
import com.aestallon.storageexplorer.swing.ui.misc.IconProvider;
import com.aestallon.storageexplorer.swing.ui.misc.MonospaceFontProvider;
import com.aestallon.storageexplorer.common.util.Uris;

@Service
public class StorageEntryInspectorViewFactory {

  private final ApplicationEventPublisher eventPublisher;
  private final StorageInstanceProvider storageInstanceProvider;
  private final MonospaceFontProvider monospaceFontProvider;
  private final InspectorTextareaFactory textareaFactory;
  private final Map<StorageEntry, InspectorView<? extends StorageEntry>> openedInspectors;
  private final Map<StorageEntry, InspectorDialog> openedDialogs;
  private final Map<StorageEntry, List<JTextArea>> textAreas;

  public StorageEntryInspectorViewFactory(ApplicationEventPublisher eventPublisher,
                                          StorageInstanceProvider storageInstanceProvider,
                                          MonospaceFontProvider monospaceFontProvider) {
    this.eventPublisher = eventPublisher;
    this.storageInstanceProvider = storageInstanceProvider;
    this.monospaceFontProvider = monospaceFontProvider;
    this.textareaFactory = new InspectorTextareaFactory(this);

    openedInspectors = new ConcurrentHashMap<>();
    openedDialogs = new ConcurrentHashMap<>();
    textAreas = new ConcurrentHashMap<>();
  }

  MonospaceFontProvider monospaceFontProvider() {
    return monospaceFontProvider;
  }

  void submitTextArea(StorageEntry storageEntry, JTextArea textArea) {
    textAreas.computeIfAbsent(storageEntry, k -> new ArrayList<>()).add(textArea);
  }

  InspectorTextareaFactory textareaFactory() {
    return textareaFactory;
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
      inspector = new ObjectEntryInspectorView((ObjectEntry) storageEntry, this);
    } else if (storageEntry instanceof ListEntry || storageEntry instanceof MapEntry) {
      inspector = new CollectionEntryInspectorView(storageEntry, this);
    } else if (storageEntry instanceof SequenceEntry) {
      inspector = new SequenceEntryInspectorView((SequenceEntry) storageEntry);
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

  public Optional<InspectorView<? extends StorageEntry>> getTab(final StorageEntry storageEntry) {
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
        if (selectedText == null || selectedText.isEmpty()) {
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
    // TODO: Absolutely DO NOT DO THIS!
    storageInstanceProvider.indexOf(uri).get(uri).ifPresentOrElse(
        it -> eventPublisher.publishEvent(new EntryInspectionEvent(it)),
        () -> JOptionPane.showMessageDialog(
            null,
            "Cannot show URI: " + uri,
            "Unreachable URI",
            JOptionPane.ERROR_MESSAGE));
  }

  void addRenderAction(final StorageEntry storageEntry, final JToolBar toolbar) {
    toolbar.add(new AbstractAction(null, IconProvider.GRAPH) {
      @Override
      public void actionPerformed(ActionEvent e) {
        eventPublisher.publishEvent(new GraphRenderingRequest(storageEntry));
      }
    });
  }

  @EventListener
  @Order(0)
  public void discardInspectorDialogsOfStorageAt(StorageIndexDiscardedEvent e) {
    openedDialogs.entrySet().stream()
        .filter(it -> it.getKey().storageId().equals(e.storageInstance().id()))
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))
        .forEach((entry, dialog) -> {
          dialog.dispose();
          dropInspector(entry); // just to make sure if listener is not called.
        });
  }

}

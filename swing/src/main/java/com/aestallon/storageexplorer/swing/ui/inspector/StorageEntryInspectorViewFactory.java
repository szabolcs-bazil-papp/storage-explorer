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
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.swing.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import com.aestallon.storageexplorer.client.ff.FeatureFlag;
import com.aestallon.storageexplorer.client.graph.event.GraphRenderingRequest;
import com.aestallon.storageexplorer.client.storage.StorageInstanceProvider;
import com.aestallon.storageexplorer.client.userconfig.service.StorageEntryTrackingService;
import com.aestallon.storageexplorer.core.event.StorageIndexDiscardedEvent;
import com.aestallon.storageexplorer.core.model.entry.ListEntry;
import com.aestallon.storageexplorer.core.model.entry.MapEntry;
import com.aestallon.storageexplorer.core.model.entry.ObjectEntry;
import com.aestallon.storageexplorer.core.model.entry.SequenceEntry;
import com.aestallon.storageexplorer.core.model.entry.StorageEntry;
import com.aestallon.storageexplorer.core.model.instance.dto.StorageId;
import com.aestallon.storageexplorer.core.model.loading.ObjectEntryLoadResult;
import com.aestallon.storageexplorer.core.util.Uris;
import com.aestallon.storageexplorer.swing.ui.dialog.entrymeta.EntryMetaEditorController;
import com.aestallon.storageexplorer.swing.ui.dialog.entrymeta.EntryMetaEditorDialog;
import com.aestallon.storageexplorer.swing.ui.editor.StorageEntryEditorController;
import com.aestallon.storageexplorer.swing.ui.event.LafChanged;
import com.aestallon.storageexplorer.swing.ui.misc.IconProvider;
import com.aestallon.storageexplorer.swing.ui.misc.JumpToUri;
import com.aestallon.storageexplorer.swing.ui.misc.LafService;
import com.aestallon.storageexplorer.swing.ui.misc.MonospaceFontProvider;
import com.aestallon.storageexplorer.swing.ui.misc.RSyntaxTextAreaThemeProvider;

@Service
public class StorageEntryInspectorViewFactory {

  private record TextAreasByDiffView(ObjectEntryDiffView view, List<JTextArea> textAreas) {}


  private final ApplicationEventPublisher eventPublisher;
  private final StorageInstanceProvider storageInstanceProvider;
  private final MonospaceFontProvider monospaceFontProvider;
  private final InspectorTextareaFactory textareaFactory;
  private final RSyntaxTextAreaThemeProvider themeProvider;
  private final StorageEntryTrackingService trackingService;
  private final Map<StorageEntry, InspectorView<? extends StorageEntry>> openedInspectors;
  private final Map<StorageEntry, InspectorDialog> openedDialogs;
  private final Map<StorageEntry, List<JTextArea>> textAreas;

  private final List<TextAreasByDiffView> diffTextAreas;

  public StorageEntryInspectorViewFactory(ApplicationEventPublisher eventPublisher,
                                          StorageInstanceProvider storageInstanceProvider,
                                          MonospaceFontProvider monospaceFontProvider,
                                          RSyntaxTextAreaThemeProvider themeProvider,
                                          StorageEntryTrackingService trackingService) {
    this.eventPublisher = eventPublisher;
    this.storageInstanceProvider = storageInstanceProvider;
    this.monospaceFontProvider = monospaceFontProvider;
    this.themeProvider = themeProvider;
    this.textareaFactory = new InspectorTextareaFactory(this, themeProvider);
    this.trackingService = trackingService;

    openedInspectors = new ConcurrentHashMap<>();
    openedDialogs = new ConcurrentHashMap<>();
    textAreas = new ConcurrentHashMap<>();
    diffTextAreas = new ArrayList<>();
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

  ApplicationEventPublisher eventPublisher() {
    return eventPublisher;
  }

  StorageInstanceProvider storageInstanceProvider() {
    return storageInstanceProvider;
  }

  private void dropDiffInspector(final ObjectEntryDiffView diffInspector) {
    diffTextAreas.stream()
        .filter(it -> it.view() == diffInspector)
        .findFirst()
        .ifPresent(it -> {
          diffTextAreas.remove(it);
          textAreas.computeIfAbsent(diffInspector.storageEntry(), k -> new ArrayList<>())
              .removeAll(it.textAreas());
        });
  }

  private void dropInspector(final StorageEntry storageEntry) {
    openedDialogs.remove(storageEntry);
    openedInspectors.remove(storageEntry);
    textAreas.remove(storageEntry);
    trackingService.removeTrackedInspector(storageEntry);
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
    if (storageEntry instanceof ObjectEntry o) {
      inspector = new ObjectEntryInspectorView(o, this);
    } else if (storageEntry instanceof ListEntry || storageEntry instanceof MapEntry) {
      inspector = new CollectionEntryInspectorView(storageEntry, this);
    } else if (storageEntry instanceof SequenceEntry s) {
      inspector = new SequenceEntryInspectorView(s, this);
    } else {
      throw new AssertionError(storageEntry + " is not interpreted - TYPE ERROR!");
    }

    openedInspectors.put(storageEntry, inspector);
    trackingService.addTrackedInspector(storageEntry);
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

  public void showDiffDialog(ObjectEntry objectEntry,
                             ObjectEntryLoadResult.SingleVersion base,
                             long version,
                             Component parent) {
    final var diffView = new ObjectEntryDiffView(objectEntry, this, base, version);
    final var dialog = new InspectorDialog(diffView);
    dialog.setSize(1_000, 800);
    dialog.addWindowListener(new WindowAdapter() {

      @Override
      public void windowClosing(WindowEvent e) {
        super.windowClosing(e);
        dropDiffInspector(diffView);
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

  public StorageEntryTrackingService trackingService() {
    return trackingService;
  }

  public Optional<InspectorView<? extends StorageEntry>> getTab(final StorageEntry storageEntry) {
    if (openedDialogs.containsKey(storageEntry)) {
      return Optional.empty();
    }

    return Optional.ofNullable(openedInspectors.get(storageEntry));
  }

  void setDescriptionTextAreaProps(final JTextArea textareaDescription) {
    textareaDescription.setWrapStyleWord(true);
    textareaDescription.setLineWrap(true);
    textareaDescription.setEditable(false);
    textareaDescription.setOpaque(false);
    textareaDescription.setFont(LafService.font(LafService.FontToken.MEDIUM));
    textareaDescription.setBorder(
        BorderFactory.createEmptyBorder() /* BorderFactory.createLineBorder(Color.RED, 1) */);
    textareaDescription.setColumns(0);
  }



  @EventListener
  void onFontSizeChanged(
      @SuppressWarnings("unused") MonospaceFontProvider.FontSizeChange fontSizeChange) {
    SwingUtilities.invokeLater(() -> {
      final Font font = monospaceFontProvider.getFont();
      textAreas.values().stream().flatMap(List::stream).forEach(it -> it.setFont(font));
      diffTextAreas.stream()
          .flatMap(it -> it.textAreas().stream())
          .forEach(it -> it.setFont(font));
    });
  }

  @EventListener
  void onLafChanged(final LafChanged lafChanged) {
    SwingUtilities.invokeLater(() -> {
      themeProvider.setCurrentTheme(lafChanged.laf());
      final var font = monospaceFontProvider.getFont();
      textAreas.values().stream().flatMap(List::stream).forEach(it -> {
        themeProvider.applyCurrentTheme(it);
        it.setFont(font);
      });
      diffTextAreas.stream()
          .flatMap(it -> it.textAreas().stream())
          .forEach(it -> {
            themeProvider.applyCurrentTheme(it);
            it.setFont(font);
          });
    });
  }

  void addJumpAction(final StorageId storageId, final JTextArea component) {
    final var ctrlShiftI = KeyStroke.getKeyStroke(
        KeyEvent.VK_I,
        InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK);
    component.getInputMap().put(ctrlShiftI, "jumpToRef");
    component.getActionMap().put("jumpToRef", new AbstractAction() {

      @Override
      public void actionPerformed(ActionEvent e) {
        final JTextArea textArea = (JTextArea) e.getSource();
        final String selectedText = textArea.getSelectedText();
        if (selectedText == null || selectedText.isEmpty()) {
          return;
        }
        jumpToUriBasedOnText(storageId, selectedText);
      }

    });
  }

  private void jumpToUriBasedOnText(StorageId storageId, String selectedText) {
    Uris.parse(selectedText).ifPresent(it -> jumpToUri(storageId, it));
  }

  void jumpToUri(final StorageId storageId, final URI uri) {
    JumpToUri.jump(eventPublisher, uri, storageInstanceProvider, storageId);
  }

  void addRenderAction(final StorageEntry storageEntry, final JToolBar toolbar) {
    toolbar.add(new AbstractAction(null, IconProvider.GRAPH) {
      @Override
      public void actionPerformed(ActionEvent e) {
        eventPublisher.publishEvent(new GraphRenderingRequest(storageEntry));
      }
    });
  }

  void addEditMetaAction(final StorageEntry storageEntry, final JToolBar toolbar) {
    toolbar.add(new AbstractAction(null, IconProvider.EDIT) {
      @Override
      public void actionPerformed(ActionEvent e) {
        final var controller = EntryMetaEditorController.newInstance(storageEntry, trackingService);
        final var dialog = new EntryMetaEditorDialog(controller);
        dialog.pack();
        dialog.setLocationRelativeTo(toolbar);
        dialog.setVisible(true);
      }
    });
  }

  void addDiffAction(final ObjectEntry objectEntry,
                     final ObjectEntryLoadResult.SingleVersion singleVersion,
                     final long versionNr,
                     final JToolBar toolbar,
                     final Component parent) {
    toolbar.add(new AbstractAction(null, IconProvider.DIFF) {
      @Override
      public void actionPerformed(ActionEvent e) {
        showDiffDialog(objectEntry, singleVersion, versionNr, parent);
      }
    });
  }

  void addModifyAction(final StorageEntry storageEntry,
                       final Supplier<ObjectEntryLoadResult.SingleVersion> versionSupplier,
                       final long versionNr,
                       final ObjectEntryLoadResult.MultiVersion multiVersion,
                       final JToolBar toolbar) {
    if (!FeatureFlag.ENTRY_EDITOR.isEnabled()) {
      return;
    }

    if (versionSupplier == null) {
      return;
    }

    toolbar.add(new AbstractAction("MODIFY ENTRY") {
      @Override
      public void actionPerformed(ActionEvent e) {
        final var controller = new StorageEntryEditorController(
            eventPublisher,
            textareaFactory,
            storageInstanceProvider,
            StorageEntryInspectorViewFactory.this);
        final ObjectEntryLoadResult.SingleVersion headVersion;
        final long headVersionNr;
        if (multiVersion != null) {
          headVersion = multiVersion.head();
          headVersionNr = multiVersion.versions().size() - 1L;
        } else {
          headVersion = null;
          headVersionNr = -1L;
        }

        final var version = versionSupplier.get();
        if (version == null) {
          JOptionPane.showMessageDialog(
              toolbar,
              "Entry not available!",
              "Check whether the entry is still present in storage!",
              JOptionPane.ERROR_MESSAGE);
          return;
        }
        controller.launch(storageEntry, version, versionNr, headVersion, headVersionNr);
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
          dropInspector(entry); // just to make sure if the listener is not called.
        });
  }

}

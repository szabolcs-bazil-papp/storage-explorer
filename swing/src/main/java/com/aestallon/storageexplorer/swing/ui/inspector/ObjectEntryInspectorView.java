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

package com.aestallon.storageexplorer.swing.ui.inspector;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import org.fife.rsta.ui.search.FindDialog;
import org.fife.rsta.ui.search.SearchEvent;
import org.fife.rsta.ui.search.SearchListener;
import org.fife.ui.rtextarea.RTextArea;
import org.fife.ui.rtextarea.SearchContext;
import org.fife.ui.rtextarea.SearchEngine;
import org.fife.ui.rtextarea.SearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.aestallon.storageexplorer.client.userconfig.service.StorageEntryTrackingService;
import com.aestallon.storageexplorer.core.model.entry.ObjectEntry;
import com.aestallon.storageexplorer.core.model.loading.ObjectEntryLoadResult;
import com.aestallon.storageexplorer.swing.ui.misc.AutoSizingTextArea;
import com.aestallon.storageexplorer.swing.ui.misc.IconProvider;
import com.aestallon.storageexplorer.swing.ui.misc.LafService;
import com.aestallon.storageexplorer.swing.ui.misc.OpenInSystemExplorerAction;

public class ObjectEntryInspectorView extends JTabbedPane implements InspectorView<ObjectEntry> {

  private static final Logger log = LoggerFactory.getLogger(ObjectEntryInspectorView.class);

  private static final DateTimeFormatter FORMATTER_CREATION_DATE =
      DateTimeFormatter.ISO_ZONED_DATE_TIME;

  protected final transient ObjectEntry objectEntry;
  protected final transient StorageEntryInspectorViewFactory factory;
  private final transient Action openInSystemExplorerAction;
  
  private final transient List<JTextArea> textAreas = new ArrayList<>();

  public ObjectEntryInspectorView(ObjectEntry objectEntry,
                                  StorageEntryInspectorViewFactory factory) {
    super(SwingConstants.LEFT, JTabbedPane.SCROLL_TAB_LAYOUT);

    this.objectEntry = objectEntry;
    this.factory = factory;
    this.openInSystemExplorerAction = new OpenInSystemExplorerAction(objectEntry, this);

    final var result = objectEntry.tryLoad().get();
    switch (result) {
      case ObjectEntryLoadResult.Err err -> setUpLoadingErrorDisplay(err);
      case ObjectEntryLoadResult.SingleVersion sv -> setUpObjectNodeDisplay(sv);
      case ObjectEntryLoadResult.MultiVersion mv -> setUpObjectNodeDisplay(mv);
    }
    addChangeListener(change -> {
      final int selectedIndex = getSelectedIndex();
      if (selectedIndex >= 0) {
        VersionPane versionPane = (VersionPane) getComponentAt(selectedIndex);
        versionPane.initialise();
      }
    });
  }

  @Override
  public List<JTextArea> textAreas() {
    return textAreas;
  }

  private void setUpObjectNodeDisplay(ObjectEntryLoadResult.MultiVersion multiVersion) {
    final var versions = multiVersion.versions();
    for (int i = 0; i < versions.size(); i++) {
      addTab(String.format("%02d", i), versionPane(versions.get(i), i, multiVersion));
    }

    setSelectedIndex(versions.size() - 1);
  }

  private void setUpObjectNodeDisplay(
      ObjectEntryLoadResult.SingleVersion singleVersion) {
    addTab("SINGLE", versionPane(singleVersion, -1L));
    setSelectedIndex(0);
  }

  protected VersionPane versionPane(ObjectEntryLoadResult.SingleVersion version,
                                    long versionNr) {
    return new VersionPane(version, versionNr, null);
  }

  protected VersionPane versionPane(ObjectEntryLoadResult.SingleVersion version,
                                    long versionNr,
                                    final ObjectEntryLoadResult.MultiVersion multiVersion) {
    return new VersionPane(version, versionNr, multiVersion);
  }

  protected class VersionPane extends JPanel {

    protected final ObjectEntryLoadResult.SingleVersion version;
    protected final long versionNr;
    private final ObjectEntryLoadResult.MultiVersion multiVersion;
    private boolean initialised = false;

    private JLabel labelName;
    private JTextArea textareaDescription;

    protected VersionPane(final ObjectEntryLoadResult.SingleVersion version,
                          final long versionNr,
                          final ObjectEntryLoadResult.MultiVersion multiVersion) {
      this.version = version;
      this.versionNr = versionNr;
      this.multiVersion = multiVersion;

      setLayout(new BoxLayout(VersionPane.this, BoxLayout.Y_AXIS));
      setBorder(new EmptyBorder(5, 5, 5, 5));

      if (version instanceof ObjectEntryLoadResult.SingleVersion.Eager) {
        initialise();
      }
    }

    protected void initialise() {
      if (initialised) {
        return;
      }

      final var toolbar = new JToolBar(SwingConstants.TOP);
      toolbar.setOrientation(SwingConstants.HORIZONTAL);
      toolbar.setBorder(new EmptyBorder(5, 0, 5, 0));
      factory.addRenderAction(objectEntry, toolbar);
      toolbar.add(openInSystemExplorerAction);
      factory.addEditMetaAction(objectEntry, toolbar);
      if (multiVersion != null) {
        factory.addDiffAction(
            objectEntry, version, versionNr, toolbar,
            ObjectEntryInspectorView.this);
      }
      factory.addModifyAction(objectEntry, () -> version, versionNr, multiVersion, toolbar);
      toolbar.add(Box.createHorizontalGlue());

      Box box = new Box(BoxLayout.X_AXIS);
      labelName = new JLabel(factory.trackingService().getUserData(objectEntry)
          .map(StorageEntryTrackingService.StorageEntryUserData::name)
          .filter(it -> !it.isBlank())
          .map(it -> it + " - " + objectEntry.getDisplayName(version))
          .orElseGet(() -> objectEntry.getDisplayName(version)));
      labelName.setFont(LafService.wrap(UIManager.getFont("h3.font")));
      labelName.setAlignmentX(Component.LEFT_ALIGNMENT);
      box.add(labelName);

      Box box1 = new Box(BoxLayout.X_AXIS);
      final var creationLabel = new JLabel("Created at:");
      creationLabel.setFont(LafService.wrap(UIManager.getFont("h4.font")));
      creationLabel.setBorder(new EmptyBorder(0, 0, 0, 5));

      final var creationValue = new JLabel(getNodeCreationValue(version));
      creationValue.setFont(LafService.wrap(UIManager.getFont("h4.font")));
      box1.add(creationLabel);
      box1.add(creationValue);

      final var description = factory.trackingService().getUserData(objectEntry)
          .map(StorageEntryTrackingService.StorageEntryUserData::description)
          .filter(it -> !it.isBlank())
          .orElse("");
      final Box box1b = new Box(BoxLayout.X_AXIS);
      textareaDescription = new AutoSizingTextArea(description);
      factory.setDescriptionTextAreaProps(textareaDescription);
      box1b.add(textareaDescription);

      final Box box2 = createTextareaContainer(toolbar);

      add(toolbar);
      toolbar.setAlignmentX(Component.LEFT_ALIGNMENT);
      add(box);
      box.setAlignmentX(Component.LEFT_ALIGNMENT);
      add(box1);
      box1.setAlignmentX(Component.LEFT_ALIGNMENT);
      add(box1b);
      box1b.setAlignmentX(Component.LEFT_ALIGNMENT);
      add(box2);
      box2.setAlignmentX(Component.LEFT_ALIGNMENT);

      initialised = true;
    }

    protected Box createTextareaContainer(JToolBar toolbar) {
      Box box2 = new Box(BoxLayout.X_AXIS);
      final var pane = factory.textareaFactory().create(
          objectEntry, version,
          new InspectorTextareaFactory.Config(null, true, true));
      if (pane.textArea() instanceof RTextArea rTextArea) {
        final var findAction = new AbstractAction(null, IconProvider.MAGNIFY) {
          @Override
          public void actionPerformed(ActionEvent e) {
            final var sd = new FindDialog((Frame) null, searchListener(rTextArea));
            sd.setVisible(true);
          }
        };
        toolbar.add(findAction);
        rTextArea.registerKeyboardAction(
            findAction, 
            KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK),
            JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
      }
      ObjectEntryInspectorView.this.textAreas.add(pane.textArea());
      box2.add(pane.scrollPane());
      SwingUtilities.invokeLater(() -> pane.scrollPane().getVerticalScrollBar().setValue(0));
      return box2;
    }

  }

  private String getNodeCreationValue(
      final ObjectEntryLoadResult.SingleVersion objectNode) {
    final OffsetDateTime createdAt = objectNode.meta().createdAt();
    if (createdAt == null) {
      return "UNKNOWN";
    }

    return FORMATTER_CREATION_DATE.format(createdAt);
  }

  private void setUpLoadingErrorDisplay(final ObjectEntryLoadResult.Err err) {
    addTab("ERROR", errorPane(err));
    setSelectedIndex(0);
  }

  private JComponent errorPane(final ObjectEntryLoadResult.Err err) {
    final var container = new JPanel();
    container.setLayout(new BoxLayout(container, BoxLayout.Y_AXIS));
    container.setBorder(new EmptyBorder(5, 5, 5, 5));

    final var label = new JLabel(
        (objectEntry == null ? "" : objectEntry + " ") + "LOADING ERROR");
    label.setFont(LafService.wrap(UIManager.getFont("h3.font")));
    label.setAlignmentX(Component.LEFT_ALIGNMENT);

    container.add(label);
    container.add(errorMessageDisplay(err.msg()));
    return container;
  }

  private JScrollPane errorMessageDisplay(final String errorMessage) {
    final var textarea = new JTextArea(errorMessage, 0, 80);
    textarea.setWrapStyleWord(true);
    textarea.setLineWrap(true);
    textarea.setEditable(false);
    textarea.setOpaque(false);
    textarea.setFont(factory.monospaceFontProvider().getFont());
    return InspectorTextareaFactory.textAreaContainerPane(textarea);
  }

  @Override
  public ObjectEntry storageEntry() {
    return objectEntry;
  }

  @Override
  public void onUserDataChanged(StorageEntryTrackingService.StorageEntryUserData userData) {
    for (int i = 0; i < getTabCount(); i++) {
      final var component = getComponentAt(i);
      if (!(component instanceof VersionPane versionPane)) {
        continue;
      }

      if (!versionPane.initialised) {
        continue;
      }

      final var name = userData.name() == null || userData.name().isBlank()
          ? objectEntry.getDisplayName(versionPane.version)
          : userData.name() + " - " + objectEntry.getDisplayName(versionPane.version);
      versionPane.labelName.setText(name);
      versionPane.textareaDescription.setText(userData.description());
    }
  }

  private SearchListener searchListener(RTextArea textArea) {
    return new SearchListener() {
      @Override
      public void searchEvent(SearchEvent e) {
        SearchEvent.Type type = e.getType();
        SearchContext context = e.getSearchContext();
        SearchResult result;

        switch (type) {
          default: // Prevent FindBugs warning later
          case MARK_ALL:
            result = SearchEngine.markAll(textArea, context);
            break;
          case FIND:
            result = SearchEngine.find(textArea, context);
            if (!result.wasFound() || result.isWrapped()) {
              UIManager.getLookAndFeel().provideErrorFeedback(textArea);
            }
            break;
          case REPLACE:
            result = SearchEngine.replace(textArea, context);
            if (!result.wasFound() || result.isWrapped()) {
              UIManager.getLookAndFeel().provideErrorFeedback(textArea);
            }
            break;
          case REPLACE_ALL:
            result = SearchEngine.replaceAll(textArea, context);
            JOptionPane.showMessageDialog(null, result.getCount() +
                                                " occurrences replaced.");
            break;
        }
      }

      @Override
      public String getSelectedText() {
        return null;
      }
    };
  }

}

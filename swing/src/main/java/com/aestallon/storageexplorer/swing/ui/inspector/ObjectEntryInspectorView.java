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
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
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
import com.aestallon.storageexplorer.swing.ui.misc.IconProvider;

public class ObjectEntryInspectorView extends JTabbedPane implements InspectorView<ObjectEntry> {

  private static final Logger log = LoggerFactory.getLogger(ObjectEntryInspectorView.class);

  private static final DateTimeFormatter FORMATTER_CREATION_DATE =
      DateTimeFormatter.ISO_ZONED_DATE_TIME;

  private final Action openInSystemExplorerAction = new AbstractAction(null, IconProvider.SE) {
    @Override
    public void actionPerformed(ActionEvent e) {
      if (objectEntry == null) {
        return;
      }

      if (!Desktop.isDesktopSupported()) {
        return;
      }

      try {
        final Path path = objectEntry.path();
        if (path != null) {
          Desktop.getDesktop().open(path.getParent().toFile());
        } else {
          JOptionPane.showMessageDialog(
              ObjectEntryInspectorView.this,
              "This entry location is not available on the file system.",
              "Info",
              JOptionPane.INFORMATION_MESSAGE);
        }
      } catch (final Exception ex) {
        log.warn("Could not open [ {} ] in system explorer!", objectEntry.uri());
        log.debug(ex.getMessage(), ex);
        JOptionPane.showMessageDialog(
            ObjectEntryInspectorView.this,
            "Could not show entry location in System Explorer!",
            "Error",
            JOptionPane.ERROR_MESSAGE);
      }
    }
  };

  private final ObjectEntry objectEntry;
  private final StorageEntryInspectorViewFactory factory;

  public ObjectEntryInspectorView(ObjectEntry objectEntry,
                                  StorageEntryInspectorViewFactory factory) {
    super(JTabbedPane.LEFT, JTabbedPane.SCROLL_TAB_LAYOUT);

    this.objectEntry = objectEntry;
    this.factory = factory;

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

  private void setUpObjectNodeDisplay(ObjectEntryLoadResult.MultiVersion multiVersion) {
    final var versions = multiVersion.versions();
    for (int i = 0; i < versions.size(); i++) {
      addTab(String.format("%02d", i), new VersionPane(versions.get(i)));
    }

    setSelectedIndex(versions.size() - 1);
  }

  private void setUpObjectNodeDisplay(
      ObjectEntryLoadResult.SingleVersion singleVersion) {
    addTab("SINGLE", new VersionPane(singleVersion));
    setSelectedIndex(0);
  }

  private final class VersionPane extends JPanel {

    private final ObjectEntryLoadResult.SingleVersion version;
    private boolean initialised = false;

    private VersionPane(final ObjectEntryLoadResult.SingleVersion version) {
      this.version = version;

      setLayout(new BoxLayout(VersionPane.this, BoxLayout.Y_AXIS));
      setBorder(new EmptyBorder(5, 5, 5, 5));

      if (version instanceof ObjectEntryLoadResult.SingleVersion.Eager) {
        initialise();
      }
    }

    private void initialise() {
      if (initialised) {
        return;
      }

      final var toolbar = new JToolBar(JToolBar.TOP);
      toolbar.setOrientation(SwingConstants.HORIZONTAL);
      toolbar.setBorder(new EmptyBorder(5, 0, 5, 0));
      factory.addRenderAction(objectEntry, toolbar);
      toolbar.add(openInSystemExplorerAction);
      factory.addEditMetaAction(objectEntry, toolbar);
      toolbar.add(Box.createHorizontalGlue());

      Box box = new Box(BoxLayout.X_AXIS);
      final var label = new JLabel(factory.trackingService().getUserData(objectEntry)
          .map(StorageEntryTrackingService.StorageEntryUserData::name)
          .orElseGet(() -> objectEntry.getDisplayName(version)));
      label.setFont(UIManager.getFont("h3.font"));
      label.setAlignmentX(Component.LEFT_ALIGNMENT);
      box.add(label);

      Box box1 = new Box(BoxLayout.X_AXIS);
      final var creationLabel = new JLabel("Created at:");
      creationLabel.setFont(UIManager.getFont("h4.font"));
      creationLabel.setBorder(new EmptyBorder(0, 0, 0, 5));

      final var creationValue = new JLabel(getNodeCreationValue(version));
      creationValue.setFont(UIManager.getFont("h4.font"));
      box1.add(creationLabel);
      box1.add(creationValue);

      final Box box1b = factory.trackingService().getUserData(objectEntry)
          .map(StorageEntryTrackingService.StorageEntryUserData::description)
          .filter(it -> !it.isBlank())
          .map(it -> {
            final Box b = new Box(BoxLayout.X_AXIS);
            final var description = new AutoSizingTextArea(it);
            description.setWrapStyleWord(true);
            description.setLineWrap(true);
            description.setEditable(false);
            description.setOpaque(false);
            description.setFont(UIManager.getFont("h4.font"));
            description.setBorder(new EmptyBorder(0, 0, 0, 0));
            description.setMinimumSize(new Dimension(0, 100));
            description.setColumns(0);

            b.add(description);
            return b;
          })
          .orElse(null);

      Box box2 = new Box(BoxLayout.X_AXIS);
      final var pane = factory.textareaFactory().create(objectEntry, version);
      toolbar.add(new AbstractAction(null, IconProvider.MAGNIFY) {
        @Override
        public void actionPerformed(ActionEvent e) {
          final var sd = new FindDialog((Frame) null, searchListener((RTextArea) pane.textArea()));
          sd.setVisible(true);
        }
      });
      box2.add(pane.scrollPane());

      add(toolbar);
      toolbar.setAlignmentX(Component.LEFT_ALIGNMENT);
      add(box);
      box.setAlignmentX(Component.LEFT_ALIGNMENT);
      add(box1);
      box1.setAlignmentX(Component.LEFT_ALIGNMENT);
      if (box1b != null) {
        add(box1b);
        box1b.setAlignmentX(Component.LEFT_ALIGNMENT);
      }
      add(box2);
      box2.setAlignmentX(Component.LEFT_ALIGNMENT);

      initialised = true;
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
    label.setFont(UIManager.getFont("h3.font"));
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

  private static final class AutoSizingTextArea extends JTextArea {

    private AutoSizingTextArea(final String text) {
      super(text);
      recalculate();
    }

    @Override
    public Dimension getPreferredSize() {
      final FontMetrics fm = getFontMetrics(getFont());
      final int rows = getRows();
      return new Dimension(super.getPreferredSize().width,
          fm.getHeight() * rows + fm.getDescent());
    }

    @Override
    public void setText(String t) {
      super.setText(t);
      recalculate();
    }

    private void recalculate() {
      setRows(getText().split("\n").length);
      setMaximumSize(new Dimension(Integer.MAX_VALUE, getPreferredSize().height * 2));
    }

  }

}

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

package hu.aestallon.storageexplorer.swing.ui.inspector;

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
import hu.aestallon.storageexplorer.storage.model.entry.ObjectEntry;
import hu.aestallon.storageexplorer.storage.model.loading.ObjectEntryLoadResult;
import hu.aestallon.storageexplorer.swing.ui.misc.IconProvider;

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

    final var result = objectEntry.tryLoad();
    switch (result) {
      case ObjectEntryLoadResult.Err err -> setUpLoadingErrorDisplay(err);
      case ObjectEntryLoadResult.SingleVersion sv -> setUpObjectNodeDisplay(sv);
      case ObjectEntryLoadResult.MultiVersion mv -> setUpObjectNodeDisplay(mv);
    }
  }

  private void setUpObjectNodeDisplay(ObjectEntryLoadResult.MultiVersion multiVersion) {
    final var versions = multiVersion.versions();
    for (int i = 0; i < versions.size(); i++) {
      addTab(String.format("%02d", i), versionPane(versions.get(i)));
    }
    setSelectedIndex(versions.size() - 1);
  }

  private void setUpObjectNodeDisplay(
      ObjectEntryLoadResult.SingleVersion singleVersion) {
    addTab("SINGLE", versionPane(singleVersion));
    setSelectedIndex(0);
  }

  private Component versionPane(final ObjectEntryLoadResult.SingleVersion objectNode) {
    final var container = new JPanel();
    container.setLayout(new BoxLayout(container, BoxLayout.Y_AXIS));
    container.setBorder(new EmptyBorder(5, 5, 5, 5));

    final var toolbar = new JToolBar(JToolBar.TOP);
    toolbar.setOrientation(SwingConstants.HORIZONTAL);
    toolbar.setBorder(new EmptyBorder(5, 0, 5, 0));
    factory.addRenderAction(objectEntry, toolbar);
    toolbar.add(openInSystemExplorerAction);
    toolbar.add(Box.createHorizontalGlue());

    Box box = new Box(BoxLayout.X_AXIS);
    final var label = new JLabel(objectEntry.toString());
    label.setFont(UIManager.getFont("h3.font"));
    label.setAlignmentX(Component.LEFT_ALIGNMENT);
    box.add(label);
    box.add(Box.createHorizontalGlue());

    Box box1 = new Box(BoxLayout.X_AXIS);
    final var creationLabel = new JLabel("Created at:");
    creationLabel.setFont(UIManager.getFont("h4.font"));
    creationLabel.setBorder(new EmptyBorder(0, 0, 0, 5));
    label.setAlignmentX(Component.LEFT_ALIGNMENT);

    final var creationValue = new JLabel(getNodeCreationValue(objectNode));
    creationValue.setFont(UIManager.getFont("h4.font"));
    creationValue.setAlignmentX(Component.LEFT_ALIGNMENT);
    box1.add(creationLabel);
    box1.add(creationValue);
    box1.add(Box.createHorizontalGlue());

    Box box2 = new Box(BoxLayout.X_AXIS);
    final var pane = factory.textareaFactory().create(objectEntry, objectNode);
    toolbar.add(new AbstractAction(null, IconProvider.MAGNIFY) {
      @Override
      public void actionPerformed(ActionEvent e) {
        final var sd = new FindDialog((Frame) null, searchListener((RTextArea) pane.textArea));
        sd.setVisible(true);
      }
    });

    box2.add(pane.scrollPane);
    box2.add(Box.createHorizontalGlue());

    container.add(toolbar);
    container.add(box);
    container.add(box1);
    container.add(box2);
    return container;
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

}

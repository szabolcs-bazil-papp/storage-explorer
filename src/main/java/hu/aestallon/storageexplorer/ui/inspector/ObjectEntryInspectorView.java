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
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.smartbit4all.core.object.ObjectNode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import hu.aestallon.storageexplorer.domain.storage.model.ObjectEntry;
import hu.aestallon.storageexplorer.util.Uris;

public class ObjectEntryInspectorView extends JTabbedPane implements InspectorView<ObjectEntry> {

  private static final Logger log = LoggerFactory.getLogger(ObjectEntryInspectorView.class);

  private final ObjectEntry objectEntry;
  private final ObjectMapper objectMapper;
  private final StorageEntryInspectorViewFactory factory;
  private final ObjectNode objectNode;

  public ObjectEntryInspectorView(ObjectEntry objectEntry, ObjectMapper objectMapper,
                                  StorageEntryInspectorViewFactory factory) {
    super(JTabbedPane.LEFT, JTabbedPane.SCROLL_TAB_LAYOUT);

    this.objectEntry = objectEntry;
    this.objectMapper = objectMapper;
    this.factory = factory;

    final var result = objectEntry.tryLoad();
    if (result.isOk()) {
      objectNode = result.objectNode();
      setUpObjectNodeDisplay(objectEntry);
    } else {
      objectNode = null;
      setUpLoadingErrorDisplay(result);
    }
  }

  private void setUpObjectNodeDisplay(ObjectEntry objectEntry) {
    final long versionNr = versionNr();
    if (versionNr == 0L) {
      addTab(
          Uris.isSingleVersion(objectEntry.uri()) ? "SINGLE" : "00",
          versionPane(objectNode));
    } else {
      for (int i = 0; i <= versionNr; i++) {
        final var versionedNode = objectEntry.load(i);
        addTab(String.format("%02d", i), versionPane(versionedNode));
      }
    }
    setSelectedIndex((int) versionNr);
  }

  private long versionNr() {
    final Long boxed = objectNode.getVersionNr();
    return (boxed == null) ? 0 : boxed;
  }

  private JTextArea objectAsMapTextarea(final ObjectNode objectNode) {
    final var textarea = new JTextArea(getObjectAsMap(objectNode), 0, 80);
    textarea.setWrapStyleWord(true);
    textarea.setLineWrap(true);
    textarea.setEditable(false);
    textarea.setOpaque(false);
    textarea.setFont(factory.monospaceFontProvider().getFont());

    factory.addJumpAction(textarea);
    factory.monospaceFontProvider().applyFontSizeChangeAction(textarea);

    factory.submitTextArea(objectEntry, textarea);

    return textarea;
  }

  private Component versionPane(final ObjectNode objectNode) {
    final var container = new JPanel();
    container.setLayout(new BoxLayout(container, BoxLayout.Y_AXIS));
    container.setBorder(new EmptyBorder(5, 5, 5, 5));

    final var toolbar = new JToolBar(JToolBar.TOP);
    factory.addRenderAction(objectEntry, toolbar);

    final var label = new JLabel(objectEntry.toString());
    label.setFont(UIManager.getFont("h3.font"));
    label.setAlignmentX(Component.LEFT_ALIGNMENT);

    final var separator = new JSeparator();
    separator.setAlignmentX(Component.LEFT_ALIGNMENT);

    final var objectAsMapTextarea = objectAsMapTextarea(objectNode);
    final var pane = textAreaContainerPane(objectAsMapTextarea);

    container.add(toolbar);
    container.add(label);
    //container.add(separator);
    container.add(pane);
    //container.add(Box.createVerticalGlue());
    return container;
  }

  private void setUpLoadingErrorDisplay(final ObjectEntry.ObjectEntryLoadResult loadResult) {
    addTab("ERROR", errorPane(loadResult));
    setSelectedIndex(0);
  }

  private JComponent errorPane(final ObjectEntry.ObjectEntryLoadResult loadResult) {
    final var container = new JPanel();
    container.setLayout(new BoxLayout(container, BoxLayout.Y_AXIS));
    container.setBorder(new EmptyBorder(5, 5, 5, 5));

    final var label = new JLabel(objectEntry.toString() + " LOADING ERROR");
    label.setFont(UIManager.getFont("h3.font"));
    label.setAlignmentX(Component.LEFT_ALIGNMENT);

    container.add(label);
    container.add(errorMessageDisplay(loadResult.errorMessage()));
    return container;
  }

  private JScrollPane errorMessageDisplay(final String errorMessage) {
    final var textarea = new JTextArea(errorMessage, 0, 80);
    textarea.setWrapStyleWord(true);
    textarea.setLineWrap(true);
    textarea.setEditable(false);
    textarea.setOpaque(false);
    textarea.setFont(factory.monospaceFontProvider().getFont());
    return textAreaContainerPane(textarea);
  }

  private static JScrollPane textAreaContainerPane(JTextArea objectAsMapTextarea) {
    final var pane = new JScrollPane(
        objectAsMapTextarea,
        ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
        ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    pane.setAlignmentX(Component.LEFT_ALIGNMENT);
    return pane;
  }

  private String getObjectAsMap(final ObjectNode objectNode) {
    try {
      return objectMapper
          .writerWithDefaultPrettyPrinter()
          .writeValueAsString(objectNode.getObjectAsMap());
    } catch (JsonProcessingException e) {
      log.error(e.getMessage(), e);
      return "Cannot render object-as-map: " + e.getMessage();
    }
  }

  @Override
  public ObjectEntry storageEntry() {
    return objectEntry;
  }

}

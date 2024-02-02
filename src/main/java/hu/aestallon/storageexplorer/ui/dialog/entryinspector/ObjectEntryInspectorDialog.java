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

package hu.aestallon.storageexplorer.ui.dialog.entryinspector;

import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.io.IOException;
import java.net.URI;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.smartbit4all.core.object.ObjectNode;
import org.smartbit4all.domain.data.storage.ObjectStorageImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import hu.aestallon.storageexplorer.domain.storage.model.ObjectEntry;
import hu.aestallon.storageexplorer.util.Uris;

public class ObjectEntryInspectorDialog extends JFrame {

  private static final Logger log = LoggerFactory.getLogger(ObjectEntryInspectorDialog.class);

  private final ObjectEntry objectEntry;
  private final ObjectMapper objectMapper;
  private ObjectNode objectNode;

  public ObjectEntryInspectorDialog(ObjectEntry objectEntry, ObjectMapper objectMapper) {
    super(objectEntry.toString());

    this.objectEntry = objectEntry;
    this.objectNode = objectEntry.load();
    this.objectMapper = objectMapper;

    add(nodePane());
    pack();
  }

  private JTabbedPane nodePane() {
    final var pane = new JTabbedPane(JTabbedPane.LEFT, JTabbedPane.SCROLL_TAB_LAYOUT);
    final long versionNr = versionNr();
    if (versionNr == 0L) {
      pane.addTab(
          Uris.isSingleVersion(objectEntry.uri()) ? "SINGLE" : "00",
          versionPane(objectNode));
    } else {
      for (int i = 0; i <= versionNr; i++) {
        final var versionedNode = objectEntry.load(i);
        pane.addTab(String.format("%02d", i), versionPane(versionedNode));
      }
    }
    pane.setSelectedIndex((int) versionNr);
    return pane;
  }

  private long versionNr() {
    final Long boxed = objectNode.getVersionNr();
    return (boxed == null) ? 0 : boxed;
  }

  private JScrollPane objectMapPane() {
    return objectMapPane(objectNode);
  }

  private JScrollPane objectMapPane(final ObjectNode objectNode) {
    final var textarea = objectAsMapTextarea(objectNode);
    return new JScrollPane(
        textarea,
        ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
        ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
    );
  }

  private JTextArea objectAsMapTextarea(final ObjectNode objectNode) {
    final var textarea = new JTextArea(getObjectAsMap(objectNode), 0, 80);
    textarea.setWrapStyleWord(true);
    textarea.setLineWrap(true);
    textarea.setEditable(false);
    // textarea.setFocusable(false);
    textarea.setOpaque(false);
    textarea.setFont(getMonoType().deriveFont(12f));
    return textarea;
  }

  private Component versionPane(final ObjectNode objectNode) {
    final var container = new JPanel();
    container.setLayout(new BoxLayout(container, BoxLayout.Y_AXIS));
    container.setBorder(new EmptyBorder(5, 5, 5, 5));

    final var label = new JLabel(objectEntry.toString());
    label.setFont(UIManager.getFont("h3.font"));
    label.setAlignmentX(Component.LEFT_ALIGNMENT);

    final var separator = new JSeparator();
    separator.setAlignmentX(Component.LEFT_ALIGNMENT);

    final var objectAsMapTextarea = objectAsMapTextarea(objectNode);
    final var pane = objectAsMapScrollPane(objectAsMapTextarea);
    container.add(label);
    container.add(separator);
    container.add(pane);

    return container;
  }

  private static JScrollPane objectAsMapScrollPane(JTextArea objectAsMapTextarea) {
    final var pane = new JScrollPane(
        objectAsMapTextarea,
        ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
        ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
    );
    pane.setAlignmentX(Component.LEFT_ALIGNMENT);
    pane.addComponentListener(new ComponentAdapter() {

      @Override
      public void componentResized(ComponentEvent e) {
        final var size = e.getComponent().getSize();
        objectAsMapTextarea.setPreferredSize(size);
      }

    });
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

  private static Font monotype = null;

  private static Font getMonoType() {
    if (monotype != null) {
      return monotype;
    }

    try (final var in = ObjectEntryInspectorDialog.class.getResourceAsStream(
        "/fonts/JetBrainsMono-Regular.ttf")) {
      monotype = Font.createFont(Font.TRUETYPE_FONT, in);
    } catch (IOException | FontFormatException e) {
      log.error(e.getMessage(), e);
    }

    return monotype;
  }

}

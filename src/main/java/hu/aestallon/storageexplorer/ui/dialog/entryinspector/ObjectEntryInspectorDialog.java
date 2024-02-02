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

import javax.swing.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.smartbit4all.core.object.ObjectNode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import hu.aestallon.storageexplorer.domain.storage.model.ObjectEntry;

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

    add(objectMapPane());
    pack();
  }

  private JScrollPane objectMapPane() {
    final var textarea = new JTextArea(getObjectAsMap(), 30, 60);
    textarea.setWrapStyleWord(true);
    textarea.setLineWrap(true);
    textarea.setEditable(false);
    textarea.setFocusable(false);
    textarea.setOpaque(false);

    return new JScrollPane(
        textarea,
        ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
        ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
    );
  }

  private String getObjectAsMap() {
    try {
      return objectMapper
          .writerWithDefaultPrettyPrinter()
          .writeValueAsString(objectNode.getObjectAsMap());
    } catch (JsonProcessingException e) {
      log.error(e.getMessage(), e);
      return "Cannot render object-as-map: " + e.getMessage();
    }
  }

}

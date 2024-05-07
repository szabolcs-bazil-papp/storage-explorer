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
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rtextarea.RTextScrollPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.smartbit4all.core.object.ObjectNode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import hu.aestallon.storageexplorer.domain.storage.model.ObjectEntry;
import hu.aestallon.storageexplorer.ui.misc.MonospaceFontProvider;


public final class InspectorTextareaFactory {

  private static final Logger log = LoggerFactory.getLogger(InspectorTextareaFactory.class);


  public static final class PaneAndTextarea {
    public final JScrollPane scrollPane;
    public final JTextArea textArea;

    PaneAndTextarea(JScrollPane scrollPane, JTextArea textArea) {
      this.scrollPane = scrollPane;
      this.textArea = textArea;
    }

  }


  public enum Type { SIMPLE, FANCY; }

  static JScrollPane textAreaContainerPane(JTextArea objectAsMapTextarea) {
    final var pane = new JScrollPane(
        objectAsMapTextarea,
        ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
        ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    pane.setPreferredSize(new Dimension(2000, 2000));
    return pane;
  }

  private final StorageEntryInspectorViewFactory inspectorViewFactory;
  private final MonospaceFontProvider monospaceFontProvider;
  private final ObjectMapper objectMapper = new ObjectMapper();

  public InspectorTextareaFactory(StorageEntryInspectorViewFactory inspectorViewFactory) {
    this.inspectorViewFactory = inspectorViewFactory;
    this.monospaceFontProvider = inspectorViewFactory.monospaceFontProvider();
  }

  public InspectorTextareaFactory(MonospaceFontProvider monospaceFontProvider) {
    this.monospaceFontProvider = monospaceFontProvider;
    this.inspectorViewFactory = null;
  }

  PaneAndTextarea create(final ObjectEntry objectEntry, final ObjectNode objectNode) {
    return create(objectEntry, objectNode, Type.FANCY);
  }

  private PaneAndTextarea create(final ObjectEntry objectEntry, final ObjectNode objectNode,
                                 final Type type) {
    switch (type) {
      case SIMPLE:
        return createSimple(objectEntry, objectNode);
      case FANCY:
        return createFancy(objectEntry, objectNode);
      default:
        throw new AssertionError(type);
    }
  }

  public PaneAndTextarea createEditor(final ObjectEntry objectEntry, final ObjectNode objectNode) {
    return createEditorInternal(objectNode);
  }

  private PaneAndTextarea createSimple(final ObjectEntry objectEntry, final ObjectNode objectNode) {
    final var textarea = objectAsMapTextarea(objectEntry, objectNode);
    final var pane = textAreaContainerPane(textarea);
    return new PaneAndTextarea(pane, textarea);
  }

  private PaneAndTextarea createFancy(final ObjectEntry objectEntry, final ObjectNode objectNode) {
    final var textarea = new RSyntaxTextArea(getObjectAsMap(objectNode));
    textarea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JSON);
    textarea.setCodeFoldingEnabled(true);
    textarea.setWrapStyleWord(true);
    textarea.setLineWrap(true);
    textarea.setEditable(false);
    textarea.setFont(inspectorViewFactory.monospaceFontProvider().getFont());

    inspectorViewFactory.addJumpAction(textarea);
    inspectorViewFactory.monospaceFontProvider().applyFontSizeChangeAction(textarea);

    inspectorViewFactory.submitTextArea(objectEntry, textarea);

    final var scrollPane = new RTextScrollPane(textarea);
    //scrollPane.add(textarea);
    return new PaneAndTextarea(scrollPane, textarea);
  }

  private PaneAndTextarea createEditorInternal(final ObjectNode objectNode) {
    final var textarea = new RSyntaxTextArea(getObjectAsMap(objectNode));
    textarea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JSON);
    textarea.setCodeFoldingEnabled(true);
    textarea.setWrapStyleWord(true);
    textarea.setLineWrap(true);
    textarea.setEditable(true);
    textarea.setFont(monospaceFontProvider.getFont());
    monospaceFontProvider.applyFontSizeChangeAction(textarea);

    final var scrollPane = new RTextScrollPane(textarea);
    return new PaneAndTextarea(scrollPane, textarea);
  }

  private JTextArea objectAsMapTextarea(final ObjectEntry objectEntry,
                                        final ObjectNode objectNode) {
    final var textarea = new JTextArea(getObjectAsMap(objectNode), 0, 80);
    textarea.setWrapStyleWord(true);
    textarea.setLineWrap(true);
    textarea.setEditable(false);
    textarea.setOpaque(false);
    textarea.setFont(inspectorViewFactory.monospaceFontProvider().getFont());

    inspectorViewFactory.addJumpAction(textarea);
    inspectorViewFactory.monospaceFontProvider().applyFontSizeChangeAction(textarea);

    inspectorViewFactory.submitTextArea(objectEntry, textarea);

    return textarea;
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

}

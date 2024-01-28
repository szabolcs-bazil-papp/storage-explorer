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

package hu.aestallon.storageexplorer.ui;

import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.io.IOException;
import java.net.URI;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.*;
import org.graphstream.graph.Graph;
import org.graphstream.graph.Node;
import org.graphstream.graph.implementations.MultiGraph;
import org.graphstream.stream.file.FileSinkImages;
import org.graphstream.stream.file.images.Resolutions;
import org.graphstream.ui.spriteManager.SpriteManager;
import org.graphstream.ui.swing.util.SwingFileSinkImages;
import org.graphstream.ui.swing_viewer.SwingViewer;
import org.graphstream.ui.swing_viewer.ViewPanel;
import org.graphstream.ui.view.Viewer;
import org.graphstream.ui.view.ViewerListener;
import org.graphstream.ui.view.ViewerPipe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.smartbit4all.core.utility.StringConstant;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import hu.aestallon.storageexplorer.service.GraphRenderingService;
import hu.aestallon.storageexplorer.util.Attributes;

@Component
public class StorageGraph {

  private static final Logger log = LoggerFactory.getLogger(StorageGraph.class);
  public static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm");

  private Graph graph;
  private Viewer viewer;
  private ViewPanel panel;
  private SpriteManager sprites;

  private final GraphRenderingService graphRenderingService;
  private final ObjectMapper objectMapper;

  public StorageGraph(GraphRenderingService graphRenderingService, ObjectMapper objectMapper) {
    this.graphRenderingService = graphRenderingService;
    this.objectMapper = objectMapper;
  }

  ViewPanel init() {
    graph = new MultiGraph("fs");
    sprites = new SpriteManager(graph);
    viewer = new SwingViewer(graph, Viewer.ThreadingModel.GRAPH_IN_GUI_THREAD);
    ViewerPipe pipe = viewer.newViewerPipe();
    MouseListener list = new MouseListener(pipe);
    pipe.addViewerListener(list);

    pipe.addSink(graph);
    panel = (ViewPanel) viewer.addDefaultView(false);
    panel.enableMouseOptions();
    panel.addKeyListener(new ScreenshotListener());
    CompletableFuture.runAsync(list::pump);
    return panel;
  }

  void initOnUri(URI uri) {
    graph.clear();
    graph.setAttribute("ui.stylesheet", "url('./styles.css')");
    graphRenderingService.render(graph, uri);
    graph.nodes()
        .filter(Objects::nonNull)
        .filter(it -> it.getDegree() == 0
            || "null".equals(String.valueOf(it.getAttribute(Attributes.OBJECT_AS_MAP))))
        .forEach(it -> graph.removeNode(it.getId()));
    viewer.enableAutoLayout();
    ViewerPipe pipe = viewer.newViewerPipe();
    MouseListener list = new MouseListener(pipe);
    pipe.addViewerListener(list);

    pipe.addSink(graph);
    CompletableFuture.runAsync(list::pump);
  }

  private final class MouseListener implements ViewerListener {

    private final AtomicBoolean doPump = new AtomicBoolean(true);
    private final ViewerPipe pipe;

    private MouseListener(ViewerPipe pipe) {this.pipe = pipe;}


    void pump() {
      while (doPump.get()) {
        pipe.pump();
      }
    }

    @Override
    public void viewClosed(String s) {
      doPump.set(false);
    }

    @Override
    public void buttonPushed(String s) {
      final Node node = graph.getNode(s);

      final Object objectAsMap = node.getAttribute(Attributes.OBJECT_AS_MAP);
      final String typeName = String.valueOf(node.getAttribute(Attributes.TYPE_NAME));
      SwingUtilities.invokeLater(() -> {
        final var dialog = new JFrame(typeName);
        final var pane = new JPanel();
        final var textarea = new JTextArea();
        textarea.setEnabled(false);
        textarea.setText(typeName + StringConstant.SPACE + prettyPrint(objectAsMap));
        pane.add(textarea);
        dialog.add(pane);
        dialog.setLocationRelativeTo(panel);
        dialog.setSize(250, 250);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        dialog.pack();
        dialog.setVisible(true);
      });
    }

    private String prettyPrint(Object o) {
      try {
        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(o);
      } catch (JsonProcessingException e) {
        log.error(e.getMessage(), e);
        return StringConstant.EMPTY;
      }
    }

    @Override
    public void buttonReleased(String s) {}

    @Override
    public void mouseOver(String s) {}

    @Override
    public void mouseLeft(String s) {}

  }


  private final class ScreenshotListener implements KeyListener {
    @Override
    public void keyTyped(KeyEvent e) {}

    @Override
    public void keyPressed(KeyEvent e) {
      if (e != null && e.getKeyCode() == KeyEvent.VK_F12) {
        final var img = new SwingFileSinkImages(FileSinkImages.OutputType.PNG, Resolutions.WUXGA);
        img.setLayoutPolicy(FileSinkImages.LayoutPolicy.COMPUTED_FULLY_AT_NEW_IMAGE);
        try {
          img.writeAll(graph, "./screens/" + DTF.format(LocalDateTime.now()) + ".png");
        } catch (IOException ex) {
          log.error(ex.getMessage(), ex);
        }

      }
    }

    @Override
    public void keyReleased(KeyEvent e) {}

  }

}

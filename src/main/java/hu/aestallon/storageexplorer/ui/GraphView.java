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

import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.io.IOException;
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
import org.springframework.stereotype.Component;
import hu.aestallon.storageexplorer.domain.graph.service.GraphRenderingService;
import hu.aestallon.storageexplorer.domain.storage.model.StorageEntry;
import hu.aestallon.storageexplorer.ui.dialog.entryinspector.StorageEntryInspectorDialogFactory;
import hu.aestallon.storageexplorer.util.Attributes;

@Component
public class GraphView extends JPanel {

  private static final Logger log = LoggerFactory.getLogger(GraphView.class);
  public static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm");

  private Graph graph;
  private Viewer viewer;
  private ViewPanel panel;
  private SpriteManager sprites;
  private KeyListener screenshotListener;

  private final GraphRenderingService graphRenderingService;
  private final StorageEntryInspectorDialogFactory storageEntryInspectorDialogFactory;
  private MouseListener clickHandler;

  public GraphView(GraphRenderingService graphRenderingService,
                   StorageEntryInspectorDialogFactory storageEntryInspectorDialogFactory) {
    super(new GridLayout(1, 1));
    this.graphRenderingService = graphRenderingService;
    this.storageEntryInspectorDialogFactory = storageEntryInspectorDialogFactory;
  }

  void init(StorageEntry storageEntry) {
    if (clickHandler != null) {
      clickHandler.doPump.set(false);
    }
    if (screenshotListener != null) {
      panel.removeKeyListener(screenshotListener);
    }
    if (panel != null) {
      remove(panel);
    }
    if (graph != null) {
      graph.clear();
    }
    graph = new MultiGraph("fs");
    sprites = new SpriteManager(graph);

    graph.setAttribute("ui.stylesheet", "url('./styles.css')");
    graphRenderingService.render(graph, storageEntry);
    graph.nodes()
        .filter(Objects::nonNull)
        .filter(it -> it.getDegree() == 0)
        .forEach(it -> graph.removeNode(it.getId()));

    viewer = new SwingViewer(graph, Viewer.ThreadingModel.GRAPH_IN_GUI_THREAD);

    viewer.enableAutoLayout();

    panel = (ViewPanel) viewer.addDefaultView(false);
    panel.enableMouseOptions();

    ViewerPipe pipe = viewer.newViewerPipe();
    clickHandler = new MouseListener(pipe);
    pipe.addViewerListener(clickHandler);

    pipe.addSink(graph);
    CompletableFuture.runAsync(clickHandler::pump);
    screenshotListener = new ScreenshotListener();
    panel.addKeyListener(screenshotListener);
    add(panel);
    revalidate();
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
      final Object entry = node.getAttribute(Attributes.STORAGE_ENTRY);
      if (!(entry instanceof StorageEntry)) {
        JOptionPane.showMessageDialog(
            GraphView.this,
            "No entry under node!",
            "Error",
            JOptionPane.ERROR_MESSAGE);
        return;
      }

      SwingUtilities.invokeLater(() -> storageEntryInspectorDialogFactory.showDialog(
          (StorageEntry) entry,
          GraphView.this));
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

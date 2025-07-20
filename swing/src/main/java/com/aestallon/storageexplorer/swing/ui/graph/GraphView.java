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

package com.aestallon.storageexplorer.swing.ui.graph;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.swing.*;
import org.graphstream.graph.Graph;
import org.graphstream.graph.implementations.MultiGraph;
import org.graphstream.stream.file.FileSinkImages;
import org.graphstream.stream.file.images.Resolutions;
import org.graphstream.ui.geom.Point3;
import org.graphstream.ui.graphicGraph.GraphicElement;
import org.graphstream.ui.graphicGraph.GraphicGraph;
import org.graphstream.ui.layout.springbox.implementations.LinLog;
import org.graphstream.ui.spriteManager.SpriteManager;
import org.graphstream.ui.swing.util.SwingFileSinkImages;
import org.graphstream.ui.swing_viewer.SwingViewer;
import org.graphstream.ui.swing_viewer.ViewPanel;
import org.graphstream.ui.swing_viewer.util.MouseOverMouseManager;
import org.graphstream.ui.view.View;
import org.graphstream.ui.view.Viewer;
import org.graphstream.ui.view.camera.Camera;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import com.aestallon.storageexplorer.client.graph.service.GraphRenderingService;
import com.aestallon.storageexplorer.client.storage.StorageInstanceProvider;
import com.aestallon.storageexplorer.client.userconfig.event.GraphConfigChanged;
import com.aestallon.storageexplorer.client.userconfig.service.UserConfigService;
import com.aestallon.storageexplorer.core.event.EntryInspectionEvent;
import com.aestallon.storageexplorer.core.model.entry.StorageEntry;
import com.aestallon.storageexplorer.core.model.instance.StorageInstance;
import com.aestallon.storageexplorer.swing.ui.controller.ViewController;
import com.aestallon.storageexplorer.swing.ui.event.LafChanged;
import com.aestallon.storageexplorer.swing.ui.misc.GraphStylingProvider;
import com.aestallon.storageexplorer.swing.ui.misc.IconProvider;
import com.aestallon.storageexplorer.swing.ui.misc.LafService;

@Component
public class GraphView extends JPanel {

  private static final Logger log = LoggerFactory.getLogger(GraphView.class);
  public static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm");

  private transient Graph graph;
  private transient Viewer viewer;
  private ViewPanel panel;
  private JPanel overlay;
  private transient @SuppressWarnings("unused") SpriteManager sprites;
  private transient KeyListener screenshotListener;
  private transient StorageEntry origin;
  private transient StorageEntry currentHighlight;
  private transient GraphRenderingService graphRenderingService;
  private transient Future<?> rendering;

  private final transient ExecutorService executorService = Executors.newSingleThreadExecutor();
  private final transient StorageInstanceProvider storageInstanceProvider;
  private final transient ApplicationEventPublisher eventPublisher;
  private final transient UserConfigService userConfigService;
  private final transient LafService lafService;

  public GraphView(StorageInstanceProvider storageInstanceProvider,
                   ApplicationEventPublisher eventPublisher,
                   UserConfigService userConfigService, LafService lafService) {
    this.storageInstanceProvider = storageInstanceProvider;
    this.eventPublisher = eventPublisher;
    this.userConfigService = userConfigService;
    this.lafService = lafService;

    setLayout(new OverlayLayout(this));
    setMinimumSize(new Dimension(500, 500));
  }

  public void init(StorageEntry storageEntry) {
    if (screenshotListener != null) {
      panel.removeKeyListener(screenshotListener);
    }

    abortRendering(false);

    if (panel != null) {
      remove(panel);
    }
    if (overlay != null) {
      remove(overlay);
    }
    if (graph != null) {
      graph.clear();
    }

    final StorageInstance storageInstance = storageInstanceProvider.storageInstanceOf(storageEntry);
    final var userConfig = userConfigService.graphSettings();
    graphRenderingService = new GraphRenderingService(storageInstance, userConfig);
    origin = storageEntry;

    graph = new MultiGraph("fs");
    sprites = new SpriteManager(graph);

    graph.setAttribute("ui.stylesheet", switch (lafService.getLaf()) {
      case DARK -> GraphStylingProvider.DARK;
      case LIGHT -> GraphStylingProvider.LIGHT;
    });
    graph.setAttribute("ui.antialias");
    graph.setAttribute("ui.quality");

    viewer = new SwingViewer(graph, Viewer.ThreadingModel.GRAPH_IN_ANOTHER_THREAD);
    viewer.enableAutoLayout(new LinLog());

    panel = (ViewPanel) viewer.addDefaultView(false);
    panel.enableMouseOptions();
    GraphViewMouseManager mouseManager = new GraphViewMouseManager();
    panel.setMouseManager(mouseManager);
    panel.addMouseWheelListener(mouseManager);

    screenshotListener = new ScreenshotListener();
    panel.addKeyListener(screenshotListener);

    overlay = overlay();
    add(overlay);
    add(panel);
    setVisible(true);
    revalidate();
    rendering = CompletableFuture.runAsync(() -> graphRenderingService.render(graph, storageEntry));
  }

  private void abortRendering(final boolean shutdown) {
    if (rendering != null) {
      switch (rendering.state()) {
        case RUNNING -> {
          rendering.cancel(true);
        }
        case null, default -> {
          log.info("No rendering in progress!");
        }
      }
    }

    rendering = null;
    if (shutdown) {
      executorService.shutdownNow();
    }
  }

  private void startRendering(final StorageEntry storageEntry) {
    rendering = executorService.submit(() -> graphRenderingService.render(graph, storageEntry));
  }

  private JPanel overlay() {
    final var overlayPanel = new JPanel();
    overlayPanel.setOpaque(false);
    overlayPanel.setLayout(new BoxLayout(overlayPanel, BoxLayout.Y_AXIS));

    final var closeBtn = new JButton(IconProvider.CLOSE);
    closeBtn.addActionListener(
        e -> eventPublisher.publishEvent(new ViewController.GraphViewCloseRequest()));
    closeBtn.setAlignmentY(TOP_ALIGNMENT);
    closeBtn.setAlignmentX(RIGHT_ALIGNMENT);

    final var box = Box.createHorizontalBox();
    box.setOpaque(false);
    box.add(Box.createGlue());
    box.add(closeBtn);

    overlayPanel.add(box);
    return overlayPanel;
  }

  public void discard() {
    setVisible(false);
    if (panel != null) {
      remove(panel);
      panel = null;
    }

    abortRendering(true);

    if (overlay != null) {
      remove(overlay);
      overlay = null;
    }

    viewer = null;
    sprites = null;
    if (graph != null) {
      graph.clear();
      graph = null;
    }
    currentHighlight = null;
    origin = null;
    screenshotListener = null;
  }

  public boolean displayingStorageAt(final StorageInstance storageInstance) {
    return origin != null && origin.storageId().equals(storageInstance.id());
  }

  public void select(final StorageEntry storageEntry) {
    if (graph == null || panel == null || Objects.equals(currentHighlight, storageEntry)) {
      return;
    }

    graphRenderingService.changeHighlight(graph, currentHighlight, storageEntry);
    currentHighlight = storageEntry;
  }

  private final class GraphViewMouseManager
      extends MouseOverMouseManager
      implements MouseWheelListener {

    @Override
    public void init(GraphicGraph graphicGraph, View view) {
      super.init(graphicGraph, view);
      view.addListener("Mouse", this);
    }

    @Override
    public void release() {
      super.release();
      view.removeListener("Mouse", this);
    }

    private int x = -1;
    private int y = -1;

    @Override
    public void mouseDragged(MouseEvent event) {
      if (SwingUtilities.isLeftMouseButton(event)) {
        if (x != -1 && y != -1) {
          final var camera = view.getCamera();
          Point3 b = camera.transformPxToGu(event.getX(), event.getY());
          Point3 a = camera.transformPxToGu(x, y);
          Point3 diff = new Point3(b.x - a.x, b.y - a.y, 0);
          final var vc = camera.getViewCenter();
          camera.setViewCenter(vc.x - diff.x, vc.y - diff.y, vc.z);
        }
        x = event.getX();
        y = event.getY();

      } else {
        super.mouseDragged(event);
      }
    }

    @Override
    public void mouseMoved(MouseEvent event) {
      x = -1;
      y = -1;
      super.mouseMoved(event);
    }

    @Override
    public void mouseClicked(MouseEvent e) {
      super.mouseClicked(e);

      if (e.getButton() == MouseEvent.BUTTON1) {
        getStorageEntry(e)
            .map(EntryInspectionEvent::new)
            .ifPresent(eventPublisher::publishEvent);
      }
    }

    @Override
    public void mousePressed(MouseEvent e) {
      super.mousePressed(e);

      final var entry = getStorageEntry(e);
      if (entry.isEmpty()) {
        return;
      }

      if (e.getButton() == MouseEvent.BUTTON3) {
        showNodePopup(entry.get(), e.getX(), e.getY());
      }
    }

    private void showNodePopup(final StorageEntry entry, final int x, final int y) {
      final var popup = new NodePopupMenu(entry, graph, graphRenderingService);
      popup.show(GraphView.this, x, y);
    }

    private Optional<StorageEntry> getStorageEntry(MouseEvent e) {
      final GraphicElement node = view.findGraphicElementAt(getManagedTypes(), e.getX(), e.getY());
      if (node == null) {
        return Optional.empty();
      }

      final String id = node.getId();
      if (id == null || id.isEmpty()) {
        JOptionPane.showMessageDialog(
            GraphView.this,
            "No entry under node!",
            "Error",
            JOptionPane.ERROR_MESSAGE);
        return Optional.empty();
      }

      final var entry = graphRenderingService.getStorageEntry(id);
      if (entry.isEmpty()) {
        JOptionPane.showMessageDialog(
            GraphView.this,
            "Id [ " + id + " ] is unknown!",
            "Error",
            JOptionPane.ERROR_MESSAGE);
        return Optional.empty();
      }
      return entry;
    }

    @Override
    public void mouseWheelMoved(MouseWheelEvent e) {
      final Camera camera = view.getCamera();
      final double viewPercent = camera.getViewPercent();
      final double delta = (double) e.getWheelRotation() / 100;
      final double newZoom = viewPercent + delta;
      camera.setViewPercent(Math.clamp(newZoom, 0.01d, 1));
    }
  }


  private final class ScreenshotListener implements KeyListener {

    @Override
    public void keyTyped(KeyEvent e) { /* NO OP */ }

    @Override
    public void keyPressed(KeyEvent e) {
      if (e != null && e.getKeyCode() == KeyEvent.VK_F12) {
        final var img = new SwingFileSinkImages(FileSinkImages.OutputType.PNG, Resolutions.UHD_4K);
        img.setLayoutPolicy(FileSinkImages.LayoutPolicy.COMPUTED_FULLY_AT_NEW_IMAGE);
        try {
          img.writeAll(graph, "./screens/" + DTF.format(LocalDateTime.now()) + ".png");
        } catch (IOException ex) {
          log.error(ex.getMessage(), ex);
        }
      }
    }

    @Override
    public void keyReleased(KeyEvent e) { /* NO OP */ }

  }


  @EventListener
  public void onGraphConfigurationChanged(GraphConfigChanged e) {
    SwingUtilities.invokeLater(() -> {
      if (graphRenderingService == null) {
        return;
      }

      if (origin == null) {
        return;
      }

      init(origin);
    });
  }

  @EventListener
  public void onLafChanged(final LafChanged event) {
    if (graph == null) {
      return;
    }

    SwingUtilities.invokeLater(() -> graph.setAttribute("ui.stylesheet", switch (event.laf()) {
      case DARK -> GraphStylingProvider.DARK;
      case LIGHT -> GraphStylingProvider.LIGHT;
    }));
  }
}

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
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.Optional;
import javax.swing.*;
import org.graphstream.graph.Graph;
import org.graphstream.graph.implementations.MultiGraph;
import org.graphstream.stream.file.FileSinkImages;
import org.graphstream.stream.file.images.Resolutions;
import org.graphstream.ui.graphicGraph.GraphicElement;
import org.graphstream.ui.graphicGraph.GraphicGraph;
import org.graphstream.ui.spriteManager.SpriteManager;
import org.graphstream.ui.swing.util.SwingFileSinkImages;
import org.graphstream.ui.swing_viewer.SwingViewer;
import org.graphstream.ui.swing_viewer.ViewPanel;
import org.graphstream.ui.swing_viewer.util.MouseOverMouseManager;
import org.graphstream.ui.view.View;
import org.graphstream.ui.view.Viewer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import com.google.common.base.Strings;
import hu.aestallon.storageexplorer.domain.graph.service.GraphRenderingService;
import hu.aestallon.storageexplorer.domain.storage.model.StorageEntry;
import hu.aestallon.storageexplorer.domain.storage.service.StorageIndex;
import hu.aestallon.storageexplorer.domain.storage.service.StorageIndexProvider;
import hu.aestallon.storageexplorer.domain.userconfig.event.GraphConfigChanged;
import hu.aestallon.storageexplorer.domain.userconfig.service.UserConfigService;
import hu.aestallon.storageexplorer.ui.controller.ViewController;
import hu.aestallon.storageexplorer.ui.misc.GraphStylingProvider;
import hu.aestallon.storageexplorer.ui.misc.IconProvider;

@Component
public class GraphView extends JPanel {

  private static final Logger log = LoggerFactory.getLogger(GraphView.class);
  public static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm");

  private Graph graph;
  private Viewer viewer;
  private ViewPanel panel;
  private SpriteManager sprites;
  private KeyListener screenshotListener;
  private StorageEntry origin;
  private StorageEntry currentHighlight;
  private GraphRenderingService graphRenderingService;

  private final StorageIndexProvider storageIndexProvider;
  private final ApplicationEventPublisher eventPublisher;
  private final UserConfigService userConfigService;

  public GraphView(StorageIndexProvider storageIndexProvider,
                   ApplicationEventPublisher eventPublisher, UserConfigService userConfigService) {
    this.storageIndexProvider = storageIndexProvider;
    this.eventPublisher = eventPublisher;
    this.userConfigService = userConfigService;

    setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
    setMinimumSize(new Dimension(500, 500));

    initToolbar();
  }

  private void initToolbar() {
    final var toolbar = new JToolBar();
    toolbar.add(Box.createHorizontalGlue());
    toolbar.add(new AbstractAction(null, IconProvider.CLOSE) {
      @Override
      public void actionPerformed(ActionEvent e) {
        eventPublisher.publishEvent(new ViewController.GraphViewCloseRequest());
      }
    });
    toolbar.setPreferredSize(new Dimension(500, 40));
    add(toolbar);
  }

  public void init(StorageEntry storageEntry) {
    if (screenshotListener != null) {
      panel.removeKeyListener(screenshotListener);
    }
    if (panel != null) {
      remove(panel);
    }
    if (graph != null) {
      graph.clear();
    }

    final StorageIndex storageIndex = storageIndexProvider.indexOf(storageEntry);
    if (graphRenderingService == null || !storageIndex.equals(
        graphRenderingService.storageIndex())) {
      final var userConfig = userConfigService.graphSettings();
      graphRenderingService = new GraphRenderingService(
          storageIndex,
          userConfig.getGraphTraversalInboundLimit(),
          userConfig.getGraphTraversalOutboundLimit());
    }
    origin = storageEntry;

    graph = new MultiGraph("fs");
    sprites = new SpriteManager(graph);

    graph.setAttribute("ui.stylesheet", GraphStylingProvider.LIGHT);
    graph.setAttribute("ui.antialias");
    graph.setAttribute("ui.quality");
    graphRenderingService.render(graph, storageEntry);

    viewer = new SwingViewer(graph, Viewer.ThreadingModel.GRAPH_IN_GUI_THREAD);

    viewer.enableAutoLayout();

    panel = (ViewPanel) viewer.addDefaultView(false);
    panel.enableMouseOptions();
    panel.setMouseManager(new GraphViewMouseManager());

    screenshotListener = new ScreenshotListener();
    panel.addKeyListener(screenshotListener);
    add(panel);
    setVisible(true);
    revalidate();
  }

  public void discard() {
    setVisible(false);
    if (panel != null) {
      remove(panel);
      panel = null;
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
    // TODO: Maybe not needed?
    graphRenderingService = null;
  }

  public void select(final StorageEntry storageEntry) {
    if (graph == null || panel == null || Objects.equals(currentHighlight, storageEntry)) {
      return;
    }

    graphRenderingService.changeHighlight(graph, currentHighlight, storageEntry);
    currentHighlight = storageEntry;
  }

  private void showNodePopup(final StorageEntry entry, final int x, final int y) {
    final var popup = new NodePopupMenu(entry, graph, graphRenderingService);
    popup.show(GraphView.this, x, y);
  }

  private final class GraphViewMouseManager extends MouseOverMouseManager {

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

    @Override
    public void mouseClicked(MouseEvent e) {
      super.mouseClicked(e);

      if (e.getButton() == MouseEvent.BUTTON1) {
        getStorageEntry(e)
            .map(ViewController.EntryInspectionEvent::new)
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

    private Optional<StorageEntry> getStorageEntry(MouseEvent e) {
      final GraphicElement node = view.findGraphicElementAt(getManagedTypes(), e.getX(), e.getY());
      if (node == null) {
        return Optional.empty();
      }

      final String id = node.getId();
      if (Strings.isNullOrEmpty(id)) {
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


  @EventListener
  public void onGraphConfigurationChanged(GraphConfigChanged e) {
    SwingUtilities.invokeLater(() -> {
      if (graphRenderingService == null) {
        return;
      }

      graphRenderingService.setLimits(e.newInboundLimit(), e.newOutboundLimit());
      if (origin == null) {
        return;
      }

      init(origin);
    });
  }
}

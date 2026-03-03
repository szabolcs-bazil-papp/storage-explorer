/*
 * Copyright (C) 2025 Szabolcs Bazil Papp
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

package com.aestallon.storageexplorer.swing.ui.graph.uml;

import java.awt.*;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import javax.swing.*;
import org.springframework.context.ApplicationEventPublisher;
import com.aestallon.storageexplorer.client.graph.service.UmlRenderingService;
import com.aestallon.storageexplorer.common.event.msg.Msg;
import com.aestallon.storageexplorer.core.model.type.EntityType;
import com.aestallon.storageexplorer.core.model.type.Property;
import com.aestallon.storageexplorer.core.model.type.PropertyType;
import com.aestallon.storageexplorer.core.model.type.StructuredType;
import static com.aestallon.storageexplorer.swing.ui.graph.uml.StructuredTypeRenderer.HEADER_HEIGHT;
import static com.aestallon.storageexplorer.swing.ui.graph.uml.StructuredTypeRenderer.ROW_HEIGHT;
import prefuse.Display;
import prefuse.Visualization;
import prefuse.action.ActionList;
import prefuse.action.RepaintAction;
import prefuse.action.assignment.ColorAction;
import prefuse.activity.Activity;
import prefuse.controls.ControlAdapter;
import prefuse.controls.ToolTipControl;
import prefuse.data.Graph;
import prefuse.data.Node;
import prefuse.util.ColorLib;
import prefuse.visual.EdgeItem;
import prefuse.visual.VisualItem;

public final class UmlView {
  private static final String GRAPH = "graph";
  private static final String NODES = "graph.nodes";
  private static final String EDGES = "graph.edges";

  private static final int COLOR_NODE_STRK_LIGHT = ColorLib.gray(50);
  private static final int COLOR_NODE_FILL_LIGHT = ColorLib.gray(240);
  private static final int COLOR_EDGE_STRK_LIGHT = ColorLib.gray(100);
  private static final int COLOR_EDGE_FILL_LIGHT = ColorLib.gray(100);


  private static final int COLOR_NODE_STRK_DARK = ColorLib.gray(200);
  private static final int COLOR_NODE_FILL_DARK = ColorLib.gray(40);
  private static final int COLOR_EDGE_STRK_DARK = ColorLib.rgb(223, 195, 88);
  private static final int COLOR_EDGE_FILL_DARK = COLOR_EDGE_STRK_DARK;


  private record Palette(int nodeStrk, int nodeFill, int edgeStrk, int edgeFill) {
    private static final Palette LIGHT = new Palette(
        COLOR_NODE_STRK_LIGHT,
        COLOR_NODE_FILL_LIGHT,
        COLOR_EDGE_STRK_LIGHT,
        COLOR_EDGE_FILL_LIGHT);
    private static final Palette DARK = new Palette(
        COLOR_NODE_STRK_DARK,
        COLOR_NODE_FILL_DARK,
        COLOR_EDGE_STRK_DARK,
        COLOR_EDGE_FILL_DARK);
  }



  final Visualization vis;
  final Display display;
  final Map<Node, Set<String>> expandedDetails = new HashMap<>();
  final Map<Node, Map<String, Integer>> propertyPositions = new HashMap<>();
  private final AssociationRenderer edgeRenderer;
  private final StructuredTypeRenderer nodeRenderer;

  private final UmlRenderingService service;
  private final ApplicationEventPublisher eventPublisher;

  private final ColorAction nodeStroke;
  private final ColorAction nodeFill;
  private final ColorAction edgeColor;
  private final ColorAction edgeArrow;

  volatile boolean dark;

  public UmlView(UmlRenderingService service,
                 ApplicationEventPublisher eventPublisher,
                 boolean dark) {
    this.service = service;
    this.eventPublisher = eventPublisher;
    this.dark = dark;


    // Create graph
    Graph graph = service.graph();

    // Setup visualization
    vis = new Visualization();
    // Register graph
    vis.addGraph(GRAPH, graph);
    vis.setInteractive(EDGES, null, false);



    this.edgeRenderer = new AssociationRenderer(this);
    this.nodeRenderer = new StructuredTypeRenderer(this);
    vis.setRendererFactory(visualItem -> visualItem instanceof EdgeItem
        ? edgeRenderer
        : nodeRenderer);

    final var palette = dark ? Palette.DARK : Palette.LIGHT;
    nodeStroke = new ColorAction(NODES, VisualItem.STROKECOLOR, palette.nodeStrk);
    nodeFill = new ColorAction(NODES, VisualItem.FILLCOLOR, palette.nodeFill);
    edgeColor = new ColorAction(EDGES, VisualItem.STROKECOLOR, palette.edgeStrk);
    edgeArrow = new ColorAction(EDGES, VisualItem.FILLCOLOR, palette.edgeFill);

    ActionList color = new ActionList();
    color.add(nodeStroke);
    color.add(nodeFill);
    color.add(edgeColor);
    color.add(edgeArrow);

    final var animate = new ActionList(Activity.INFINITY);
    animate.add(new RepaintAction());
    vis.putAction("color", color);
    vis.putAction("layout", animate);
    // vis.runAfter("draw", "color");
    // vis.runAfter("color", "layout");

    // Setup display
    display = new Display(vis);
    display.setBackground(dark ? new Color(29, 32, 33) : Color.WHITE);
    // display.setDamageRedraw(false);
    display.setSize(1200, 800);
    display.pan(600, 400);
    display.zoom(new Point2D.Double(600, 400), 1.3);
    display.setHighQuality(true);


    // Initial positioning: spread nodes in a circle
    int i = 0;
    double radius = 300;
    int nodeCount = service.nodesByTypeName().size();
    for (Iterator<?> it = vis.items(NODES); it.hasNext(); ) {
      var next = it.next();
      if (!(next instanceof VisualItem n)) {
        continue;
      }
      double angle = nodeCount > 0 ? 2 * Math.PI * i / nodeCount : 0;
      n.setStartX(radius * Math.cos(angle));
      n.setStartY(radius * Math.sin(angle));
      n.setX(radius * Math.cos(angle));
      n.setY(radius * Math.sin(angle));
      i++;
    }

    // Add interaction
    // display.addControlListener(new FocusControl(1));
    display.addControlListener(new EntityClickControl());
    display.addControlListener(new PanControl());
    display.addControlListener(new prefuse.controls.DragControl());
    display.addControlListener(new prefuse.controls.WheelZoomControl() {
      {
        setMinScale(0.05d);
      }

      @Override
      public void mouseWheelMoved(MouseWheelEvent e) {
        Display display = (Display) e.getComponent();
        Point p = e.getPoint();
        zoom(display, p,
            1 - 0.1f * e.getWheelRotation(), false);
      }
    });
    display.addControlListener(new ToolTipControl(UmlRenderingService.COL_TOOLTIP));

    vis.run("draw");
    vis.run("color");
    // vis.run("layout");
    // Setup frame
    vis.run("layout");
    display.repaint();
  }

  public void applyTheme(boolean darkMode) {
    this.dark = darkMode;
    final var palette = darkMode ? Palette.DARK : Palette.LIGHT;

    nodeStroke.setDefaultColor(palette.nodeStrk);
    nodeFill.setDefaultColor(palette.nodeFill);
    edgeColor.setDefaultColor(palette.edgeStrk);
    edgeArrow.setDefaultColor(palette.edgeFill);

    display.setBackground(darkMode ? new Color(29, 32, 33) : Color.WHITE);
    fullRepaint();
  }

  public Display display() {
    return display;
  }

  public UmlRenderingService service() {
    return service;
  }

  private void positionNewNodes() {
    List<VisualItem> existingNodes = new ArrayList<>();
    List<VisualItem> newNodes = new ArrayList<>();

    for (Iterator<?> it = vis.items(NODES); it.hasNext(); ) {
      VisualItem n = (VisualItem) it.next();
      // Assume nodes with (0,0) or default layout positions are new if they haven't been placed yet.
      // Prefuse often defaults to (0,0) for new items if no layout is active.
      if (n.getX() == 0 && n.getY() == 0) {
        newNodes.add(n);
      } else {
        existingNodes.add(n);
      }
    }

    if (newNodes.isEmpty()) {
      return;
    }

    if (existingNodes.isEmpty()) {
      // If no existing nodes, place new ones in a circle around center
      double radius = 300;
      for (int i = 0; i < newNodes.size(); i++) {
        VisualItem n = newNodes.get(i);
        double angle = 2 * Math.PI * i / newNodes.size();
        double x = 600 + radius * Math.cos(angle);
        double y = 400 + radius * Math.sin(angle);
        n.setStartX(x);
        n.setStartY(y);
        n.setX(x);
        n.setY(y);
      }
      return;
    }

    // Calculate bounding box of existing nodes
    double minX = Double.MAX_VALUE;
    double minY = Double.MAX_VALUE;
    double maxX = -Double.MAX_VALUE;
    double maxY = -Double.MAX_VALUE;

    for (VisualItem n : existingNodes) {
      Rectangle2D b = n.getBounds();
      minX = Math.min(minX, b.getMinX());
      minY = Math.min(minY, b.getMinY());
      maxX = Math.max(maxX, b.getMaxX());
      maxY = Math.max(maxY, b.getMaxY());
    }

    double centerX = (minX + maxX) / 2.0;
    double centerY = (minY + maxY) / 2.0;
    double width = maxX - minX;
    double height = maxY - minY;
    double radius = Math.max(width, height) / 2.0 + 200; // Place outside the current bounding box

    for (int i = 0; i < newNodes.size(); i++) {
      VisualItem n = newNodes.get(i);
      // Try to find a free spot in a spiraling manner or just spread them out
      double angle = 2 * Math.PI * i / newNodes.size();
      double x = centerX + radius * Math.cos(angle);
      double y = centerY + radius * Math.sin(angle);

      // Basic overlap check with existing nodes and adjust if needed
      boolean overlap = true;
      int attempts = 0;
      while (overlap && attempts < 8) {
        overlap = false;
        Rectangle2D newBounds =
            new Rectangle2D.Double(x - 100, y - 50, 200, 100); // Approximate size
        for (VisualItem existing : existingNodes) {
          if (newBounds.intersects(existing.getBounds())) {
            overlap = true;
            radius += 50;
            x = centerX + radius * Math.cos(angle);
            y = centerY + radius * Math.sin(angle);
            break;
          }
        }
        attempts++;
      }

      n.setStartX(x);
      n.setStartY(y);
      n.setX(x);
      n.setY(y);
      // Add to existingNodes so subsequent newNodes don't overlap with this one
      existingNodes.add(n);
    }
  }


  void toggleDetail(Node node, String propertyPath) {
    expandedDetails.computeIfAbsent(node, k -> new HashSet<>());
    Set<String> expanded = expandedDetails.get(node);
    if (expanded.contains(propertyPath)) {
      expanded.remove(propertyPath);
    } else {
      expanded.add(propertyPath);
    }
  }

  boolean isDetailExpanded(Node node, String propertyPath) {
    Set<String> expanded = expandedDetails.get(node);
    return expanded != null && expanded.contains(propertyPath);
  }

  public void fullRepaint() {
    vis.run("draw");
    vis.run("color");
    vis.run("layout");
    vis.repaint();
    display.repaint();
  }

  // Click control for expanding/collapsing details
  class EntityClickControl extends ControlAdapter {

    public StructuredType getEntity(Node node) {
      return (StructuredType) node.get(UmlRenderingService.COL_NODE_TYPE);
    }



    @Override
    public void itemClicked(VisualItem item, java.awt.event.MouseEvent e) {
      if (item.isInGroup(NODES)) {
        Node node = (Node) item.getSourceTuple();
        StructuredType st = getEntity(node);
        if (!(st instanceof EntityType entity)) {
          StructuredType.Unknown unknownType = (StructuredType.Unknown) st;
          CompletableFuture
              .supplyAsync(() -> service.determineStructure(unknownType))
              .thenAccept(success -> SwingUtilities.invokeLater(() -> {
                if (!success) {
                  eventPublisher.publishEvent(Msg.warn(
                      "Could not determine structure of " + unknownType.name(),
                      "No type information is available for the requested type. Try indexing more entries belonging to this type!"));
                } else {
                  positionNewNodes();
                  fullRepaint();
                }
              }));
          return;
        }

        // Determine which property was clicked
        Point2D point = new Point2D.Double(e.getX(), e.getY());
        display.getAbsoluteCoordinate(point, point);

        Rectangle2D bounds = item.getBounds();
        double relativeY = point.getY() - bounds.getY() - HEADER_HEIGHT; // Account for header

        if (relativeY > 0) {
          int row = (int) (relativeY / ROW_HEIGHT);
          String clickedPath = findPropertyPath(entity.properties(), row, "", node);

          if (clickedPath != null) {
            Property pe = getPropertyAtPath(entity.properties(), clickedPath);
            if (pe != null && pe.type() instanceof PropertyType.Complex) {
              toggleDetail(node, clickedPath);
              fullRepaint();
            }
          }
        }
      }
    }

    private String findPropertyPath(List<Property> properties, int targetRow,
                                    String propPath, Node node) {
      int currentRow = 0;
      for (Property p : properties) {
        final var pKey = p.key();
        final var currentPath = propPath.isEmpty() ? pKey : propPath + "." + pKey;
        if (currentRow == targetRow) {
          return currentPath;
        }
        currentRow++;

        if (p.type() instanceof PropertyType.Complex c && isDetailExpanded(node, currentPath)) {
          String found = findPropertyPath(
              c.properties(),
              targetRow - currentRow,
              currentPath,
              node);
          if (found != null) {
            return found;
          }
          currentRow += countVisibleRows(c.properties(), currentPath, node);
        }

      }
      return null;
    }

    private int countVisibleRows(List<Property> properties, String propPath, Node node) {
      int count = 0;
      for (Property p : properties) {
        count++;
        final var currPath = propPath.isEmpty() ? p.key() : propPath + "." + p.key();
        if (p.type() instanceof PropertyType.Complex detail && isDetailExpanded(node, currPath)) {
          count += countVisibleRows(detail.properties(), currPath, node);
        }

      }
      return count;
    }

    private Property getPropertyAtPath(List<Property> properties, String propertyPath) {
      if (propertyPath.isEmpty())
        return null;

      int dotIndex = propertyPath.indexOf('.');
      final boolean needDescend;
      final String surfaceTarget;
      if (dotIndex != -1) {
        needDescend = true;
        surfaceTarget = propertyPath.substring(0, dotIndex);
      } else {
        needDescend = false;
        surfaceTarget = propertyPath;
      }
      Property current = properties.stream()
          .filter(it -> surfaceTarget.equals(it.key()))
          .findFirst()
          .orElse(null);
      if (!needDescend || current == null) {
        return current;
      }

      if (current.type() instanceof PropertyType.Complex detail) {
        return getPropertyAtPath(
            detail.properties(),
            propertyPath.substring(dotIndex + 1));
      }
      return null;
    }
  }

}

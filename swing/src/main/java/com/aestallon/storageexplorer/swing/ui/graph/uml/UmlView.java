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
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import javax.swing.*;
import org.springframework.context.ApplicationEventPublisher;
import com.aestallon.storageexplorer.client.graph.service.UmlRenderingService;
import com.aestallon.storageexplorer.client.userconfig.service.NominalTypeService;
import com.aestallon.storageexplorer.common.event.msg.Msg;
import com.aestallon.storageexplorer.common.util.Pair;
import com.aestallon.storageexplorer.core.model.type.EntityType;
import com.aestallon.storageexplorer.core.model.type.NominalType;
import com.aestallon.storageexplorer.core.model.type.Property;
import com.aestallon.storageexplorer.core.model.type.PropertyType;
import com.aestallon.storageexplorer.core.model.type.StructuredType;
import static com.aestallon.storageexplorer.swing.ui.graph.uml.StructuredTypeRenderer.HEADER_HEIGHT;
import static com.aestallon.storageexplorer.swing.ui.graph.uml.StructuredTypeRenderer.ROW_HEIGHT;
import com.aestallon.storageexplorer.swing.ui.misc.ColourService;
import prefuse.Display;
import prefuse.Visualization;
import prefuse.action.ActionList;
import prefuse.action.RepaintAction;
import prefuse.action.assignment.ColorAction;
import prefuse.activity.Activity;
import prefuse.controls.ControlAdapter;
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
  private final ColourService colourService;
  private final NominalTypeService nominalTypeService;
  private final ApplicationEventPublisher eventPublisher;

  private final ColorAction nodeStroke;
  private final ColorAction nodeFill;
  private final ColorAction edgeColor;
  private final ColorAction edgeArrow;

  volatile boolean dark;

  public UmlView(UmlRenderingService service, ColourService colourService,
                 NominalTypeService nominalTypeService,
                 ApplicationEventPublisher eventPublisher,
                 boolean dark) {
    this.service = service;
    this.colourService = colourService;
    this.nominalTypeService = nominalTypeService;
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
    double radius = 600;
    int nodeCount = service.nodesByTypeName().size();
    for (Iterator<?> it = vis.items(NODES); it.hasNext(); ) {
      var next = it.next();
      if (!(next instanceof VisualItem n)) {
        continue;
      }
      final var typename = ((StructuredType) n.get(UmlRenderingService.COL_NODE_TYPE)).name();
      final double x, y;
      if (typename.equals(service.originTypename())) {
        x = 0d;
        y = 0d;
      } else {
        double angle = nodeCount > 0 ? 2 * Math.PI * i / nodeCount : 0;
        x = radius * Math.cos(angle);
        y = radius * Math.sin(angle);
      }

      n.setStartX(x);
      n.setStartY(y);
      n.setX(x);
      n.setY(y);
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

  ColourService colours() {
    return colourService;
  }

  NominalTypeService types() {
    return nominalTypeService;
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
      expanded.removeIf(it -> it.startsWith(propertyPath));
      final Map<String, Integer> propPositionsByPath = propertyPositions.get(node);
      if (propPositionsByPath == null) {
        return;
      }

      final Integer propPosition = propPositionsByPath.get(propertyPath);
      final Set<String> subPaths = propPositionsByPath.keySet().stream()
          .filter(it -> it.startsWith(propertyPath) && !it.equals(propertyPath))
          .collect(Collectors.toSet());
      if (propPosition != null) {
        subPaths.forEach(it -> propPositionsByPath.put(it, propPosition));
      } else {
        subPaths.forEach(propPositionsByPath::remove);
      }


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

    private PropertyTooltip tooltipPanel = new PropertyTooltip();
    private Popup activePopup;
    private Window popupWindow;

    @Override
    public void itemEntered(VisualItem item, MouseEvent e) {
      updateTooltipContent(item, e);
      showTooltip(e);
    }

    @Override
    public void itemMoved(VisualItem item, MouseEvent e) {
      updateTooltipContent(item, e);  // update content based on new cursor position
      repositionTooltip(e);           // move the window, no flicker
    }

    @Override
    public void itemPressed(VisualItem item, MouseEvent e) {
      hideTooltip();
    }

    @Override
    public void itemReleased(VisualItem item, MouseEvent e) {
      updateTooltipContent(item, e);
      showTooltip(e);
    }

    @Override
    public void itemExited(VisualItem item, MouseEvent e) {
      hideTooltip();
    }

    // -------------------------------------------------------------------------

    private void updateTooltipContent(VisualItem item, MouseEvent e) {
      final var data = extractMetaAtCursor(item, e);
      tooltipPanel.update(data);
      tooltipPanel.setSize(tooltipPanel.getPreferredSize());
      if (popupWindow != null) {
        popupWindow.setSize(tooltipPanel.getPreferredSize());
      }
    }

    private void showTooltip(MouseEvent e) {
      Point p = tooltipPosition(e);
      activePopup = PopupFactory.getSharedInstance()
          .getPopup(e.getComponent(), tooltipPanel, p.x, p.y);
      activePopup.show();

      // Grab the window Swing created so we can reposition it cheaply
      popupWindow = SwingUtilities.windowForComponent(tooltipPanel);
    }

    private void repositionTooltip(MouseEvent e) {
      if (popupWindow != null) {
        Point p = tooltipPosition(e);
        popupWindow.setLocation(p.x, p.y);  // just moves the window, no repaint of canvas
      }
    }

    private void hideTooltip() {
      if (activePopup != null) {
        activePopup.hide();
        activePopup = null;
        popupWindow = null;
      }
    }

    private Point tooltipPosition(MouseEvent e) {
      Point screenPos = e.getLocationOnScreen();
      return new Point(screenPos.x + 16, screenPos.y + 8);
    }

    private TooltipData extractMetaAtCursor(VisualItem item, MouseEvent e) {
      final NodeProp locator = findPropertyAtCursor(item, e);
      if (locator == null) {
        return null;
      }

      final var structuredType = locator.t();

      final var p = locator.p();
      final var propPath = locator.propertyPath();
      if (propPath != null && !propPath.isEmpty()) {
        Optional<? extends NominalType> nominalTypeOfProp =
            nominalTypeService.get(structuredType.name(), propPath);
        Optional<Pair<NominalType.Obj, NominalType.ObjProperty>> nominalPropByHostType =
            nominalTypeService
                .get(structuredType.name())
                .map(it -> it instanceof NominalType.Obj obj ? obj : null)
                .flatMap(objType -> nominalTypeService.getProperty(objType, propPath));
        final String
            nominalPropertyKey,
            nominalHostTypeName,
            nominalPropertyDescription,
            structuralPropertyTypeName,
            nominalPropertyTypeName,
            nominalPropertyTypeDescription;
        final TooltipData.TypeMatchStatus typeMatchStatus;
        if (nominalPropByHostType.isPresent()) {
          final var hostType = nominalPropByHostType.get().a();
          final var nominalProp = nominalPropByHostType.get().b();
          final NominalType nominalPropType = nominalTypeOfProp
              .map(it -> (NominalType) it) // :(
              .orElseGet(nominalProp::type);
          final var nominalArity = nominalProp.arity();
          final String aPrefix = nominalArity == PropertyType.Arity.MANY ? "[" : "";
          final String aSuffix = nominalArity == PropertyType.Arity.MANY ? "]" : "";

          nominalPropertyKey = nominalProp.key();
          nominalHostTypeName = hostType.typeName();
          nominalPropertyDescription = nominalProp.description();
          structuralPropertyTypeName = p.type().toString();
          nominalPropertyTypeName = aPrefix +(nominalProp.required()
              ? nominalPropType.typeName()
              : nominalPropType.typeName() + " | null") + aSuffix;
          nominalPropertyTypeDescription = nominalPropType.description();
          typeMatchStatus = (p.type().satisfies(nominalTypeService.asPropertyType(hostType.typeName(), nominalProp)))
              ? TooltipData.TypeMatchStatus.MATCH
              : TooltipData.TypeMatchStatus.MISMATCH;
        } else {
          nominalPropertyKey = "";
          nominalHostTypeName = "";
          nominalPropertyDescription = "";
          structuralPropertyTypeName = p.type().toString();
          nominalPropertyTypeName = "";
          nominalPropertyTypeDescription = "";
          typeMatchStatus = TooltipData.TypeMatchStatus.UNAVAILABLE;
        }

        return new TooltipData(
            structuredType.name(),
            propPath,
            nominalPropertyKey,
            nominalHostTypeName,
            nominalPropertyDescription,
            typeMatchStatus,
            structuralPropertyTypeName,
            nominalPropertyTypeName,
            nominalPropertyTypeDescription);
      }
      return new TooltipData(
          structuredType.name(),
          nominalTypeService
              .get(structuredType.name())
              .map(it -> (NominalType) it)
              .map(NominalType::description).orElse(""));
    }

    record NodeProp(Node node, StructuredType t, Property p, String propertyPath) {}

    private NodeProp findPropertyAtCursor(VisualItem item, MouseEvent e) {
      if (!item.isInGroup(NODES)) {
        return null;
      }

      final Node node = (Node) item.getSourceTuple();
      final StructuredType st = getEntity(node);
      if (!(st instanceof EntityType entity)) {
        return new NodeProp(node, st, null, "");
      }

      // Determine which property was clicked
      final Point2D point = new Point2D.Double(e.getX(), e.getY());
      display.getAbsoluteCoordinate(point, point);

      Rectangle2D bounds = item.getBounds();
      double relativeY = point.getY() - bounds.getY() - HEADER_HEIGHT; // Account for header

      if (relativeY > 0) {
        int row = (int) (relativeY / ROW_HEIGHT);
        String clickedPath = findPropertyPath(entity.properties(), row, "", node);

        if (clickedPath != null) {
          Property pe = getPropertyAtPath(entity.properties(), clickedPath);
          return new NodeProp(node, st, pe, clickedPath);
        }
      }

      return new NodeProp(node, st, null, "");
    }

    @Override
    public void itemClicked(VisualItem item, java.awt.event.MouseEvent e) {
      final var locator = findPropertyAtCursor(item, e);
      if (locator == null) {
        return;
      }

      final var type = locator.t();
      if (!(type instanceof EntityType entity)) {
        StructuredType.Unknown unknownType = (StructuredType.Unknown) type;
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

      final var pe = locator.p();
      final var clickedPath = locator.propertyPath();
      if (pe != null && (pe.type() instanceof PropertyType.Complex || (
          pe.type() instanceof PropertyType.Union u && u.hasComplex()))) {
        toggleDetail(locator.node(), clickedPath);
        fullRepaint();
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
        } else if (p.type() instanceof PropertyType.Union u && u.hasComplex() && isDetailExpanded(
            node, currentPath)) {
          for (PropertyType type : u.types()) {
            if (type instanceof PropertyType.Complex c) {
              String found = findPropertyPath(
                  c.properties(),
                  targetRow - currentRow,
                  currentPath,
                  node);
              if (found != null) {
                return found;
              }
              currentRow += countVisibleRows(c.properties(), currentPath, node) + 1;
            }
          }
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
        } else if (p.type() instanceof PropertyType.Union u && u.hasComplex() && isDetailExpanded(
            node, currPath)) {
          for (PropertyType type : u.types()) {
            if (type instanceof PropertyType.Complex detail) {
              count += countVisibleRows(detail.properties(), currPath, node) + 1;
            }
          }
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
      } else if (current.type() instanceof PropertyType.Union u && u.hasComplex()) {
        for (final var complex : u.complexes()) {
          final var p = getPropertyAtPath(
              complex.properties(),
              propertyPath.substring(dotIndex + 1));
          if (p != null) {
            return p;
          }
        }
      }
      return null;
    }
  }

}

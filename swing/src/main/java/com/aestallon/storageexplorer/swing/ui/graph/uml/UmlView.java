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
import com.aestallon.storageexplorer.client.graph.service.UmlRenderingService;
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
import prefuse.data.Edge;
import prefuse.data.Graph;
import prefuse.data.Node;
import prefuse.util.ColorLib;
import prefuse.visual.EdgeItem;
import prefuse.visual.VisualItem;

public final class UmlView extends JFrame {
  private static final String GRAPH = "graph";
  private static final String NODES = "graph.nodes";
  private static final String EDGES = "graph.edges";

  final Visualization vis;
  final Display display;
  private final AssociationRenderer edgeRenderer;
  private final StructuredTypeRenderer nodeRenderer;
  Map<String, EntityType> entityMap;
  final Map<Node, Set<List<Integer>>> expandedDetails = new HashMap<>();
  final Map<Node, Map<String, Integer>> propertyPositions = new HashMap<>();

  private final UmlRenderingService service;


  public UmlView(UmlRenderingService service) {
    super("Entity Relationship Diagram");
    this.service = service;

    this.entityMap = new HashMap<>();

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

    ColorAction nodeStroke = new ColorAction(NODES, VisualItem.STROKECOLOR, ColorLib.gray(50));
    ColorAction nodeFill = new ColorAction(NODES, VisualItem.FILLCOLOR, ColorLib.gray(240));
    ColorAction edgeColor = new ColorAction(EDGES, VisualItem.STROKECOLOR, ColorLib.gray(100));
    ColorAction edgeArrow = new ColorAction(EDGES, VisualItem.FILLCOLOR, ColorLib.gray(100));

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
      n.setStartX(600 + radius * Math.cos(angle));
      n.setStartY(400 + radius * Math.sin(angle));
      n.setX(600 + radius * Math.cos(angle));
      n.setY(400 + radius * Math.sin(angle));
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
    //    display.addControlListener(new ZoomToFitControl(
    //        Visualization.ALL_ITEMS,
    //        50, 800,
    //        Control.RIGHT_MOUSE_BUTTON));
    display.addControlListener(new ToolTipControl(UmlRenderingService.COL_TOOLTIP));

    vis.run("draw");
    vis.run("color");
    // vis.run("layout");
    // Setup frame
    setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
    add(display);
    pack();
    setLocationRelativeTo(null);

    //    final var layout = new ForceDirectedLayout(GRAPH);
    //    layout.getForceSimulator().setIntegrator(new RungeKuttaIntegrator());
    //    for (prefuse.util.force.Force f : layout.getForceSimulator().getForces()) {
    //      if (f instanceof NBodyForce nbf) {
    //        nbf.setParameter(0, -400f);
    //        nbf.setParameter(1, 500f);
    //      }
    //    }
    //
    //    layout.setVisualization(vis);
    //    animate.add(layout);

    vis.run("layout");
    display.repaint();
  }

  private Graph createGraph(List<EntityType> entities) {
    Graph graph = new Graph(true); // directed
    graph.addColumn("entity", EntityType.class);
    graph.getEdgeTable().addColumn("arity", String.class);
    graph.getEdgeTable().addColumn("propertyPath", List.class);

    // Create nodes with initial positions
    Map<String, Node> nodeMap = new HashMap<>();
    java.util.Random rand = new java.util.Random(42); // Fixed seed for reproducibility
    for (final var entity : entities) {
      Node node = graph.addNode();
      node.set("entity", entity);
      nodeMap.put(entity.name(), node);
    }

    // Create edges for references
    for (final var entity : entities) {
      Node sourceNode = nodeMap.get(entity.name());
      addReferencesRecursive(graph, sourceNode, nodeMap, entity.properties(), new ArrayList<>());
    }

    return graph;
  }

  private void addReferencesRecursive(Graph graph, Node sourceNode, Map<String, Node> nodeMap,
                                      List<Property> properties, List<Integer> path) {
    for (int i = 0; i < properties.size(); i++) {
      Property pe = properties.get(i);
      List<Integer> currentPath = new ArrayList<>(path);
      currentPath.add(i);

      if (pe.type() instanceof PropertyType.Ref ref) {
        Node targetNode = nodeMap.get(ref.entityName());
        if (targetNode != null) {
          Edge edge = graph.addEdge(sourceNode, targetNode);
          edge.set("arity", pe.type().arity().name());
          edge.set("propertyPath", currentPath);
        }
      } else if (pe.type() instanceof PropertyType.Complex detail) {
        addReferencesRecursive(graph, sourceNode, nodeMap, detail.properties(), currentPath);
      }
    }
  }

  public void toggleDetail(Node node, List<Integer> path) {
    expandedDetails.computeIfAbsent(node, k -> new HashSet<>());
    Set<List<Integer>> expanded = expandedDetails.get(node);
    if (expanded.contains(path)) {
      expanded.remove(path);
    } else {
      expanded.add(path);
    }
    vis.repaint();
  }

  public boolean isDetailExpanded(Node node, List<Integer> path) {
    Set<List<Integer>> expanded = expandedDetails.get(node);
    return expanded != null && expanded.contains(path);
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
              .runAsync(() -> service.determineStructure(unknownType))
              .thenRun(() -> SwingUtilities.invokeLater(display::repaint));
          return;
        }

        // Determine which property was clicked
        Point2D point = new Point2D.Double(e.getX(), e.getY());
        display.getAbsoluteCoordinate(point, point);

        Rectangle2D bounds = item.getBounds();
        double relativeY = point.getY() - bounds.getY() - HEADER_HEIGHT; // Account for header

        if (relativeY > 0) {
          int row = (int) (relativeY / ROW_HEIGHT);
          List<Integer> clickedPath =
              findPropertyPath(entity.properties(), row, new ArrayList<>(), node);

          if (clickedPath != null) {
            Property pe = getPropertyAtPath(entity.properties(), clickedPath);
            if (pe != null && pe.type() instanceof PropertyType.Complex) {
              toggleDetail(node, clickedPath);
            }
          }
        }
      }
    }

    private List<Integer> findPropertyPath(List<Property> properties, int targetRow,
                                           List<Integer> currentPath, Node node) {
      int currentRow = 0;
      for (int i = 0; i < properties.size(); i++) {
        if (currentRow == targetRow) {
          List<Integer> result = new ArrayList<>(currentPath);
          result.add(i);
          return result;
        }
        currentRow++;

        Property pe = properties.get(i);
        if (pe.type() instanceof PropertyType.Complex detail) {
          List<Integer> newPath = new ArrayList<>(currentPath);
          newPath.add(i);
          if (isDetailExpanded(node, newPath)) {
            List<Integer> found =
                findPropertyPath(detail.properties(), targetRow - currentRow, newPath, node);
            if (found != null) {
              return found;
            }
            currentRow += countVisibleRows(detail.properties(), newPath, node);
          }
        }
      }
      return null;
    }

    private int countVisibleRows(List<Property> properties, List<Integer> path, Node node) {
      int count = 0;
      for (int i = 0; i < properties.size(); i++) {
        count++;
        Property pe = properties.get(i);
        if (pe.type() instanceof PropertyType.Complex detail) {
          List<Integer> newPath = new ArrayList<>(path);
          newPath.add(i);
          if (isDetailExpanded(node, newPath)) {
            count += countVisibleRows(detail.properties(), newPath, node);
          }
        }
      }
      return count;
    }

    private Property getPropertyAtPath(List<Property> properties, List<Integer> path) {
      if (path.isEmpty())
        return null;

      Property current = properties.get(path.get(0));
      if (path.size() == 1) {
        return current;
      }

      if (current.type() instanceof PropertyType.Complex detail) {
        return getPropertyAtPath(detail.properties(), path.subList(1, path.size()));
      }
      return null;
    }
  }

}

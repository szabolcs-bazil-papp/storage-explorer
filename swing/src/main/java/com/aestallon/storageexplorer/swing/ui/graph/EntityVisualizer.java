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

package com.aestallon.storageexplorer.swing.ui.graph;

import java.awt.*;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.*;
import prefuse.Display;
import prefuse.Visualization;
import prefuse.action.ActionList;
import prefuse.action.RepaintAction;
import prefuse.action.assignment.ColorAction;
import prefuse.action.layout.graph.ForceDirectedLayout;
import prefuse.controls.ControlAdapter;
import prefuse.data.Edge;
import prefuse.data.Graph;
import prefuse.data.Node;
import prefuse.render.AbstractShapeRenderer;
import prefuse.render.DefaultRendererFactory;
import prefuse.render.EdgeRenderer;
import prefuse.util.ColorLib;
import prefuse.visual.VisualItem;

public class EntityVisualizer extends JFrame {
  private static final String GRAPH = "graph";
  private static final String NODES = "graph.nodes";
  private static final String EDGES = "graph.edges";

  private Visualization vis;
  private Display display;
  private Map<String, Entity> entityMap;
  private Map<Node, Set<List<Integer>>> expandedDetails; // Node -> Set of property paths
  private Map<Node, Map<String, Integer>> propertyPositions; // Node -> (path -> y-position)

  private static final int ROW_HEIGHT = 20;
  private static final int HEADER_HEIGHT = 25;

  public EntityVisualizer(List<Entity> entities) {
    super("Entity Relationship Diagram");

    this.entityMap = new HashMap<>();
    for (Entity entity : entities) {
      entityMap.put(entity.uniqueName(), entity);
    }
    this.expandedDetails = new HashMap<>();
    this.propertyPositions = new HashMap<>();

    // Create graph
    Graph graph = createGraph(entities);

    // Setup visualization
    vis = new Visualization();
    vis.add(GRAPH, graph);

    // Setup renderers
    DefaultRendererFactory rf = new DefaultRendererFactory();
    rf.setDefaultRenderer(new EntityNodeRenderer(this));
    rf.setDefaultEdgeRenderer(new ReferenceEdgeRenderer());
    vis.setRendererFactory(rf);

    // Setup display
    display = new Display(vis);
    display.setSize(1200, 800);
    display.setHighQuality(true);

    // Setup actions
    ColorAction nodeStroke = new ColorAction(NODES, VisualItem.STROKECOLOR, ColorLib.gray(50));
    ColorAction nodeFill = new ColorAction(NODES, VisualItem.FILLCOLOR, ColorLib.gray(240));
    ColorAction edgeColor = new ColorAction(EDGES, VisualItem.STROKECOLOR, ColorLib.gray(100));
    ColorAction edgeArrow = new ColorAction(EDGES, VisualItem.FILLCOLOR, ColorLib.gray(100));

    ActionList color = new ActionList();
    color.add(nodeStroke);
    color.add(nodeFill);
    color.add(edgeColor);
    color.add(edgeArrow);

    // Layout
    ActionList layout = new ActionList(ActionList.INFINITY);
    ForceDirectedLayout fdl = new ForceDirectedLayout(GRAPH);
    layout.add(fdl);
    layout.add(new RepaintAction());

    vis.putAction("color", color);
    vis.putAction("layout", layout);

    // Run color once
    vis.run("color");
    
    // Initial positioning: spread nodes in a circle
    int i = 0;
    double radius = 300;
    for (Iterator<?> it = vis.items(NODES); it.hasNext(); ) {
      var next = it.next();
      if (!(next instanceof VisualItem n)) {
        System.out.println("n class: " + next.getClass());
        continue;
      }
      double angle = 2 * Math.PI * i / entities.size();
      n.setStartX(600 + radius * Math.cos(angle));
      n.setStartY(400 + radius * Math.sin(angle));
      n.setX(600 + radius * Math.cos(angle));
      n.setY(400 + radius * Math.sin(angle));
      i++;
    }

    // Run layout
    vis.run("layout");

    // Add interaction
    display.addControlListener(new EntityClickControl());
    display.addControlListener(new prefuse.controls.DragControl());
    display.addControlListener(new prefuse.controls.PanControl());
    display.addControlListener(new prefuse.controls.ZoomControl());
    display.addControlListener(new prefuse.controls.WheelZoomControl());

    // Setup frame
    setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    add(display);
    pack();

    // Run visualization
    vis.run("color");
    vis.run("layout");
  }

  private Graph createGraph(List<Entity> entities) {
    Graph graph = new Graph(true); // directed
    graph.addColumn("entity", Entity.class);
    graph.getEdgeTable().addColumn("arity", String.class);
    graph.getEdgeTable().addColumn("propertyPath", List.class);

    // Create nodes with initial positions
    Map<String, Node> nodeMap = new HashMap<>();
    java.util.Random rand = new java.util.Random(42); // Fixed seed for reproducibility
    for (Entity entity : entities) {
      Node node = graph.addNode();
      node.set("entity", entity);
      nodeMap.put(entity.uniqueName(), node);
    }

    // Create edges for references
    for (Entity entity : entities) {
      Node sourceNode = nodeMap.get(entity.uniqueName());
      addReferencesRecursive(graph, sourceNode, nodeMap, entity.properties(), new ArrayList<>());
    }

    return graph;
  }

  private void addReferencesRecursive(Graph graph, Node sourceNode, Map<String, Node> nodeMap,
                                      List<PropertyEntry> properties, List<Integer> path) {
    for (int i = 0; i < properties.size(); i++) {
      PropertyEntry pe = properties.get(i);
      List<Integer> currentPath = new ArrayList<>(path);
      currentPath.add(i);

      if (pe.property().type() instanceof Reference ref) {
        Node targetNode = nodeMap.get(ref.entityUniqueName());
        if (targetNode != null) {
          Edge edge = graph.addEdge(sourceNode, targetNode);
          edge.set("arity", pe.arity().name());
          edge.set("propertyPath", currentPath);
        }
      } else if (pe.property().type() instanceof Detail detail) {
        addReferencesRecursive(graph, sourceNode, nodeMap, detail.properties(), currentPath);
      }
    }
  }

  public boolean isDetailExpanded(Node node, List<Integer> path) {
    Set<List<Integer>> expanded = expandedDetails.get(node);
    return expanded != null && expanded.contains(path);
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

  public Entity getEntity(Node node) {
    return (Entity) node.get("entity");
  }

  public void setPropertyPosition(Node node, List<Integer> path, int yPosition) {
    propertyPositions.computeIfAbsent(node, k -> new HashMap<>());
    propertyPositions.get(node).put(pathToString(path), yPosition);
  }

  public Integer getPropertyPosition(Node node, List<Integer> path) {
    Map<String, Integer> positions = propertyPositions.get(node);
    if (positions == null) return null;
    return positions.get(pathToString(path));
  }

  private String pathToString(List<Integer> path) {
    return path.toString().replaceAll("[\\[\\], ]", "_");
  }

  // Custom node renderer
  class EntityNodeRenderer extends AbstractShapeRenderer {
    private EntityVisualizer visualizer;
    private static final int PADDING = 10;
    private static final int INDENT = 15;
    private static final int MIN_WIDTH = 200;

    public EntityNodeRenderer(EntityVisualizer visualizer) {
      this.visualizer = visualizer;
    }

    @Override
    protected Shape getRawShape(VisualItem item) {
      Rectangle2D bounds = calculateBounds(item);
      // Prefuse uses (x, y) from the item as center by default for some layouts,
      // but we want to return a shape that is positioned correctly relative to the item's coordinates.
      double x = item.getX();
      double y = item.getY();
      return new Rectangle2D.Double(x + bounds.getX(), y + bounds.getY(), bounds.getWidth(), bounds.getHeight());
    }

    private Rectangle2D calculateBounds(VisualItem item) {
      Entity entity = (Entity) item.get("entity");
      
      FontMetrics fm = display.getFontMetrics(new Font("SansSerif", Font.PLAIN, 11));
      int maxWidth = Math.max(MIN_WIDTH, fm.stringWidth(entity.uniqueName()) + 2 * PADDING);
      maxWidth = Math.max(maxWidth, calculatePropertiesWidth(item, entity.properties(), new ArrayList<>(), 0) + 2 * PADDING);

      int height = HEADER_HEIGHT + calculatePropertiesHeight(item, entity.properties(), new ArrayList<>());

      return new Rectangle2D.Double(-maxWidth / 2.0, -height / 2.0, maxWidth, height);
    }

    private int calculatePropertiesWidth(VisualItem item, List<PropertyEntry> properties, List<Integer> path, int indentLevel) {
      FontMetrics fm = display.getFontMetrics(new Font("SansSerif", Font.PLAIN, 11));
      int maxWidth = 0;
      for (int i = 0; i < properties.size(); i++) {
        PropertyEntry pe = properties.get(i);
        String label = pe.property().key() + ": " + (pe.arity() == Arity.MANY ? "List<" : "") + formatType(pe.property().type()) + (pe.arity() == Arity.MANY ? ">" : "");
        maxWidth = Math.max(maxWidth, fm.stringWidth(label) + indentLevel * INDENT);

        if (pe.property().type() instanceof Detail detail) {
          List<Integer> currentPath = new ArrayList<>(path);
          currentPath.add(i);
          if (isDetailExpanded((Node) item.getSourceTuple(), currentPath)) {
            maxWidth = Math.max(maxWidth, calculatePropertiesWidth(item, detail.properties(), currentPath, indentLevel + 1));
          }
        }
      }
      return maxWidth;
    }

    private int calculatePropertiesHeight(VisualItem item, List<PropertyEntry> properties, List<Integer> path) {
      int height = 0;
      for (int i = 0; i < properties.size(); i++) {
        PropertyEntry pe = properties.get(i);
        height += ROW_HEIGHT;

        if (pe.property().type() instanceof Detail detail) {
          List<Integer> currentPath = new ArrayList<>(path);
          currentPath.add(i);
          if (isDetailExpanded((Node) item.getSourceTuple(), currentPath)) {
            height += calculatePropertiesHeight(item, detail.properties(), currentPath);
          }
        }
      }
      return height;
    }

    @Override
    public void render(Graphics2D g, VisualItem item) {
      Entity entity = (Entity) item.get("entity");
      Shape shape = getShape(item);
      Rectangle2D bounds = shape.getBounds2D();

      // Draw box
      g.setColor(ColorLib.getColor(item.getFillColor()));
      g.fill(bounds);
      g.setColor(ColorLib.getColor(item.getStrokeColor()));
      g.setStroke(new BasicStroke(2));
      g.draw(bounds);

      // Draw header
      g.setColor(new Color(100, 150, 200));
      g.fill(new Rectangle2D.Double(bounds.getX(), bounds.getY(), bounds.getWidth(), HEADER_HEIGHT));

      g.setColor(Color.WHITE);
      g.setFont(new Font("SansSerif", Font.BOLD, 12));
      g.drawString(entity.uniqueName(),
          (int) bounds.getX() + PADDING,
          (int) bounds.getY() + 17);

      // Draw properties
      g.setColor(Color.BLACK);
      g.setFont(new Font("SansSerif", Font.PLAIN, 11));
      int y = (int) bounds.getY() + HEADER_HEIGHT + 15;

      renderProperties(g, item, entity.properties(), new ArrayList<>(),
          (int) bounds.getX() + PADDING, y, 0, bounds);
    }

    private int renderProperties(Graphics2D g, VisualItem item, List<PropertyEntry> properties,
                                 List<Integer> path, int x, int y, int indentLevel, Rectangle2D bounds) {
      for (int i = 0; i < properties.size(); i++) {
        PropertyEntry pe = properties.get(i);
        List<Integer> currentPath = new ArrayList<>(path);
        currentPath.add(i);

        String arityStr = pe.arity() == Arity.MANY ? "List<" : "";
        String arityEnd = pe.arity() == Arity.MANY ? ">" : "";
        String typeStr = formatType(pe.property().type());

        int currentX = x + (indentLevel * INDENT);
        g.drawString(pe.property().key() + ": " + arityStr + typeStr + arityEnd, currentX, y);

        // Store absolute position for edge calculation
        double absoluteY = item.getY() + (y - (item.getY() + bounds.getY()));
        setPropertyPosition((Node) item.getSourceTuple(), currentPath, (int) absoluteY);

        y += ROW_HEIGHT;

        // If it's an expanded detail, render nested properties
        if (pe.property().type() instanceof Detail detail) {
          if (isDetailExpanded((Node) item.getSourceTuple(), currentPath)) {
            y = renderProperties(g, item, detail.properties(), currentPath, x, y, indentLevel + 1, bounds);
          }
        }
      }
      return y;
    }

    private String formatType(PropertyType type) {
      if (type instanceof Inline inline) {
        return inline.name().toLowerCase();
      } else if (type instanceof Reference ref) {
        return "→ " + ref.entityUniqueName();
      } else if (type instanceof Detail) {
        return "{ ... }";
      }
      return "unknown";
    }
  }

  // Custom edge renderer
  class ReferenceEdgeRenderer extends EdgeRenderer {
    private static final BasicStroke SINGLE_STROKE = new BasicStroke(2);
    private static final float[] DASH_PATTERN = {10, 5};
    private static final BasicStroke MULTI_DASHED_STROKE =
        new BasicStroke(3, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, DASH_PATTERN, 0);

    public ReferenceEdgeRenderer() {
      setArrowType(prefuse.Constants.EDGE_ARROW_FORWARD);
    }

    @Override
    protected Shape getRawShape(VisualItem item) {
      Edge edge = (Edge) item.getSourceTuple();
      VisualItem sourceItem = vis.getVisualItem(NODES, edge.getSourceNode());
      VisualItem targetItem = vis.getVisualItem(NODES, edge.getTargetNode());

      if (sourceItem == null || targetItem == null) {
        return super.getRawShape(item);
      }

      List<Integer> path = (List<Integer>) item.get("propertyPath");
      Integer yOffset = getPropertyPosition(edge.getSourceNode(), path);

      double startX, startY;
      Rectangle2D sourceBounds = sourceItem.getBounds();

      if (yOffset != null) {
        // Use the stored property position
        startY = yOffset - 5; // Approximate center of the row
        // Determine if target is to the left or right to pick side of node
        if (targetItem.getX() > sourceItem.getX()) {
          startX = sourceBounds.getMaxX();
        } else {
          startX = sourceBounds.getMinX();
        }
      } else {
        startX = sourceItem.getX();
        startY = sourceItem.getY();
      }

      double endX = targetItem.getX();
      double endY = targetItem.getY();

      // Find intersection with target node boundary
      Rectangle2D targetBounds = targetItem.getBounds();
      
      Point2D intersection = getIntersection(startX, startY, endX, endY, targetBounds);
      if (intersection != null) {
        endX = intersection.getX();
        endY = intersection.getY();
      }

      m_line.setLine(startX, startY, endX, endY);
      return m_line;
    }

    private Point2D getIntersection(double x1, double y1, double x2, double y2, Rectangle2D rect) {
      // Very simple intersection with rectangle
      double dx = x2 - x1;
      double dy = y2 - y1;

      if (dx == 0 && dy == 0) return null;

      double tMin = Double.MAX_VALUE;
      Point2D result = null;

      // Check each side of the rectangle
      double[][] sides = {
          {rect.getMinX(), rect.getMinY(), rect.getMaxX(), rect.getMinY()}, // Top
          {rect.getMinX(), rect.getMaxY(), rect.getMaxX(), rect.getMaxY()}, // Bottom
          {rect.getMinX(), rect.getMinY(), rect.getMinX(), rect.getMaxY()}, // Left
          {rect.getMaxX(), rect.getMinY(), rect.getMaxX(), rect.getMaxY()}  // Right
      };

      for (double[] side : sides) {
        Point2D p = intersectLines(x1, y1, x2, y2, side[0], side[1], side[2], side[3]);
        if (p != null) {
          double t;
          if (Math.abs(dx) > Math.abs(dy)) {
            t = (p.getX() - x1) / dx;
          } else {
            t = (p.getY() - y1) / dy;
          }
          if (t >= 0 && t <= 1 && t < tMin) {
            tMin = t;
            result = p;
          }
        }
      }
      return result;
    }

    private Point2D intersectLines(double x1, double y1, double x2, double y2,
                                   double x3, double y3, double x4, double y4) {
      double den = (y4 - y3) * (x2 - x1) - (x4 - x3) * (y2 - y1);
      if (den == 0) return null;
      double ua = ((x4 - x3) * (y1 - y3) - (y4 - y3) * (x1 - x3)) / den;
      return new Point2D.Double(x1 + ua * (x2 - x1), y1 + ua * (y2 - y1));
    }

    @Override
    protected BasicStroke getStroke(VisualItem item) {
      String arity = (String) item.get("arity");
      if ("MANY".equals(arity)) {
        return MULTI_DASHED_STROKE;
      }
      return SINGLE_STROKE;
    }
  }

  // Click control for expanding/collapsing details
  class EntityClickControl extends ControlAdapter {
    @Override
    public void itemClicked(VisualItem item, java.awt.event.MouseEvent e) {
      if (item.isInGroup(NODES)) {
        Node node = (Node) item.getSourceTuple();
        Entity entity = getEntity(node);

        // Determine which property was clicked
        Point2D point = new Point2D.Double(e.getX(), e.getY());
        display.getAbsoluteCoordinate(point, point);

        Rectangle2D bounds = item.getBounds();
        double relativeY = point.getY() - bounds.getY() - HEADER_HEIGHT; // Account for header

        if (relativeY > 0) {
          int row = (int) (relativeY / ROW_HEIGHT);
          List<Integer> clickedPath = findPropertyPath(entity.properties(), row, new ArrayList<>(), node);

          if (clickedPath != null) {
            PropertyEntry pe = getPropertyAtPath(entity.properties(), clickedPath);
            if (pe != null && pe.property().type() instanceof Detail) {
              toggleDetail(node, clickedPath);
            }
          }
        }
      }
    }

    private List<Integer> findPropertyPath(List<PropertyEntry> properties, int targetRow,
                                           List<Integer> currentPath, Node node) {
      int currentRow = 0;
      for (int i = 0; i < properties.size(); i++) {
        if (currentRow == targetRow) {
          List<Integer> result = new ArrayList<>(currentPath);
          result.add(i);
          return result;
        }
        currentRow++;

        PropertyEntry pe = properties.get(i);
        if (pe.property().type() instanceof Detail detail) {
          List<Integer> newPath = new ArrayList<>(currentPath);
          newPath.add(i);
          if (isDetailExpanded(node, newPath)) {
            List<Integer> found = findPropertyPath(detail.properties(), targetRow - currentRow, newPath, node);
            if (found != null) {
              return found;
            }
            currentRow += countVisibleRows(detail.properties(), newPath, node);
          }
        }
      }
      return null;
    }

    private int countVisibleRows(List<PropertyEntry> properties, List<Integer> path, Node node) {
      int count = 0;
      for (int i = 0; i < properties.size(); i++) {
        count++;
        PropertyEntry pe = properties.get(i);
        if (pe.property().type() instanceof Detail detail) {
          List<Integer> newPath = new ArrayList<>(path);
          newPath.add(i);
          if (isDetailExpanded(node, newPath)) {
            count += countVisibleRows(detail.properties(), newPath, node);
          }
        }
      }
      return count;
    }

    private PropertyEntry getPropertyAtPath(List<PropertyEntry> properties, List<Integer> path) {
      if (path.isEmpty()) return null;

      PropertyEntry current = properties.get(path.get(0));
      if (path.size() == 1) {
        return current;
      }

      if (current.property().type() instanceof Detail detail) {
        return getPropertyAtPath(detail.properties(), path.subList(1, path.size()));
      }
      return null;
    }
  }

  // Example usage
  public static void main(String[] args) {
    // Create sample entities
    Entity user = new Entity("User", List.of(
        new PropertyEntry(new Property("id", Inline.NUMBER), Arity.ONE),
        new PropertyEntry(new Property("name", Inline.STRING), Arity.ONE),
        new PropertyEntry(new Property("email", Inline.STRING), Arity.ONE),
        new PropertyEntry(new Property("address", new Detail(List.of(
            new PropertyEntry(new Property("street", Inline.STRING), Arity.ONE),
            new PropertyEntry(new Property("city", Inline.STRING), Arity.ONE),
            new PropertyEntry(new Property("country", new Reference("Country")), Arity.ONE)
        ))), Arity.ONE),
        new PropertyEntry(new Property("orders", new Reference("Order")), Arity.MANY)
    ));

    Entity order = new Entity("Order", List.of(
        new PropertyEntry(new Property("id", Inline.NUMBER), Arity.ONE),
        new PropertyEntry(new Property("date", Inline.STRING), Arity.ONE),
        new PropertyEntry(new Property("total", Inline.NUMBER), Arity.ONE),
        new PropertyEntry(new Property("user", new Reference("User")), Arity.ONE),
        new PropertyEntry(new Property("items", new Reference("Product")), Arity.MANY)
    ));

    Entity product = new Entity("Product", List.of(
        new PropertyEntry(new Property("id", Inline.NUMBER), Arity.ONE),
        new PropertyEntry(new Property("name", Inline.STRING), Arity.ONE),
        new PropertyEntry(new Property("price", Inline.NUMBER), Arity.ONE),
        new PropertyEntry(new Property("category", new Reference("Category")), Arity.ONE)
    ));

    Entity category = new Entity("Category", List.of(
        new PropertyEntry(new Property("id", Inline.NUMBER), Arity.ONE),
        new PropertyEntry(new Property("name", Inline.STRING), Arity.ONE)
    ));

    Entity country = new Entity("Country", List.of(
        new PropertyEntry(new Property("code", Inline.STRING), Arity.ONE),
        new PropertyEntry(new Property("name", Inline.STRING), Arity.ONE)
    ));

    List<Entity> entities = List.of(user, order, product, category, country);

    SwingUtilities.invokeLater(() -> {
      EntityVisualizer viz = new EntityVisualizer(entities);
      viz.setVisible(true);
    });
  }
}

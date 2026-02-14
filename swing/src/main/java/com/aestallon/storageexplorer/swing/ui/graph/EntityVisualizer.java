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
import prefuse.activity.Activity;
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

  public EntityVisualizer(List<Entity> entities) {
    super("Entity Relationship Diagram");

    this.entityMap = new HashMap<>();
    for (Entity entity : entities) {
      entityMap.put(entity.uniqueName(), entity);
    }
    this.expandedDetails = new HashMap<>();

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

    ActionList color = new ActionList();
    color.add(nodeStroke);
    color.add(nodeFill);
    color.add(edgeColor);

    ActionList layout = new ActionList(Activity.INFINITY);
    ForceDirectedLayout fdl = new ForceDirectedLayout(GRAPH);
    fdl.setForceSimulator(new prefuse.util.force.ForceSimulator());
    layout.add(fdl);
    layout.add(new RepaintAction());

    vis.putAction("color", color);
    vis.putAction("layout", layout);

    // Add interaction
    display.addControlListener(new EntityClickControl());
    display.addControlListener(new prefuse.controls.DragControl());
    display.addControlListener(new prefuse.controls.PanControl());
    display.addControlListener(new prefuse.controls.ZoomControl());

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
    graph.getEdgeTable().addColumn("arity", String.class); // For edges
    graph.getEdgeTable().addColumn("propertyPath", List.class);

    // Create nodes
    Map<String, Node> nodeMap = new HashMap<>();
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

  // Custom node renderer
  class EntityNodeRenderer extends AbstractShapeRenderer {
    private EntityVisualizer visualizer;
    private static final int PADDING = 10;
    private static final int ROW_HEIGHT = 20;
    private static final int HEADER_HEIGHT = 25;
    private static final int INDENT = 15;

    public EntityNodeRenderer(EntityVisualizer visualizer) {
      this.visualizer = visualizer;
    }

    @Override
    protected Shape getRawShape(VisualItem item) {
      Rectangle2D bounds = calculateBounds(item);
      return bounds;
    }

    private Rectangle2D calculateBounds(VisualItem item) {
      Entity entity = (Entity) item.get("entity");
      int maxWidth = 200;

      FontMetrics fm = display.getFontMetrics(new Font("SansSerif", Font.PLAIN, 11));
      maxWidth = Math.max(maxWidth, fm.stringWidth(entity.uniqueName()) + 2 * PADDING);

      int height = HEADER_HEIGHT + calculatePropertiesHeight(item, entity.properties(), new ArrayList<>(), fm);

      return new Rectangle2D.Double(-maxWidth/2.0, -height/2.0, maxWidth, height + PADDING);
    }

    private int calculatePropertiesHeight(VisualItem item, List<PropertyEntry> properties, List<Integer> path, FontMetrics fm) {
      int height = 0;
      for (int i = 0; i < properties.size(); i++) {
        PropertyEntry pe = properties.get(i);
        height += ROW_HEIGHT;

        if (pe.property().type() instanceof Detail detail) {
          List<Integer> currentPath = new ArrayList<>(path);
          currentPath.add(i);
          if (isDetailExpanded((Node) item.getSourceTuple(), currentPath)) {
            height += calculatePropertiesHeight(item, detail.properties(), currentPath, fm);
          }
        }
      }
      return height;
    }

    @Override
    public void render(Graphics2D g, VisualItem item) {
      Entity entity = (Entity) item.get("entity");
      Rectangle2D bounds = (Rectangle2D) getShape(item);

      // Draw box
      g.setColor(ColorLib.getColor(item.getFillColor()));
      g.fill(bounds);
      g.setColor(ColorLib.getColor(item.getStrokeColor()));
      g.setStroke(new BasicStroke(2));
      g.draw(bounds);

      // Draw header
      g.setColor(new Color(100, 150, 200));
      g.fillRect((int)bounds.getX(), (int)bounds.getY(), (int)bounds.getWidth(), HEADER_HEIGHT);

      g.setColor(Color.WHITE);
      g.setFont(new Font("SansSerif", Font.BOLD, 12));
      g.drawString(entity.uniqueName(),
          (int)bounds.getX() + PADDING,
          (int)bounds.getY() + 17);

      // Draw properties
      g.setColor(Color.BLACK);
      g.setFont(new Font("SansSerif", Font.PLAIN, 11));
      int y = (int)bounds.getY() + HEADER_HEIGHT + 15;

      renderProperties(g, item, entity.properties(), new ArrayList<>(),
          (int)bounds.getX() + PADDING, y, 0);
    }

    private int renderProperties(Graphics2D g, VisualItem item, List<PropertyEntry> properties,
                                 List<Integer> path, int x, int y, int indentLevel) {
      for (int i = 0; i < properties.size(); i++) {
        PropertyEntry pe = properties.get(i);
        List<Integer> currentPath = new ArrayList<>(path);
        currentPath.add(i);

        String arityStr = pe.arity() == Arity.MANY ? "List<" : "";
        String arityEnd = pe.arity() == Arity.MANY ? ">" : "";
        String typeStr = formatType(pe.property().type());

        int currentX = x + (indentLevel * INDENT);
        g.drawString(pe.property().key() + ": " + arityStr + typeStr + arityEnd, currentX, y);

        // Store position for edge calculation
        // !!! Commented out, because this crashes!
        // item.set("prop_" + pathToString(currentPath) + "_y", y);

        y += ROW_HEIGHT;

        // If it's an expanded detail, render nested properties
        if (pe.property().type() instanceof Detail detail) {
          if (isDetailExpanded((Node) item.getSourceTuple(), currentPath)) {
            y = renderProperties(g, item, detail.properties(), currentPath, x, y, indentLevel + 1);
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

    private String pathToString(List<Integer> path) {
      return path.toString().replaceAll("[\\[\\], ]", "_");
    }
  }

  // Custom edge renderer
  class ReferenceEdgeRenderer extends EdgeRenderer {
    private static final BasicStroke SINGLE_STROKE = new BasicStroke(2);
    private static final BasicStroke MULTI_STROKE = new BasicStroke(4);
    private static final float[] DASH_PATTERN = {10, 5};
    private static final BasicStroke MULTI_DASHED_STROKE =
        new BasicStroke(4, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, DASH_PATTERN, 0);

    public ReferenceEdgeRenderer() {
      setArrowType(prefuse.Constants.EDGE_ARROW_FORWARD);
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
        double relativeY = point.getY() - bounds.getY() - 25; // Account for header

        if (relativeY > 0) {
          int row = (int) (relativeY / 20);
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


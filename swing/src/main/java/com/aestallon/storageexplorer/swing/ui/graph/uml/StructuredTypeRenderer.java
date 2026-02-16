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
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import com.aestallon.storageexplorer.client.graph.service.UmlRenderingService;
import com.aestallon.storageexplorer.core.model.type.EntityType;
import com.aestallon.storageexplorer.core.model.type.Property;
import com.aestallon.storageexplorer.core.model.type.PropertyType;
import com.aestallon.storageexplorer.core.model.type.StructuredType;
import prefuse.data.Node;
import prefuse.render.AbstractShapeRenderer;
import prefuse.util.ColorLib;
import prefuse.visual.VisualItem;

public class StructuredTypeRenderer extends AbstractShapeRenderer {

  private static final int PADDING = 10;
  private static final int INDENT = 15;
  private static final int MIN_WIDTH = 200;
  static final int ROW_HEIGHT = 20;
  static final int HEADER_HEIGHT = 25;

  private final UmlView umlView;

  public StructuredTypeRenderer(UmlView umlView) {
    this.umlView = umlView;
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
    StructuredType st = (StructuredType) item.get(UmlRenderingService.COL_NODE_TYPE);

    FontMetrics fm = umlView.display.getFontMetrics(new Font("SansSerif", Font.PLAIN, 11));
    int maxWidth = Math.max(MIN_WIDTH, fm.stringWidth(st.name()) + 2 * PADDING);
    int height = HEADER_HEIGHT;
    if (st instanceof EntityType entity) {
      maxWidth = Math.max(
          maxWidth,
          calculatePropertiesWidth(item, entity.properties(), new ArrayList<>(), 0) + 2 * PADDING);
      height += calculatePropertiesHeight(item, entity.properties(), new ArrayList<>());
    }
    return new Rectangle2D.Double(-maxWidth / 2.0, -height / 2.0, maxWidth, height);
  }

  private int calculatePropertiesWidth(VisualItem item, java.util.List<Property> properties,
                                       java.util.List<Integer> path, int indentLevel) {
    FontMetrics fm = umlView.display.getFontMetrics(new Font("SansSerif", Font.PLAIN, 11));
    int maxWidth = 0;
    for (int i = 0; i < properties.size(); i++) {
      Property pe = properties.get(i);
      String label =
          pe.key() + ": " + (pe.arity() == PropertyType.Arity.MANY ? "List<" : "")
              + formatType(pe.type()) + (pe.arity() == PropertyType.Arity.MANY ? ">" : "");
      maxWidth = Math.max(maxWidth, fm.stringWidth(label) + indentLevel * INDENT);

      if (pe.type() instanceof PropertyType.Complex detail) {
        java.util.List<Integer> currentPath = new ArrayList<>(path);
        currentPath.add(i);
        if (umlView.isDetailExpanded((Node) item.getSourceTuple(), currentPath)) {
          maxWidth = Math.max(maxWidth,
              calculatePropertiesWidth(item, detail.properties(), currentPath, indentLevel + 1));
        }
      }
    }
    return maxWidth;
  }

  private int calculatePropertiesHeight(VisualItem item, java.util.List<Property> properties,
                                        java.util.List<Integer> path) {
    int height = 0;
    for (int i = 0; i < properties.size(); i++) {
      Property pe = properties.get(i);
      height += ROW_HEIGHT;

      if (pe.type() instanceof PropertyType.Complex detail) {
        java.util.List<Integer> currentPath = new ArrayList<>(path);
        currentPath.add(i);
        if (umlView.isDetailExpanded((Node) item.getSourceTuple(), currentPath)) {
          height += calculatePropertiesHeight(item, detail.properties(), currentPath);
        }
      }
    }
    return height;
  }

  @Override
  public void render(Graphics2D g, VisualItem item) {
    StructuredType st = (StructuredType) item.get(UmlRenderingService.COL_NODE_TYPE);
    Shape shape = getShape(item);
    if (shape == null) return;
    Rectangle2D bounds = shape.getBounds2D();

    // Draw box
    g.setColor(ColorLib.getColor(item.getFillColor()));
    g.fill(bounds);
    g.setColor(ColorLib.getColor(item.getStrokeColor()));
    g.setStroke(new BasicStroke(2));
    g.draw(bounds);

    // Draw header
    g.setColor(new Color(100, 150, 200));
    g.fill(
        new Rectangle2D.Double(bounds.getX(), bounds.getY(), bounds.getWidth(), HEADER_HEIGHT));

    g.setColor(Color.WHITE);
    g.setFont(new Font("SansSerif", Font.BOLD, 12));
    g.drawString(st.name(),
        (int) bounds.getX() + PADDING,
        (int) bounds.getY() + 17);

    // Draw properties
    g.setColor(Color.BLACK);
    g.setFont(new Font("SansSerif", Font.PLAIN, 11));
    int y = (int) bounds.getY() + HEADER_HEIGHT + 15;

    if (st instanceof EntityType entity) {
      renderProperties(g, item, entity.properties(), new ArrayList<>(),
          (int) bounds.getX() + PADDING, y, 0, bounds);
    }
  }

  private int renderProperties(Graphics2D g, VisualItem item, java.util.List<Property> properties,
                               java.util.List<Integer> path, int x, int y, int indentLevel,
                               Rectangle2D bounds) {
    for (int i = 0; i < properties.size(); i++) {
      Property pe = properties.get(i);
      List<Integer> currentPath = new ArrayList<>(path);
      currentPath.add(i);

      String arityStr = pe.arity() == PropertyType.Arity.MANY ? "List<" : "";
      String arityEnd = pe.arity() == PropertyType.Arity.MANY ? ">" : "";
      String typeStr = formatType(pe.type());

      int currentX = x + (indentLevel * INDENT);
      g.drawString(pe.key() + ": " + arityStr + typeStr + arityEnd, currentX, y);

      // Store absolute position for edge calculation
      double absoluteY = item.getY() + (y - (item.getY() + bounds.getY()));
      setPropertyPosition((Node) item.getSourceTuple(), currentPath, (int) absoluteY);

      y += ROW_HEIGHT;

      // If it's an expanded detail, render nested properties
      if (pe.type() instanceof PropertyType.Complex detail) {
        if (umlView.isDetailExpanded((Node) item.getSourceTuple(), currentPath)) {
          y = renderProperties(g, item, detail.properties(), currentPath, x, y, indentLevel + 1,
              bounds);
        }
      }
    }
    return y;
  }

  private String formatType(PropertyType type) {
    if (type instanceof PropertyType.Primitive inline) {
      return inline.type().name().toLowerCase();
    } else if (type instanceof PropertyType.Ref ref) {
      return "→ " + ref.entityName();
    } else if (type instanceof PropertyType.Complex) {
      return "{ ... }";
    } else if (type instanceof PropertyType.Union union) {
      return union.types().stream().map(this::formatType).collect(Collectors.joining(" | "));
    }
    return "unknown";
  }



  public StructuredType getEntity(Node node) {
    return (StructuredType) node.get(UmlRenderingService.COL_NODE_TYPE);
  }

  public void setPropertyPosition(Node node, List<Integer> path, int yPosition) {
    umlView.propertyPositions.computeIfAbsent(node, k -> new HashMap<>());
    umlView.propertyPositions.get(node).put(pathToString(path), yPosition);
  }

  public Integer getPropertyPosition(Node node, List<Integer> path) {
    Map<String, Integer> positions = umlView.propertyPositions.get(node);
    if (positions == null)
      return null;
    return positions.get(pathToString(path));
  }

  private String pathToString(List<Integer> path) {
    return path.toString().replaceAll("[\\[\\], ]", "_");
  }
}

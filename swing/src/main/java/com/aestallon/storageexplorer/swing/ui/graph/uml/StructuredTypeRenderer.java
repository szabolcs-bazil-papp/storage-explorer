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
import java.awt.geom.RoundRectangle2D;
import java.util.HashMap;
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

  private static final String FONT_NAME = "JetBrains Mono";
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
    return new Rectangle2D.Double(x + bounds.getX(), y + bounds.getY(), bounds.getWidth(),
        bounds.getHeight());
  }

  private Rectangle2D calculateBounds(VisualItem item) {
    StructuredType st = (StructuredType) item.get(UmlRenderingService.COL_NODE_TYPE);

    FontMetrics fm = umlView.display.getFontMetrics(new Font(FONT_NAME, Font.PLAIN, 11));
    int maxWidth = Math.max(MIN_WIDTH, fm.stringWidth(st.name()) + 2 * PADDING);
    int height = HEADER_HEIGHT;
    if (st instanceof EntityType entity) {
      maxWidth = Math.max(
          maxWidth,
          calculatePropertiesWidth(item, entity.properties(), "", 0) + 2 * PADDING);
      height += calculatePropertiesHeight(item, entity.properties(), "");
    }
    return new Rectangle2D.Double(-maxWidth / 2.0, -height / 2.0, maxWidth, height);
  }

  private int calculatePropertiesWidth(VisualItem item, java.util.List<Property> properties,
                                       String propertyPath, int indentLevel) {
    FontMetrics fm = umlView.display.getFontMetrics(new Font(FONT_NAME, Font.PLAIN, 11));
    int maxWidth = 0;
    for (Property pe : properties) {
      String label =
          pe.key() + ": " + (pe.arity() == PropertyType.Arity.MANY ? "List<" : "")
              + formatType(pe.type()) + (pe.arity() == PropertyType.Arity.MANY ? ">" : "");
      maxWidth = Math.max(maxWidth, fm.stringWidth(label) + indentLevel * INDENT);

      if (pe.type() instanceof PropertyType.Complex detail) {
        final var currentPath = propertyPath.isEmpty() ? pe.key() : propertyPath + "." + pe.key();
        if (umlView.isDetailExpanded((Node) item.getSourceTuple(), currentPath)) {
          maxWidth = Math.max(maxWidth,
              calculatePropertiesWidth(item, detail.properties(), currentPath, indentLevel + 1));
        }
      } else if (pe.type() instanceof PropertyType.Union u && u.hasComplex()) {
        final var currentPath = propertyPath.isEmpty() ? pe.key() : propertyPath + "." + pe.key();
        if (umlView.isDetailExpanded((Node) item.getSourceTuple(), currentPath)) {
          for (final var detail : u.complexes()) {
            maxWidth = Math.max(maxWidth,
                calculatePropertiesWidth(item, detail.properties(), currentPath, indentLevel + 1));
            maxWidth += INDENT;
          }
        }
      }
    }
    return maxWidth + 10;
  }

  private int calculatePropertiesHeight(VisualItem item, java.util.List<Property> properties,
                                        String path) {
    int height = 0;
    for (Property pe : properties) {
      height += ROW_HEIGHT;

      if (pe.type() instanceof PropertyType.Complex detail) {
        final var currentPath = path.isEmpty() ? pe.key() : path + "." + pe.key();
        if (umlView.isDetailExpanded((Node) item.getSourceTuple(), currentPath)) {
          height += calculatePropertiesHeight(item, detail.properties(), currentPath);
        }
      } else if (pe.type() instanceof PropertyType.Union u && u.hasComplex()) {
        final var currentPath = path.isEmpty() ? pe.key() : path + "." + pe.key();
        if (umlView.isDetailExpanded((Node) item.getSourceTuple(), currentPath)) {
          for (final var detail : u.complexes()) {

            height += calculatePropertiesHeight(item, detail.properties(), currentPath);
            height += ROW_HEIGHT;
          }
        }
      }
    }
    return height;
  }

  @Override
  public void render(Graphics2D g, VisualItem item) {
    final StructuredType st = (StructuredType) item.get(UmlRenderingService.COL_NODE_TYPE);
    final Shape shape = getShape(item);
    if (shape == null) {
      return;
    }

    Rectangle2D bounds = shape.getBounds2D();
    final var roundedBounds = new RoundRectangle2D.Double(
        bounds.getX(),
        bounds.getY(), bounds.getWidth(), bounds.getHeight(), 10, 10);

    // Draw box
    g.setColor(ColorLib.getColor(item.getFillColor()));
    g.fill(roundedBounds);


    // Draw header
    g.setColor(umlView.dark
        ? new Color(38, 103, 87)
        : new Color(66, 188, 165));
    g.fill(new RoundRectangle2D.Double(
        bounds.getX() + 1,
        bounds.getY(),
        bounds.getWidth() - 2,
        HEADER_HEIGHT,
        2,
        2));

    g.setColor(ColorLib.getColor(item.getStrokeColor()));
    g.setStroke(new BasicStroke(2));
    g.draw(roundedBounds);

    g.setColor(Color.WHITE);
    g.setFont(new Font(FONT_NAME, Font.BOLD, 12));
    g.drawString(st.name(),
        (int) roundedBounds.getX() + PADDING,
        (int) roundedBounds.getY() + 17);

    // Draw properties
    if (umlView.dark)
      g.setColor(new Color(223, 195, 88));
    else
      g.setColor(Color.BLACK);
    g.setFont(new Font(FONT_NAME, Font.PLAIN, 11));

    if (st instanceof EntityType entity) {
      int y = (int) bounds.getY() + HEADER_HEIGHT + 15;
      renderProperties(g, item, entity.properties(), "",
          (int) bounds.getX() + PADDING, y, 0, bounds);
    }
  }

  private int renderProperties(Graphics2D g, VisualItem item, java.util.List<Property> properties,
                               String path, int x, int y, int indentLevel,
                               Rectangle2D bounds) {
    for (int i = 0; i < properties.size(); i++) {
      Property pe = properties.get(i);
      String currPath = path.isEmpty() ? pe.key() : path + "." + pe.key();

      String typeStr = formatType(pe.type());

      int currentX = x + (indentLevel * INDENT);
      g.drawString(pe.key() + ": " + typeStr, currentX, y);

      // Store absolute position for edge calculation
      setPropertyPosition((Node) item.getSourceTuple(), currPath, (int) y);

      y += ROW_HEIGHT;

      // If it's an expanded detail, render nested properties
      if (pe.type() instanceof PropertyType.Complex detail) {
        if (umlView.isDetailExpanded((Node) item.getSourceTuple(), currPath)) {
          y = renderProperties(g, item, detail.properties(), currPath, x, y, indentLevel + 1,
              bounds);
        }
      } else if (pe.type() instanceof PropertyType.Union u && u.hasComplex()) {
        if (umlView.isDetailExpanded((Node) item.getSourceTuple(), currPath)) {
          for (final var detail : u.complexes()) {
            y = renderProperties(g, item, detail.properties(), currPath, x, y, indentLevel + 1,
                bounds);
            g.drawString("-------", x + ((indentLevel + 1) * INDENT), y);
            y += ROW_HEIGHT;
          }
        }
      }
    }
    return y;
  }

  private String formatType(PropertyType type) {
    final var typeSymbol = switch (type) {
      case PropertyType.Primitive inline -> inline.type().name().toLowerCase();
      case PropertyType.Ref ref -> "\u2504\u2504\u25b7 " + ref.entityName();
      case PropertyType.Complex c -> c.properties().isEmpty() ? "{ }" : "{ ... }";
      case PropertyType.Union union ->
          union.types().stream().map(this::formatType).collect(Collectors.joining(" | "));
      case PropertyType.EmptyArray e -> "?";
      case null, default -> "unknown";
    };

    return type.isArityOne() ? typeSymbol : "[" + typeSymbol + "]";
  }



  public StructuredType getEntity(Node node) {
    return (StructuredType) node.get(UmlRenderingService.COL_NODE_TYPE);
  }

  public void setPropertyPosition(Node node, String propertyPath, int yPosition) {
    umlView.propertyPositions.computeIfAbsent(node, k -> new HashMap<>());
    umlView.propertyPositions.get(node).put(propertyPath, yPosition);
  }

}

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
import java.awt.geom.AffineTransform;
import java.awt.geom.CubicCurve2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.Map;
import java.util.OptionalInt;
import com.aestallon.storageexplorer.client.graph.service.UmlRenderingService;
import com.aestallon.storageexplorer.core.model.type.Association;
import prefuse.Constants;
import prefuse.data.Node;
import prefuse.render.EdgeRenderer;
import prefuse.util.GraphicsLib;
import prefuse.visual.EdgeItem;
import prefuse.visual.VisualItem;

public class AssociationRenderer extends EdgeRenderer {

  private final UmlView umlView;

  public AssociationRenderer(UmlView umlView) {
    super(Constants.EDGE_TYPE_LINE, Constants.EDGE_ARROW_FORWARD);
    this.umlView = umlView;
  }

  @Override
  protected Shape getRawShape(VisualItem item) {
    EdgeItem edge = (EdgeItem) item;
    VisualItem item1 = edge.getSourceItem();
    VisualItem item2 = edge.getTargetItem();

    if (item1 == item2) {
      m_edgeType = Constants.EDGE_TYPE_CURVE;
      Rectangle2D bounds = item1.getBounds();
      double middleX = bounds.getX() + bounds.getWidth() / 2;
      double top = bounds.getY();
      double right = bounds.getX() + bounds.getWidth();
      double startY =
          Math.min(top + ((double) StructuredTypeRenderer.HEADER_HEIGHT) / 2, bounds.getCenterY());
      m_tmpPoints[0].setLocation(right, startY);
      m_tmpPoints[1].setLocation(middleX, top);
      m_cubic = new CubicCurve2D.Double(
          right, startY,
          right + 50, startY,
          right + 50, startY - 100,
          middleX, top);
    } else {
      final var association = (Association) edge.get(UmlRenderingService.COL_EDGE_ASSOC);
      m_edgeType = Constants.EDGE_TYPE_LINE;
      final double sourceY = getPropertySourceY(association, item1).stream()
          .mapToDouble(i -> i)
          .findFirst()
          .orElse(item1.getY());
      final double sourceX;
      final double tarX = item2.getX();
      if (tarX > item1.getBounds().getX() && tarX < item1.getBounds().getX() + item1.getBounds().getWidth()) {
        sourceX = item1.getX();
      } else if (item2.getX() < item1.getX()) {
        sourceX =item1.getBounds().getX();
      } else {
        sourceX = item1.getBounds().getX() + item1.getBounds().getWidth();
      }
      m_tmpPoints[0].setLocation(sourceX, sourceY);
      m_tmpPoints[1].setLocation(item2.getX(), item2.getY());
    }


    m_curWidth = (float) (m_width * getLineWidth(item));
    EdgeItem e = (EdgeItem) item;


    // get starting and ending edge endpoints
    Point2D start = m_tmpPoints[0];
    Point2D end = m_tmpPoints[1];

    // create the arrow head, if needed
    if (e.isDirected() && m_edgeArrow != Constants.EDGE_ARROW_NONE) {
      if (m_edgeType == Constants.EDGE_TYPE_CURVE) {
        AffineTransform t = new AffineTransform();
        t.setToRotation(Math.PI / 4);
        Point2D p = new Point2D.Double();
        Point2D shift = new Point2D.Double();
        double d = start.distance(end) / 5.0;
        p.setLocation((end.getX() - start.getX()) / d, (end.getY() - start.getY()) / d);
        t.transform(p, shift);
        start.setLocation(start.getX() + shift.getX(), start.getY() + shift.getY());
        end.setLocation(end.getX() + shift.getX(), end.getY() + shift.getY());
      }

      // compute the intersection with the target bounding box
      VisualItem dest = e.getTargetItem();
      int i = GraphicsLib.intersectLineRectangle(start, end,
          dest.getBounds(), m_isctPoints);
      if (i > 0) {
        end.setLocation(m_isctPoints[0]);
      }

      // create the arrow head shape
      AffineTransform at = getArrowTrans(start, end, m_curWidth);
      m_curArrow = at.createTransformedShape(m_arrowHead);


      Point2D lineEnd = m_tmpPoints[1];
      lineEnd.setLocation(0, m_edgeType == Constants.EDGE_TYPE_CURVE ? 0 : -m_arrowHeight);
      at.transform(lineEnd, lineEnd);
    } else {
      m_curArrow = null;
    }

    double n1x = m_tmpPoints[0].getX();
    double n1y = m_tmpPoints[0].getY();
    double n2x = m_tmpPoints[1].getX();
    double n2y = m_tmpPoints[1].getY();
    m_line.setLine(n1x, n1y, n2x, n2y);
    return m_edgeType == Constants.EDGE_TYPE_CURVE ? m_cubic : m_line;
  }

  private OptionalInt getPropertySourceY(Association association, VisualItem nodeItem) {
    final var propertyPath = association.propertyPath();
    final var node = (Node) nodeItem.getSourceTuple();
    return findPropertySourceY(propertyPath, node);
  }

  private OptionalInt findPropertySourceY(String propPath, Node node) {
    final Map<String, Integer> propYByPath = umlView.propertyPositions.get(node);
    if (propYByPath == null) {
      return OptionalInt.empty();
    }

    final var propY = propYByPath.get(propPath);
    if (propY != null) {
      return OptionalInt.of(propY);
    }

    final int lastDot = propPath.lastIndexOf('.');
    if (lastDot < 0) {
      return OptionalInt.empty();
    }

    final var parentPropPath = propPath.substring(0, lastDot);
    return findPropertySourceY(parentPropPath, node);
  }

  @Override
  protected AffineTransform getArrowTrans(Point2D p1, Point2D p2,
                                          double width) {
    m_arrowTrans.setToTranslation(p2.getX(), p2.getY());
    m_arrowTrans.rotate(-HALF_PI +
        Math.atan2(p2.getY() - p1.getY(), p2.getX() - p1.getX()));
    if (width > 1) {
      double scalar = width / 2;
      m_arrowTrans.scale(scalar, scalar);
    }
    return m_arrowTrans;
  }

  @Override
  public boolean locatePoint(Point2D p, VisualItem item) {
    Shape s = getShape(item);
    if (s == null) {
      return false;
    } else {
      double width = Math.max(14, getLineWidth(item));
      double halfWidth = width / 2.0;
      return s.intersects(p.getX() - halfWidth,
          p.getY() - halfWidth,
          width, width);
    }
  }

}

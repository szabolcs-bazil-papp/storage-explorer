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
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import com.aestallon.storageexplorer.core.model.type.PropertyType;
import prefuse.Constants;
import prefuse.render.EdgeRenderer;
import prefuse.util.GraphicsLib;
import prefuse.visual.EdgeItem;
import prefuse.visual.VisualItem;

public class AssociationRenderer extends EdgeRenderer {

  private final UmlView visualizer;
  boolean full = true;

  public AssociationRenderer(UmlView visualizer) {
    super(Constants.EDGE_TYPE_LINE, Constants.EDGE_ARROW_FORWARD);
    this.visualizer = visualizer;
  }


  /**
   * Temporary used in getRawShape.
   */
  private Point2D m_isctPoints2[] = new Point2D[2];
  private Point2D starPosition = null;
  private Point2D midPosition = null;
  private Point2D pendingPosition = null;
  private double starTheta;

  @Override
  protected Shape getRawShape(VisualItem item) {
    EdgeItem   edge = (EdgeItem)item;
    VisualItem item1 = edge.getSourceItem();
    VisualItem item2 = edge.getTargetItem();

    int type = m_edgeType;
    boolean reversedCurve = false;
    if (item1 == item2 || true) {
      type = Constants.EDGE_TYPE_CURVE;
    }

    m_tmpPoints[0].setLocation(item1.getX(), item1.getY());
    m_tmpPoints[1].setLocation(item2.getX(), item2.getY());
    
    m_curWidth = (float)(m_width * getLineWidth(item));
    EdgeItem e = (EdgeItem)item;

    boolean forward = (m_edgeArrow == Constants.EDGE_ARROW_FORWARD);

    // get starting and ending edge endpoints
    Point2D start = null, end = null;
    start = m_tmpPoints[forward?0:1];
    end   = m_tmpPoints[forward?1:0];

    if (!full) {
      double midX;
      double midY;
      Point2D sp = start, ep = end;

      VisualItem dest = forward ? e.getTargetItem() : e.getSourceItem();
      int i = GraphicsLib.intersectLineRectangle(start, end,
          dest.getBounds(), m_isctPoints);
      if ( i > 0 ) ep = m_isctPoints[0];

      VisualItem src = !forward ? e.getTargetItem() : e.getSourceItem();
      i = GraphicsLib.intersectLineRectangle(start, end,
          src.getBounds(), m_isctPoints2);
      if ( i > 0 ) sp = m_isctPoints2[0];

      midX = (sp.getX() + ep.getX()) / 2;
      midY = (sp.getY() + ep.getY()) / 2;
      m_tmpPoints[0].setLocation(midX, midY);
    }

    // create the arrow head, if needed
    if ( e.isDirected() && m_edgeArrow != Constants.EDGE_ARROW_NONE) {
      if (type == Constants.EDGE_TYPE_CURVE) {
        AffineTransform t = new AffineTransform();
        t.setToRotation(Math.PI/4 * (reversedCurve? 1 : -1));
        Point2D p = new Point2D.Double(), shift = new Point2D.Double();
        double d = start.distance(end) / 5.0;
        p.setLocation((end.getX() - start.getX()) / d, (end.getY() - start.getY()) / d);
        t.transform(p, shift);
        start.setLocation(start.getX() + shift.getX(), start.getY() + shift.getY());
        end.setLocation(end.getX() + shift.getX(), end.getY() + shift.getY());
      }

      // compute the intersection with the target bounding box
      VisualItem dest = forward ? e.getTargetItem() : e.getSourceItem();
      int i = GraphicsLib.intersectLineRectangle(start, end,
          dest.getBounds(), m_isctPoints);
      if ( i > 0 ) end = m_isctPoints[0];

      // create the arrow head shape
      AffineTransform at = getArrowTrans(start, end, m_curWidth);
      m_curArrow = at.createTransformedShape(m_arrowHead);

      // update the endpoints for the edge shape
      // need to bias this by arrow head size
      if (type == Constants.EDGE_TYPE_CURVE) {
        if (false) {
          m_curArrow = null;
        }
      }
      Point2D lineEnd = m_tmpPoints[forward?1:0];
      lineEnd.setLocation(0, type == Constants.EDGE_TYPE_CURVE? 0 : -m_arrowHeight);
      at.transform(lineEnd, lineEnd);
    } else {
      m_curArrow = null;
    }

    // create the edge shape
    Shape shape = null;
    double n1x = m_tmpPoints[0].getX();
    double n1y = m_tmpPoints[0].getY();
    double n2x = m_tmpPoints[1].getX();
    double n2y = m_tmpPoints[1].getY();
    m_line.setLine(n1x, n1y, n2x, n2y);
    shape = m_line;

    if (false /* assoc == null */) {
      return shape;
    }

    starBounds = null;
    starPosition = null;
    starTheta = 0;
    midPosition = new Point2D.Double((n1x + n2x) / 2, (n1y + n2y) / 2);

      starPosition = new Point2D.Double(m_tmpPoints[forward? 1:0].getX(), m_tmpPoints[forward? 1:0].getY());
      start = starPosition;
      end = m_tmpPoints[forward? 0:1];
      AffineTransform t = new AffineTransform();
      t.setToRotation(-Math.PI/4.5);
      Point2D p = new Point2D.Double(), shift = new Point2D.Double();
      double d = m_tmpPoints[0].distance(m_tmpPoints[1]) / 9.0;
      p.setLocation((end.getX() - start.getX()) / d, (end.getY() - start.getY()) / d);
      t.transform(p, shift);
      starTheta = Math.atan2(end.getY() - start.getY(), end.getX() - start.getX());
      starPosition.setLocation(starPosition.getX() + shift.getX(), starPosition.getY() + shift.getY());
      starBounds = new Rectangle2D.Double(starPosition.getX() - STAR_SIZE * (starWidth / 2), starPosition.getY() - STAR_SIZE * (starHeight / 2), starWidth * STAR_SIZE, starHeight * STAR_SIZE);

    pendingBounds = null;
    pendingPosition = null;

    return shape;
  }

  /**
   * Returns an affine transformation that maps the arrowhead shape
   * to the position and orientation specified by the provided
   * line segment end points.
   */
  @Override
  protected AffineTransform getArrowTrans(Point2D p1, Point2D p2,
                                          double width)
  {
    m_arrowTrans.setToTranslation(p2.getX(), p2.getY());
    m_arrowTrans.rotate(-HALF_PI +
        Math.atan2(p2.getY()-p1.getY(), p2.getX()-p1.getX()));
    if ( width > 1 ) {
      double scalar = width/2;
      m_arrowTrans.scale(scalar, scalar);
    }
    return m_arrowTrans;
  }

//  @Override
//  public void render(Graphics2D g, VisualItem item) {
//    render(g, item, false);
//  }

  public void render(Graphics2D g, VisualItem item, boolean isSelected) {
    item.setSize(isSelected? 3 : 1);
    int color = Color.GREEN.getRGB();
    boolean restricted = false;
    BasicStroke stroke = item.getStroke();

    item.setFillColor(color);
    item.setStrokeColor(color);
//    if (association != null && isObjectNatationFormat(association)) {
//      m_arrowHead = updateArrowHead(m_arrowWidth, m_arrowHeight, association, isSelected);
//      arrowIsPotAggregation = true;
//    } else {
//      if (arrowIsPotAggregation) {
//        m_arrowHead = updateArrowHead(m_arrowWidth, m_arrowHeight);
//      }
//      arrowIsPotAggregation = false;
//    }
    starPosition = null;
    pendingPosition = null;
    midPosition = null;
    super.render(g, item);
    if (starPosition != null && starImage != null) {
      double size = STAR_SIZE;
      AffineTransform t2 = new AffineTransform();
      t2.translate(starWidth / 2, starHeight / 2);
      t2.rotate(starTheta - Math.PI / 8.0);
      t2.translate(-starWidth / 2, -starHeight / 2);
      transform.setTransform(size, 0, 0, size, starPosition.getX() - size * (starWidth / 2), starPosition.getY() - size * (starHeight / 2));
      transform.concatenate(t2);
      g.drawImage(starImage, transform, null);
      starPosition = null;
    }
    if (pendingPosition != null && pendingImage != null) {
      double size = PENDING_SIZE;
      transform.setTransform(size, 0, 0, size, pendingPosition.getX() - size * (pendingWidth / 2), pendingPosition.getY() - size * (pendingHeight / 2));
      g.drawImage(pendingImage, transform, null);
      pendingPosition = null;
    }
    if (midPosition != null) {
//      if (isFKNull) {
//        int r = 5;
//        g.setStroke(new BasicStroke(1.5f));
//        g.setColor(new Color(color));
//        g.drawOval((int) midPosition.getX() - r, (int) midPosition.getY() - r, 2 * r, 2 * r);
//      }
    }
  }

  /**
   * @see prefuse.render.Renderer#setBounds(prefuse.visual.VisualItem)
   */
  @Override
  public void setBounds(VisualItem item) {
    super.setBounds(item);
    if (starBounds != null ) {
      Rectangle2D bbox = (Rectangle2D)item.get(VisualItem.BOUNDS);
      if (bbox != null) {
        Rectangle2D.union(bbox, starBounds, bbox);
      }
    }
    if (pendingBounds != null ) {
      Rectangle2D bbox = (Rectangle2D)item.get(VisualItem.BOUNDS);
      if (bbox != null) {
        Rectangle2D.union(bbox, pendingBounds, bbox);
      }
    }
  }

  private boolean arrowIsPotAggregation = false;
  private AffineTransform transform = new AffineTransform();
  private Rectangle2D starBounds = null;
  private Rectangle2D pendingBounds = null;
  private long lastDataModelVersion = -1;


  /**
   * Returns true if the Point is located inside the extents of the item.
   * This calculation matches against the exact item shape, and so is more
   * sensitive than just checking within a bounding box.
   *
   * @param p the point to test for containment
   * @param item the item to test containment against
   * @return true if the point is contained within the the item, else false
   */
  @Override
  public boolean locatePoint(Point2D p, VisualItem item) {
    Shape s = getShape(item);
    if ( s == null ) {
      return false;
    } else {
      double width = Math.max(14, getLineWidth(item));
      double halfWidth = width/2.0;
      return s.intersects(p.getX()-halfWidth,
          p.getY()-halfWidth,
          width,width);
    }
  }

  /**
   * Render aggregation symbols.
   */
  protected Polygon updateArrowHead(int w, int h, PropertyType.Arity arity, boolean isSelected) {
    if (arity == PropertyType.Arity.MANY) {
      if ( m_arrowHead == null ) {
        m_arrowHead = new Polygon();
      } else {
        m_arrowHead.reset();
      }
      double ws = 0.9;
      double hs = 2.0/3.0;
      if (isSelected) {
        ws /= 1.3;
        hs /= 1.3;
      }
      m_arrowHead.addPoint(0, 0);
      m_arrowHead.addPoint((int) (ws*-w), (int) (hs*(-h)));
      m_arrowHead.addPoint( 0, (int) (hs*(-2*h)));
      m_arrowHead.addPoint((int) (ws*w), (int) (hs*(-h)));
      m_arrowHead.addPoint(0, 0);
      return m_arrowHead;
    } else {
      return updateArrowHead(w, h);
    }
  }

  private Image starImage = null;
  private double starWidth = 0;
  private double starHeight = 0;
  private final double STAR_SIZE = 0.25;
  private Image pendingImage = null;
  private double pendingWidth = 0;
  private double pendingHeight = 0;
  private final double PENDING_SIZE = 0.32;
//  {
//    // load images
//    try {
//      starImage = UIUtil.readImage("/star.png").getImage();
////      starWidth = starImage.getWidth(null);
////      starHeight = starImage.getHeight(null);
//    } catch (Throwable t) {
//      // ignore
//    }
//    try {
//      pendingImage = UIUtil.readImage("/wanr.png").getImage();
//      pendingWidth = pendingImage.getWidth(null);
//      pendingHeight = pendingImage.getHeight(null);
//    } catch (Throwable t) {
//      // ignore
//    }
//  }

}

package com.aestallon.storageexplorer.swing.ui.graph.uml;

import java.awt.*;
import java.util.Map;
import javax.swing.*;

public class PropertyTooltip extends JPanel {

  private static final Color BG = new Color(30, 30, 30, 180);
  private static final Color FG_PRIMARY = new Color(255, 255, 255);
  private static final Color FG_MUTED = new Color(200, 200, 200);
  private static final Color SEPARATOR = new Color(255, 255, 255, 128);
  private static final int MAX_WIDTH = 340;
  private static final int ARC = 12;
  private static final int PAD = 10;
  private static final int CONTENT_WIDTH = MAX_WIDTH - (2 * PAD);

  private String title;
  private Map<String, String> metadata;

  PropertyTooltip() {
    setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
    setOpaque(false);
    setBorder(BorderFactory.createEmptyBorder(PAD, PAD, PAD, PAD));
  }

  public void update(TooltipData data) {
    removeAll();
    if (data != null) {
      if (data.isTypeTooltip()) {
        buildTypeTooltip(data);
      } else {
        buildPropertyTooltip(data);
      }
    }

    revalidate();

    repaint();

    setSize(getPreferredSize());
  }

  private void buildTypeTooltip(TooltipData d) {
    add(title(d.structuralTypeName));
    add(separator());
    add(description(d.typeLevelDescription));
  }

  private void buildPropertyTooltip(TooltipData d) {
    // Header
    add(title(d.structuralTypeName + "::" + d.propertyPath));
    if (d.nominalTypeName != null) {
      add(subtitle(d.nominalTypeName + "::" + d.propertyName));
    }

    // Description
    add(separator());
    add(description(d.propertyDescription));

    // Type match indicator
    add(typeMatchRow(d));

    // Nominal property type section
    if (d.typeMatchStatus != TooltipData.TypeMatchStatus.UNAVAILABLE && d.nominalPropertyTypeName != null) {
      add(separator());
      add(subtitle(d.nominalPropertyTypeName));
      if (d.nominalPropertyTypeDescription != null) {
        add(description(d.nominalPropertyTypeDescription));
      }
    }
  }

  // -------------------------------------------------------------------------
  // Component factories
  // -------------------------------------------------------------------------

  /** h1 — bold, primary colour */
  private JLabel title(String text) {
    return styledLabel(
        "<html><b>" + esc(text) + "</b></html>",
        FG_PRIMARY, Font.BOLD, 14f
    );
  }

  /** h3 — bold, muted colour */
  private JLabel subtitle(String text) {
    return styledLabel(
        "<html><b>" + esc(text) + "</b></html>",
        FG_MUTED, Font.BOLD, 12f
    );
  }

  /** <p><em>…</em></p> — italic, wrapping, muted */
  private JTextArea description(String text) {
    JTextArea area = new JTextArea(text);
    area.setFont(getFont().deriveFont(Font.ITALIC, 12f));
    area.setForeground(FG_MUTED);
    area.setBackground(new Color(0, 0, 0, 0)); // transparent
    area.setOpaque(false);
    area.setEditable(false);
    area.setFocusable(false);
    area.setLineWrap(true);
    area.setWrapStyleWord(true);
    area.setAlignmentX(LEFT_ALIGNMENT);
    // This is the key: constrain width so the height is computed correctly
    area.setSize(new Dimension(CONTENT_WIDTH, Short.MAX_VALUE));
    area.setMaximumSize(new Dimension(CONTENT_WIDTH, area.getPreferredSize().height));
    return area;
  }

  /** Type match indicator row */
  private JLabel typeMatchRow(TooltipData d) {
    String text = switch (d.typeMatchStatus) {
      case MATCH -> "✅ " + d.structuralPropertyTypeName + " == " + d.nominalPropertyTypeName;
      case MISMATCH -> "❌ " + d.structuralPropertyTypeName + " <-> " + d.nominalPropertyTypeName;
      case UNAVAILABLE -> "⚠️ No nominal type information is available";
    };
    return styledLabel(
        "<html><b>" + esc(text) + "</b></html>",
        FG_PRIMARY, Font.BOLD, 12f
    );
  }

  /** Horizontal rule */
  private JSeparator separator() {
    JSeparator sep = new JSeparator(SwingConstants.HORIZONTAL);
    sep.setForeground(SEPARATOR);
    sep.setAlignmentX(LEFT_ALIGNMENT);
    // Top+bottom margin around the line
    sep.setBorder(BorderFactory.createEmptyBorder(4, 0, 6, 0));
    sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 12));
    return sep;
  }

  private JLabel styledLabel(String html, Color fg, int style, float size) {
    JLabel label = new JLabel(html);
    label.setForeground(fg);
    label.setFont(label.getFont().deriveFont(style, size));
    label.setAlignmentX(LEFT_ALIGNMENT);
    label.setMaximumSize(new Dimension(CONTENT_WIDTH, Integer.MAX_VALUE));
    return label;
  }

  // -------------------------------------------------------------------------
  // Painting
  // -------------------------------------------------------------------------

  @Override
  protected void paintComponent(Graphics g) {
    Graphics2D g2 = (Graphics2D) g.create();
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g2.setColor(BG);
    g2.fillRoundRect(0, 0, getWidth(), getHeight(), ARC, ARC);
    g2.dispose();
  }

  @Override
  public Dimension getPreferredSize() {
    Dimension d = super.getPreferredSize();
    d.width = MAX_WIDTH;
    return d;
  }

  // -------------------------------------------------------------------------
  // Helpers
  // -------------------------------------------------------------------------

  private static String esc(String s) {
    if (s == null)
      return "";
    return s.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;");
  }
}

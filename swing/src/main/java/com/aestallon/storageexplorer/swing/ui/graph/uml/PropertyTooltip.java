package com.aestallon.storageexplorer.swing.ui.graph.uml;

import java.awt.*;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import javax.swing.*;

public class PropertyTooltip extends JPanel {

  private String title;
  private Map<String, String> metadata;

  public PropertyTooltip() {
    setOpaque(false);
    setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
  }

  public void update(String title, Map<String, String> metadata) {
    this.title = title;
    this.metadata = metadata;
    // Resize to fit content
    final int width = metadata.values().stream()
        .filter(Objects::nonNull)
        .max(Comparator.comparingInt(String::length))
        .map(String::length)
        .map(it -> it * 6)
        .orElse(150) + 50;
    setPreferredSize(new Dimension(width, 50 + (metadata.size() * 18)));
    revalidate();
    repaint();
  }

  @Override
  protected void paintComponent(Graphics g) {
    Graphics2D g2 = (Graphics2D) g.create();
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

    // Background + shadow
    g2.setColor(new Color(30, 30, 30, 220));
    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);

    // Border
    g2.setColor(new Color(100, 180, 255));
    g2.setStroke(new BasicStroke(1.2f));
    g2.drawRoundRect(0, 0, getWidth(), getHeight(), 10, 10);

    // Title
    g2.setColor(Color.WHITE);
    g2.setFont(new Font("SansSerif", Font.BOLD, 12));
    g2.drawString(title, 10, 18);

    // Metadata rows
    g2.setFont(new Font("SansSerif", Font.PLAIN, 11));
    int y = 36;
    for (Map.Entry<String, String> entry : metadata.entrySet()) {
      if (entry.getValue() == null) {
        continue;
      }
      g2.setColor(new Color(160, 160, 160));
      g2.drawString(entry.getKey() + ": ", 10, y);
      g2.setColor(new Color(220, 220, 220));
      g2.drawString(entry.getValue(), 10 + g2.getFontMetrics().stringWidth(entry.getKey() + ": "),
          y);
      y += 18;
    }

    g2.dispose();
  }
}

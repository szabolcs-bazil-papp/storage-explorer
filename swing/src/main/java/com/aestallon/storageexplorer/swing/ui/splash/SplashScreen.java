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

package com.aestallon.storageexplorer.swing.ui.splash;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import javax.imageio.ImageIO;
import javax.swing.*;

public class SplashScreen extends JWindow {

  public static Optional<SplashScreen> create(final String version,
                                              final String art) {
    try {

      final var bgImage = loadImage(art);
      return Optional.of(new SplashScreen(version, bgImage));

    } catch (IOException e) {
      return Optional.empty();
    }
  }

  private static BufferedImage loadImage(final String path) throws IOException {
    try (InputStream in = SplashScreen.class.getResourceAsStream(path)) {
      if (in == null) {
        throw new IOException("image path not found: " + path);
      }

      return ImageIO.read(in);
    }
  }

  private final String appName = "Storage Explorer";
  private final String version;
  private final BufferedImage bg;
  private final JPanel content;
  private String status = "Initialising...";

  private SplashScreen(final String version, BufferedImage image) {
    this.version = version;
    this.bg = image;

    setSize(bg.getWidth(), bg.getHeight());
    setLocationRelativeTo(null);
    content = new SplashPanel();
    setContentPane(content);
  }

  public void setStatus(final String status) {
    this.status = status;
    this.content.repaint();
  }

  private final class SplashPanel extends JPanel {

    @Override
    protected void paintComponent(Graphics g) {
      super.paintComponent(g);

      final var g2 = (Graphics2D) g.create();
      g2.setRenderingHint(
          RenderingHints.KEY_TEXT_ANTIALIASING,
          RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

      g2.drawImage(bg, 0, 0, null);
      g2.setColor(Color.WHITE);
      g2.setFont(new Font("SansSerif", Font.BOLD, 36));

      int x = 40;
      int y = getHeight() - 80;
      g2.drawString(appName, x, y);
      g2.setFont(new Font("SansSerif", Font.PLAIN, 24));
      g2.drawString("Version " + version, x, y + 30);

      g2.setFont(new Font("SansSerif", Font.PLAIN, 18));
      g2.drawString(status, x, y + 60);

      g2.dispose();
    }

  }

}

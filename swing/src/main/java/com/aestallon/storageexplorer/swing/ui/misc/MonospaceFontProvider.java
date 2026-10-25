/*
 * Copyright (C) 2024 it4all Hungary Kft.
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

package com.aestallon.storageexplorer.swing.ui.misc;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseWheelListener;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

@Service
public class MonospaceFontProvider {

  private static final Logger log = LoggerFactory.getLogger(MonospaceFontProvider.class);

  public static final int DEFAULT_FONT_SIZE = 12;

  private static final String FONT_DIR = "classpath:/fonts/";

  private static final List<String> FONT_FILES = List.of(
      "JetBrainsMono-Thin.ttf",
      "JetBrainsMono-ExtraLight.ttf",
      "JetBrainsMono-Light.ttf",
      "JetBrainsMono-Regular.ttf",
      "JetBrainsMono-Medium.ttf",
      "JetBrainsMono-SemiBold.ttf",
      "JetBrainsMono-Bold.ttf",
      "JetBrainsMono-ExtraBold.ttf",
      "JetBrainsMono-ThinItalic.ttf",
      "JetBrainsMono-ExtraLightItalic.ttf",
      "JetBrainsMono-LightItalic.ttf",
      "JetBrainsMono-Italic.ttf",
      "JetBrainsMono-MediumItalic.ttf",
      "JetBrainsMono-SemiBoldItalic.ttf",
      "JetBrainsMono-BoldItalic.ttf",
      "JetBrainsMono-ExtraBoldItalic.ttf",
      "JetBrainsMonoNL-Thin.ttf",
      "JetBrainsMonoNL-ExtraLight.ttf",
      "JetBrainsMonoNL-Light.ttf",
      "JetBrainsMonoNL-Regular.ttf",
      "JetBrainsMonoNL-Medium.ttf",
      "JetBrainsMonoNL-SemiBold.ttf",
      "JetBrainsMonoNL-Bold.ttf",
      "JetBrainsMonoNL-ExtraBold.ttf",
      "JetBrainsMonoNL-ThinItalic.ttf",
      "JetBrainsMonoNL-ExtraLightItalic.ttf",
      "JetBrainsMonoNL-LightItalic.ttf",
      "JetBrainsMonoNL-Italic.ttf",
      "JetBrainsMonoNL-MediumItalic.ttf",
      "JetBrainsMonoNL-SemiBoldItalic.ttf",
      "JetBrainsMonoNL-BoldItalic.ttf",
      "JetBrainsMonoNL-ExtraBoldItalic.ttf");

  private final ResourceLoader resourceLoader;
  private final ApplicationEventPublisher eventPublisher;
  private final AtomicInteger fontSize;
  private final Font monospace;

  public MonospaceFontProvider(ResourceLoader resourceLoader,
                               ApplicationEventPublisher eventPublisher) {
    this.resourceLoader = resourceLoader;
    this.eventPublisher = eventPublisher;
    this.fontSize = new AtomicInteger(DEFAULT_FONT_SIZE);
    this.monospace = loadFont().deriveFont((float) fontSize.get());
    registerFonts();
  }

  private Font loadFont() {
    final Resource r = resourceLoader.getResource(FONT_DIR + "JetBrainsMono-Regular.ttf");
    if (!r.exists()) {
      throw new RuntimeException("asd");
    }
    try (final var in = r.getInputStream()) {
      return Font.createFont(Font.TRUETYPE_FONT, in);
    } catch (IOException | FontFormatException e) {
      throw new RuntimeException(e);
    }
  }

  private void registerFonts() {
    final GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
    for (final String fontFile : FONT_FILES) {
      final Resource r = resourceLoader.getResource(FONT_DIR + fontFile);
      if (!r.exists()) {
        log.warn("Font resource not found, skipping: [ {} ]", fontFile);
        continue;
      }

      try (final var in = r.getInputStream()) {
        final Font font = Font.createFont(Font.TRUETYPE_FONT, in);
        if (!ge.registerFont(font)) {
          log.warn("Graphics environment rejected font, skipping: [ {} ]", fontFile);
        }
      } catch (IOException | FontFormatException e) {
        log.warn("Could not load font, skipping: [ {} ]", fontFile, e);
      }
    }
  }

  public Font getFont() {
    return monospace.deriveFont((float) fontSize.get());
  }

  public void applyFontSizeChangeAction(JTextArea textArea) {
    final int ctrlMask = KeyEvent.CTRL_DOWN_MASK;

    textArea.addMouseWheelListener(e -> {
      final int wheelRotation = e.getWheelRotation();
      if (wheelRotation == 0) {
        return;
      }

      if ((e.getModifiersEx() & ctrlMask) != ctrlMask) {
        MouseWheelListener[] mouseWheelListeners = textArea
            .getParent()
            .getParent()
            .getMouseWheelListeners();
        for (MouseWheelListener mouseWheelListener : mouseWheelListeners) {
          mouseWheelListener.mouseWheelMoved(e);
        }
        return;
      }


      fontSize.updateAndGet(i -> {
        final int x = i - wheelRotation;
        return Math.max(x, 1);
      });
      fireFontChanged(new FontSizeChange(-wheelRotation));
    });

  }

  private void fireFontChanged(FontSizeChange fontSizeChange) {
    eventPublisher.publishEvent(fontSizeChange);
  }

  public record FontSizeChange(int amount) {}

}

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
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

@Service
public class MonospaceFontProvider {

  public static final int DEFAULT_FONT_SIZE = 12;

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
  }

  private Font loadFont() {
    final Resource r = resourceLoader.getResource("classpath:/fonts/JetBrainsMono-Regular.ttf");
    if (!r.exists()) {
      throw new RuntimeException("asd");
    }
    try (final var in = r.getInputStream()) {
      return Font.createFont(Font.TRUETYPE_FONT, in);
    } catch (IOException | FontFormatException e) {
      throw new RuntimeException(e);
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

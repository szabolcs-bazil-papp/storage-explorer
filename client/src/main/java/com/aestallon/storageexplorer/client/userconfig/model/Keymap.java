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

package com.aestallon.storageexplorer.client.userconfig.model;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.Map;
import java.util.Objects;
import javax.swing.*;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({ "primary", "secondary" })
public final class Keymap {

  public static Map<String, Keymap> defaultKeymaps() {
    return Map.of(
        "Search Indices",
        new Keymap(
            asInt(KeyStroke
                .getKeyStroke(KeyEvent.VK_T,
                    InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK)),
            -1),

        "Parse URI and Load Entry",
        new Keymap(
            asInt(KeyStroke
                .getKeyStroke(KeyEvent.VK_I,
                    InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK)),
            -1),

        "Reset Graph",
        new Keymap(
            asInt(KeyStroke
                .getKeyStroke(KeyEvent.VK_R, InputEvent.SHIFT_DOWN_MASK)),
            -1),

        "Screenshot Graph",
        new Keymap(KeyEvent.VK_F12, -1)

    );
  }

  public static int asInt(KeyStroke keyStroke) {
    if (keyStroke == null) {
      return -1;
    }

    int keyCode = keyStroke.getKeyCode();
    int modifiers = keyStroke.getModifiers();
    return modifiers << 16 | keyCode;
  }

  public static KeyStroke asKeyStroke(int code) {
    if (code == -1) {
      return null;
    }

    int modifiers = (code >> 16) & 0xFFFF;
    int keyCode = code & 0xFFFF;
    return KeyStroke.getKeyStroke(keyCode, modifiers);
  }

  @JsonProperty("primary")
  private int primary = -1;
  @JsonProperty("secondary")
  private int secondary = -1;

  public Keymap() {}

  public Keymap(int primary, int secondary) {
    this.primary = primary;
    this.secondary = secondary;
  }

  public Keymap primary(int primary) {
    this.primary = primary;
    return this;
  }

  public Keymap secondary(int secondary) {
    this.secondary = secondary;
    return this;
  }

  @JsonProperty("primary")
  public int getPrimary() {
    return primary;
  }

  @JsonProperty("primary")
  public void setPrimary(int primary) {
    this.primary = primary;
  }

  @JsonProperty("secondary")
  public int getSecondary() {
    return secondary;
  }

  @JsonProperty("secondary")
  public void setSecondary(int secondary) {
    this.secondary = secondary;
  }

  @Override
  public String toString() {
    return "Keymap [primary=" + primary + ", secondary=" + secondary + "]";
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) { return false; }
    Keymap keymap = (Keymap) o;
    return primary == keymap.primary && secondary == keymap.secondary;
  }

  @Override
  public int hashCode() {
    return Objects.hash(primary, secondary);
  }
}

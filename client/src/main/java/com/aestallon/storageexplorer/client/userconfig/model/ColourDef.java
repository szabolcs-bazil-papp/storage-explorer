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
 * You should have received a copy of the GNU Lesser General Public License aint with this program.
 * If not, see <http://www.gnu.org/licenses/>.
 */

package com.aestallon.storageexplorer.client.userconfig.model;

import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;

public class ColourDef {

  private String name;
  private int light = -1;
  private int dark = -1;

  public ColourDef() {}

  public ColourDef(String name, int light, int dark) {
    this.name = name;
    this.light = light;
    this.dark = dark;
  }

  @JsonProperty("name")
  public String getName() {
    return name;
  }

  @JsonProperty("name")
  public void setName(String name) {
    this.name = name;
  }

  @JsonProperty("light")
  public int getLight() {
    return light;
  }

  @JsonProperty("light")
  public void setLight(int light) {
    this.light = light;
  }

  @JsonProperty("dark")
  public int getDark() {
    return dark;
  }

  @JsonProperty("dark")
  public void setDark(int dark) {
    this.dark = dark;
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass())
      return false;
    ColourDef colourDef = (ColourDef) o;
    return light == colourDef.light && dark == colourDef.dark && Objects.equals(name,
        colourDef.name);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, light, dark);
  }

  @Override
  public String toString() {
    return "ColourDef {" +
        "\n  name: " + name +
        ",\n  light: " + light +
        ",\n  dark: " + dark +
        "\n}";
  }

}

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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;

public class ColourSettings {

  private Map<String, ColourDef> defs = new LinkedHashMap<>();

  public ColourSettings() {}

  @JsonProperty("defs")
  public Map<String, ColourDef> getDefs() {
    return defs;
  }

  @JsonProperty("defs")
  public void setDefs(
      Map<String, ColourDef> defs) {
    this.defs = defs;
  }

  public ColourSettings with(final String key, final ColourDef def) {
    defs.put(key, def);
    return this;
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass())
      return false;
    ColourSettings that = (ColourSettings) o;
    return Objects.equals(defs, that.defs);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(defs);
  }

  @Override
  public String toString() {
    return "ColourSettings {" +
        "\n  defs: " + defs +
        "\n}";
  }

}

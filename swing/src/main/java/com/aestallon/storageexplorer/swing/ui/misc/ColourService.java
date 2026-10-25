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

package com.aestallon.storageexplorer.swing.ui.misc;

import java.awt.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;
import com.aestallon.storageexplorer.client.userconfig.model.Theme;
import com.aestallon.storageexplorer.client.userconfig.service.ThemeService;
import com.aestallon.storageexplorer.common.util.Pair;

@Service
public class ColourService {

  private final ThemeService themeService;
  private final ConcurrentHashMap<Integer, Color> colors;

  public ColourService(ThemeService themeService) {
    this.themeService = themeService;
    colors = new ConcurrentHashMap<>();
  }

  public Color get(final String key) {
    return colors.computeIfAbsent(themeService.colour(key), i -> new Color(i, true));
  }

  public Color get(final String key, Theme theme) {
    return colors.computeIfAbsent(themeService.colour(key, theme), i -> new Color(i, true));
  }

  public List<Pair<String, String>> keys() {
    return themeService
        .colourSettings()
        .getDefs()
        .entrySet().stream()
        .map(e -> Pair.of(e.getKey(), e.getValue().getName()))
        .toList();
  }

  public void set(final String key, Theme theme, Color colour) {
    themeService.colour(key, theme, colour.getRGB());
  }

}

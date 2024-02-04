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

package hu.aestallon.storageexplorer.ui.misc;

import java.io.IOException;
import java.io.InputStream;
import javax.swing.*;
import org.springframework.util.StreamUtils;

public final class IconProvider {

  private IconProvider() {}

  private static String location(final String iconName) {
    return String.format("/icons/%s.png", iconName);
  }

  private static byte[] loadIcon(String iconName) {
    InputStream resourceAsStream = IconProvider.class.getResourceAsStream(location(iconName));

    try {
      return StreamUtils.copyToByteArray(resourceAsStream);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static final ImageIcon LIST = new ImageIcon(loadIcon("list"));
  public static final ImageIcon MAP = new ImageIcon(loadIcon("map"));
  public static final ImageIcon OBJ = new ImageIcon(loadIcon("obj"));
  public static final ImageIcon GRAPH = new ImageIcon(loadIcon("graph"));
}

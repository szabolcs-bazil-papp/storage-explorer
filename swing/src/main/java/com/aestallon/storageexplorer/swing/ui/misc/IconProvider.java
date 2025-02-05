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

import java.io.IOException;
import java.io.InputStream;
import javax.swing.*;
import org.springframework.util.StreamUtils;
import com.aestallon.storageexplorer.core.model.entry.ListEntry;
import com.aestallon.storageexplorer.core.model.entry.MapEntry;
import com.aestallon.storageexplorer.core.model.entry.ObjectEntry;
import com.aestallon.storageexplorer.core.model.entry.ScopedListEntry;
import com.aestallon.storageexplorer.core.model.entry.ScopedMapEntry;
import com.aestallon.storageexplorer.core.model.entry.ScopedObjectEntry;
import com.aestallon.storageexplorer.core.model.entry.SequenceEntry;
import com.aestallon.storageexplorer.core.model.entry.StorageEntry;
import com.aestallon.storageexplorer.core.model.instance.StorageInstance;
import com.aestallon.storageexplorer.core.model.instance.dto.DatabaseVendor;
import com.aestallon.storageexplorer.core.model.instance.dto.FsStorageLocation;
import com.aestallon.storageexplorer.core.model.instance.dto.SqlStorageLocation;
import com.aestallon.storageexplorer.core.model.instance.dto.StorageLocation;

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

  public static ImageIcon getIconForStorageEntry(StorageEntry entry) {
    return switch (entry) {
      case ListEntry list -> switch (list) {
        case ScopedListEntry scoped -> SCOPED_LIST;
        default -> LIST;
      };
      case MapEntry map -> switch (map) {
        case ScopedMapEntry scoped -> SCOPED_MAP;
        default -> MAP;
      };
      case ObjectEntry object -> switch (object) {
        case ScopedObjectEntry scoped -> SCOPED_OBJ;
        default -> OBJ;
      };
      case SequenceEntry seq -> SEQUENCE;
    };
  }

  public static ImageIcon getIconForStorageInstance(final StorageInstance storageInstance) {
    final StorageLocation location = storageInstance.location();
    if (location instanceof FsStorageLocation) {
      return (IconProvider.DB_FS);
    } else if (location instanceof SqlStorageLocation) {
      final DatabaseVendor vendor = ((SqlStorageLocation) location).getVendor();
      if (DatabaseVendor.PG == vendor) {
        return (IconProvider.DB_PG);
      } else if (DatabaseVendor.ORACLE == vendor) {
        return (IconProvider.DB_ORA);
      } else if (DatabaseVendor.H2 == vendor) {
        return (IconProvider.DB_H2);
      } else {
        return (IconProvider.DB);
      }

    } else {
      return (IconProvider.DB);
    }
  }

  public static final ImageIcon FAVICON = new ImageIcon(loadIcon("favicon"));

  public static final ImageIcon LIST = new ImageIcon(loadIcon("list"));
  public static final ImageIcon MAP = new ImageIcon(loadIcon("map"));
  public static final ImageIcon OBJ = new ImageIcon(loadIcon("object"));
  public static final ImageIcon SCOPED_LIST = new ImageIcon(loadIcon("scoped_list"));
  public static final ImageIcon SCOPED_MAP = new ImageIcon(loadIcon("scoped_map"));
  public static final ImageIcon SCOPED_OBJ = new ImageIcon(loadIcon("scoped_object"));
  public static final ImageIcon GRAPH = new ImageIcon(loadIcon("graph"));
  public static final ImageIcon CLOSE = new ImageIcon(loadIcon("close"));
  public static final ImageIcon REFRESH = new ImageIcon(loadIcon("refresh"));
  public static final ImageIcon DB = new ImageIcon(loadIcon("db"));
  public static final ImageIcon SE = new ImageIcon(loadIcon("se"));
  public static final ImageIcon MAGNIFY = new ImageIcon(loadIcon("magnify"));
  public static final ImageIcon SEQUENCE = new ImageIcon(loadIcon("sequence"));
  public static final ImageIcon EDIT = new ImageIcon(loadIcon("edit"));
  public static final ImageIcon DB_FS = new ImageIcon(loadIcon("db_fs"));
  public static final ImageIcon DB_PG = new ImageIcon(loadIcon("db_pg"));
  public static final ImageIcon DB_ORA = new ImageIcon(loadIcon("db_ora"));
  public static final ImageIcon DB_H2 = new ImageIcon(loadIcon("db_h2"));
  public static final ImageIcon DATA_TRANSFER = new ImageIcon(loadIcon("data_transfer"));
  public static final ImageIcon OK = new ImageIcon(loadIcon("ok"));
  public static final ImageIcon NOT_OK = new ImageIcon(loadIcon("not_ok"));
  public static final ImageIcon TREE = new ImageIcon(loadIcon("tree"));
  public static final ImageIcon TERMINAL = new ImageIcon(loadIcon("terminal"));

  public static final ImageIcon ERROR = new ImageIcon(loadIcon("error"));
  public static final ImageIcon WARNING = new ImageIcon(loadIcon("warn"));
  public static final ImageIcon INFO = new ImageIcon(loadIcon("info"));

  public static final ImageIcon ARC_SCRIPT = new ImageIcon(loadIcon("arc_script"));
  public static final ImageIcon SAVE = new ImageIcon(loadIcon("save"));
  public static final ImageIcon PLAY = new ImageIcon(loadIcon("play"));
  public static final ImageIcon PLUS = new ImageIcon(loadIcon("plus"));
  public static final ImageIcon DELETE = new ImageIcon(loadIcon("delete"));
  
  public static final ImageIcon CSV = new ImageIcon(loadIcon("csv"));
  public static final ImageIcon JSON = new ImageIcon(loadIcon("json"));

}

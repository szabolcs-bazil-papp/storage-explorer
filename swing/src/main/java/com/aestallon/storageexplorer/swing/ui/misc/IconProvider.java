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
import javax.swing.*;
import com.aestallon.storageexplorer.core.model.entry.GodObjectEntry;
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
import com.formdev.flatlaf.extras.FlatSVGIcon;

public final class IconProvider {

  public static final int ICON_SIZE = 18;

  private IconProvider() {}

  public record IconColour(Color light, Color dark) {

    Color get() {
      return FlatSVGIcon.isDarkLaf() ? dark : light;
    }

    static final IconColour RED = new IconColour(
        new Color(201, 56, 69),
        new Color(255, 118, 128)
    );

    static final IconColour ORANGE = new IconColour(
        new Color(212, 110, 24),
        new Color(255, 170, 82)
    );

    static final IconColour YELLOW = new IconColour(
        new Color(184, 134, 11),
        new Color(255, 214, 90)
    );

    static final IconColour GREEN = new IconColour(
        new Color(34, 139, 94),
        new Color(94, 212, 150)
    );

    static final IconColour TEAL = new IconColour(
        new Color(16, 140, 150),
        new Color(78, 208, 214)
    );

    static final IconColour BLUE = new IconColour(
        new Color(44, 104, 214),
        new Color(110, 168, 255)
    );

    static final IconColour INDIGO = new IconColour(
        new Color(84, 74, 208),
        new Color(146, 138, 255)
    );

    static final IconColour PURPLE = new IconColour(
        new Color(140, 70, 186),
        new Color(192, 142, 240)
    );

    static final IconColour PINK = new IconColour(
        new Color(200, 60, 128),
        new Color(255, 134, 186)
    );

    static final IconColour SLATE = new IconColour(
        new Color(84, 96, 114),
        new Color(158, 170, 188)
    );

    static final IconColour BLACK_N_WHITE = new IconColour(
        new Color(36, 39, 46),
        new Color(216, 219, 225)
    );

  }


  public record IconFactory(String name, IconColour colour) {

    FlatSVGIcon create() {
      final FlatSVGIcon icon = new FlatSVGIcon("icons/" + name + ".svg", ICON_SIZE, ICON_SIZE);
      if (colour != null) {
        icon.setColorFilter(new FlatSVGIcon.ColorFilter(it -> colour.get()));
      }

      return icon;
    }
  }



  public static FlatSVGIcon getIconForStorageEntry(StorageEntry entry) {
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
        case GodObjectEntry god -> GOD_OBJ;
        default -> OBJ;
      };
      case SequenceEntry seq -> SEQUENCE;
    };
  }

  public static FlatSVGIcon getIconForStorageInstance(final StorageInstance storageInstance) {
    return switch (storageInstance.location()) {
      case null -> DB;
      case FsStorageLocation fs -> DB_FS;
      case SqlStorageLocation sql -> switch (sql.getVendor()) {
        case DatabaseVendor.PG -> DB_PG;
        case DatabaseVendor.ORACLE -> DB_ORA;
        case DatabaseVendor.H2 -> DB_H2;
        case null, default -> DB;
      };
    };
  }

  public static final ImageIcon FAVICON = new IconFactory("favicon", null).create();

  public static final FlatSVGIcon LIST = new IconFactory("list", IconColour.INDIGO).create();
  public static final FlatSVGIcon MAP = new IconFactory("map", IconColour.ORANGE).create();
  public static final FlatSVGIcon OBJ = new IconFactory("object", IconColour.GREEN).create();
  public static final FlatSVGIcon SCOPED_LIST = new IconFactory("scoped_list", IconColour.TEAL).create();
  public static final FlatSVGIcon SCOPED_MAP = new IconFactory("scoped_map", IconColour.PURPLE).create();
  public static final FlatSVGIcon SCOPED_OBJ = new IconFactory("scoped_object", IconColour.SLATE).create();
  public static final FlatSVGIcon GOD_OBJ = new IconFactory("god_object", IconColour.RED).create();
  public static final FlatSVGIcon GRAPH = new IconFactory("graph", IconColour.BLACK_N_WHITE).create();
  public static final FlatSVGIcon CLOSE = new IconFactory("close", IconColour.RED).create();

  public static final FlatSVGIcon REFRESH = new IconFactory("refresh", IconColour.GREEN).create();
  public static final FlatSVGIcon DB = new IconFactory("db", IconColour.SLATE).create();
  public static final FlatSVGIcon SE = new IconFactory("se", IconColour.BLUE).create();
  public static final FlatSVGIcon MAGNIFY = new IconFactory("magnify", IconColour.BLACK_N_WHITE).create();
  public static final FlatSVGIcon SEQUENCE = new IconFactory("sequence", IconColour.INDIGO).create();
  public static final FlatSVGIcon EDIT = new IconFactory("edit", IconColour.ORANGE).create();
  public static final FlatSVGIcon DIFF = new IconFactory("diff", IconColour.PINK).create();
  public static final FlatSVGIcon DB_FS = new IconFactory("db_fs", IconColour.YELLOW).create();
  public static final FlatSVGIcon DB_PG = new IconFactory("db_pg", null).create();
  public static final FlatSVGIcon DB_ORA = new IconFactory("db_ora", null).create();
  public static final FlatSVGIcon DB_H2 = new IconFactory("db_h2", null).create();
  public static final FlatSVGIcon DATA_TRANSFER = new IconFactory("data_transfer", IconColour.TEAL).create();
  public static final FlatSVGIcon OK = new IconFactory("ok", IconColour.GREEN).create();
  public static final FlatSVGIcon NOT_OK = new IconFactory("not_ok", IconColour.RED).create();
  public static final FlatSVGIcon TREE = new IconFactory("tree", IconColour.GREEN).create();
  public static final FlatSVGIcon TERMINAL = new IconFactory("terminal", IconColour.BLACK_N_WHITE).create();

  public static final FlatSVGIcon ERROR = new IconFactory("error", IconColour.RED).create();
  public static final FlatSVGIcon WARNING = new IconFactory("warn", IconColour.YELLOW).create();
  public static final FlatSVGIcon INFO = new IconFactory("info", IconColour.BLUE).create();

  public static final FlatSVGIcon ARC_SCRIPT = new IconFactory("arc_script", IconColour.TEAL).create();
  public static final FlatSVGIcon SAVE = new IconFactory("save", IconColour.BLUE).create();
  public static final FlatSVGIcon PLAY = new IconFactory("play", IconColour.GREEN).create();
  public static final FlatSVGIcon PLUS = new IconFactory("plus", IconColour.GREEN).create();
  public static final FlatSVGIcon DELETE = new IconFactory("delete", IconColour.RED).create();

  public static final FlatSVGIcon CSV = new IconFactory("csv", IconColour.GREEN).create();
  public static final FlatSVGIcon JSON = new IconFactory("json", IconColour.YELLOW).create();

  public static final FlatSVGIcon UML = new IconFactory("uml", IconColour.INDIGO).create();
  public static final FlatSVGIcon PUML = new IconFactory("plantuml", null).create();

}

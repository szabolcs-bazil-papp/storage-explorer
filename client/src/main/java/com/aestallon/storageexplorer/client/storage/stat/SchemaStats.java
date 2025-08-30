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

package com.aestallon.storageexplorer.client.storage.stat;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import com.aestallon.storageexplorer.core.model.instance.StorageInstance;

public final class SchemaStats {

  public static final NumberFormat PERCENT_FORMAT;

  static {
    NumberFormat temp = NumberFormat.getPercentInstance(Locale.getDefault());
    temp.setMaximumFractionDigits(2);
    PERCENT_FORMAT = temp;
  }

  private SchemaStats() {}


  public record EntryStat(String schema, boolean valid) {}


  public static final class SchemaRow {



    private final String schema;
    private long total;
    private long valid;

    private SchemaRow(final String schema) {
      this.schema = schema;
      this.total = 0;
      this.valid = 0;
    }

    private void add(final boolean valid) {
      this.total++;
      if (valid) {
        this.valid++;
      }
    }

    public String schema() {
      return schema;
    }

    public long total() {
      return total;
    }

    public long valid() {
      return valid;
    }

    public long invalid() {
      return total() - valid();
    }

    public double validPercent() {
      return (total == 0L) ? 0L : ((double) valid) / total();
    }

    public String validPercentString() {
      return PERCENT_FORMAT.format(validPercent());
    }

  }


  public record SchemaStatData(long total, long valid, List<SchemaRow> rows) {

    private static final String[] COLS =
        { "Schema", "Total", "Valid", "Invalid", "Valid Percentage" };
    private static final Class<?>[] COL_CLASSES =
        { String.class, Long.class, Long.class, Long.class, String.class };
    
    public String getHeader(int column) {
      return COLS[column];
    }
    
    public int getRowCount() {
      return rows.size() + 1;
    }
    
    public int getColumnCount() {
      return COLS.length;
    }
    
    public Object getValueAt(int row, int column) {
      return switch (row) {
        case 0 -> switch (column) {
          case 0 -> "TOTAL";
          case 1 -> total;
          case 2 -> valid;
          case 3 -> total - valid;
          case 4 -> total == 0L ? "0" : SchemaStats.PERCENT_FORMAT.format(((double) valid) / total);
          default -> null;
        };
        default -> switch (column) {
          case 0 -> rows.get(row - 1).schema();
          case 1 -> rows.get(row - 1).total();
          case 2 -> rows.get(row - 1).valid();
          case 3 -> rows.get(row - 1).invalid();
          case 4 -> rows.get(row - 1).validPercentString();
          default -> null;
        };
      };
    }
    
    public Class<?> getColumnClass(int column) {
      return COL_CLASSES[column];
    }
    
  }


  public static SchemaStatData of(StorageInstance storageInstance) {
    final var stats = storageInstance.entities()
        .map(it -> new EntryStat(it.uri().getScheme(), it.valid()))
        .toList();
    final var total = stats.size();
    final var valid = stats.stream().filter(it -> it.valid).count();

    final Map<String, SchemaRow> schemaMap = new HashMap<>();
    for (final var entry : stats) {
      schemaMap.computeIfAbsent(entry.schema(), SchemaRow::new).add(entry.valid());
    }
    final var rows = new ArrayList<>(schemaMap.values());
    rows.sort(Comparator.comparing(SchemaRow::schema));
    return new SchemaStatData(total, valid, rows);
  }

}

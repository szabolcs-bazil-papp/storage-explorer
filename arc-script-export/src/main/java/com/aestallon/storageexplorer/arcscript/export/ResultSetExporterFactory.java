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

package com.aestallon.storageexplorer.arcscript.export;

public class ResultSetExporterFactory {

  public enum Target { CSV, JSON }


  private final CsvResultSetExporter csvExporter;
  private final JsonResultSetExporter jsonExporter;
  
  public ResultSetExporterFactory() {
    this.csvExporter = new CsvResultSetExporter();
    this.jsonExporter = new JsonResultSetExporter();
  }

  public ResultSetExporter get(final Target target) {
    return switch (target) {
      case CSV -> csvExporter;
      case JSON -> jsonExporter;
    };
  }

}

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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import com.aestallon.storageexplorer.arcscript.engine.ArcScriptResult;
import com.opencsv.CSVWriterBuilder;

public class CsvResultSetExporter implements ResultSetExporter {

  @Override
  public Result export(ArcScriptResult.ResultSet resultSet, Path path) {
    final var exporter = CsvExporter.of(resultSet.meta());
    try (final var writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8);
        final var csvWriter = new CSVWriterBuilder(writer)
            .withLineEnd(System.lineSeparator())
            .withSeparator(',')
            .withQuoteChar('"')
            .build()) {

      csvWriter.writeNext(exporter.headers());
      for (final var row : resultSet.rows()) {
        csvWriter.writeNext(exporter.row(row));
      }
      return new Result.Ok();

    } catch (IOException e) {
      return new Result.Error(e.getMessage());
    }
  }

  private sealed interface CsvExporter {

    private static CsvExporter of(ArcScriptResult.ResultSetMeta meta) {
      return (meta.columns().isEmpty()) ? new Default() : new Custom(meta);
    }

    String[] headers();

    String[] row(final ArcScriptResult.QueryResultRow row);

    final class Default implements CsvExporter {

      private static final String[] DEFAULT_HEADERS = { "URI" };

      @Override
      public String[] headers() {
        return DEFAULT_HEADERS;
      }

      @Override
      public String[] row(ArcScriptResult.QueryResultRow row) {
        return new String[] { row.entry().uri().toString() };
      }

    }


    final class Custom extends CustomExport implements CsvExporter {

      private Custom(ArcScriptResult.ResultSetMeta meta) {
        super(meta);
      }

      public String[] headers() {
        return headers;
      }

      public String[] row(ArcScriptResult.QueryResultRow row) {
        return Arrays.stream(props)
            .map(prop -> row.cells().get(prop))
            .map(it -> it == null ? "" : it.toString())
            .toArray(String[]::new);
      }

    }

  }

}

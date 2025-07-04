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

package com.aestallon.storageexplorer.client.asexport;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import com.aestallon.storageexplorer.arcscript.engine.ArcScriptResult;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONException;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONWriter;

public class JsonResultSetExporter implements ResultSetExporter {

  @Override
  public Result export(ArcScriptResult.ResultSet resultSet, Path path) {
    final JsonExport export = JsonExport.of(resultSet.meta());
    final JSONArray jsonArray = new JSONArray();
    for (final var row : resultSet.rows()) {
      jsonArray.add(export.row(row));
    }

    try (final var out = Files.newOutputStream(path)) {

      JSON.writeTo(out, jsonArray, JSONWriter.Feature.PrettyFormat);
      return new Result.Ok();

    } catch (IOException | JSONException e) {
      return new Result.Error(e.getMessage());
    }

  }


  private sealed interface JsonExport {

    private static JsonExport of(ArcScriptResult.ResultSetMeta meta) {
      return (meta.columns().isEmpty()) ? new Default() : new Custom(meta);
    }

    Object row(ArcScriptResult.QueryResultRow row);

    final class Default implements JsonExport {

      @Override
      public Object row(ArcScriptResult.QueryResultRow row) {
        return row.entry().uri().toString();
      }

    }


    final class Custom extends CustomExport implements JsonExport {

      private Custom(ArcScriptResult.ResultSetMeta meta) {
        super(meta);
      }

      @Override
      public Object row(ArcScriptResult.QueryResultRow row) {
        final JSONObject obj = new JSONObject();
        for (int i = 0; i < headers.length; i++) {
          final var cell = row.cells().get(props[i]);
          obj.put(headers[i], cell == null ? null : cell.value());
        }
        return obj;
      }

    }
  }

}

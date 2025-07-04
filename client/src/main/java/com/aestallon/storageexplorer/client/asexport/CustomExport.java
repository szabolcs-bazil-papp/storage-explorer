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

import com.aestallon.storageexplorer.arcscript.engine.ArcScriptResult;

class CustomExport {

  protected final String[] headers;
  protected final String[] props;

  protected CustomExport(ArcScriptResult.ResultSetMeta meta) {
    final var columns = meta.columns();
    headers = new String[columns.size()];
    props = new String[columns.size()];
    for (int i = 0; i < columns.size(); i++) {
      final var col = columns.get(i);
      headers[i] = col.title();
      props[i] = col.prop();
    }
  }

}

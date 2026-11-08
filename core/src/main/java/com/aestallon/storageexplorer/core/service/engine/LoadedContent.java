/*
 * Copyright (C) 2026 Szabolcs Bazil Papp
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

package com.aestallon.storageexplorer.core.service.engine;

import java.util.LinkedHashMap;
import java.util.List;

/**
 * What {@link ContentHandle#materialize()} actually loaded, kind-aware. {@code ObjectEntry}/
 * {@code ListEntry}/{@code MapEntry} load a {@link JsonObject}; {@code ObjectStreamEntry} loads
 * an {@link ObjectStream}; {@code TableDataEntry} loads a {@link TableData}. Callers that only
 * care about one entry kind pattern-match and ignore the others.
 */
public sealed interface LoadedContent {

  /** A plain, order-preserving JSON object — deliberately not a generic tree type, same
   *  reasoning as {@link EntryMetadata}: this is the shape every caller actually needs. */
  record JsonObject(LinkedHashMap<String, Object> data) implements LoadedContent {}

  /** The entries of an object stream, each a parsed JSON document, in file order. */
  record ObjectStream(List<LinkedHashMap<String, Object>> entries) implements LoadedContent {}

  /** Table data: a flat, column-named grid. Each row has one value per column, in the same
   *  order as {@code columns}; a cell holds a JSON-shaped Java value (String/Long/Double/
   *  Boolean/Map/List/null). */
  record TableData(List<String> columns, List<List<Object>> rows) implements LoadedContent {}
}

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

package com.aestallon.storageexplorer.core.service.index;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.aestallon.storageexplorer.core.model.entry.UriProperty;
import com.aestallon.storageexplorer.core.util.Uris;

/**
 * Finds every outgoing reference in a JSON document without ever materializing it as a
 * {@code Map<String, Object>} — walks the raw bytes token by token with a Jackson streaming
 * {@link JsonParser}, tracking the property path as it descends, and emits a {@link UriProperty}
 * for every string leaf whose value matches the smartbit4all URI shape ({@link Uris#parseStr}).
 *
 * <p>This exists for the operations that only need the reference set, not the whole document —
 * graph adjacency discovery chief among them — on the storages this whole rework is motivated by
 * (hundreds of GB+), where building a full {@code Map} per entry just to throw most of it away
 * again is exactly the kind of eager, whole-document cost the rework means to avoid.
 *
 * <p>Deliberately reuses {@link Uris#parseStr} rather than re-implementing its shape heuristic —
 * the point of this class is a different (streaming) traversal strategy over the same addressing
 * rules, not a second, possibly-drifting definition of what counts as a URI leaf. The one
 * existing exception stays: the top-level {@code "uri"} field (the entry's own self-reference) is
 * never reported, matching {@code ObjectEntry}'s own {@code initUriProperties} today.
 */
public final class UriLeafScanner {

  private static final JsonFactory JSON_FACTORY = new JsonFactory();

  private UriLeafScanner() {}

  /** Scans {@code json}, closing it before returning. */
  public static Set<UriProperty> scan(final InputStream json) throws IOException {
    try (JsonParser parser = JSON_FACTORY.createParser(json)) {
      final Set<UriProperty> found = new HashSet<>();
      if (parser.nextToken() != null) {
        walk(parser, new ArrayList<>(), found);
      }
      return found;
    }
  }

  private static void walk(final JsonParser p, final List<UriProperty.Segment> path,
                            final Set<UriProperty> out) throws IOException {
    switch (p.currentToken()) {
      case START_OBJECT -> {
        while (p.nextToken() != JsonToken.END_OBJECT) {
          final String key = p.currentName();
          p.nextToken();
          path.add(UriProperty.Segment.key(key));
          walk(p, path, out);
          path.remove(path.size() - 1);
        }
      }
      case START_ARRAY -> {
        int idx = 0;
        while (p.nextToken() != JsonToken.END_ARRAY) {
          path.add(UriProperty.Segment.idx(idx++));
          walk(p, path, out);
          path.remove(path.size() - 1);
        }
      }
      case VALUE_STRING -> {
        final UriProperty.Segment[] segments = path.toArray(UriProperty.Segment[]::new);
        if (!UriProperty.Segment.isOwnUri(segments)) {
          Uris.parseStr(p.getText()).ifPresent(uri -> out.add(UriProperty.of(segments, uri)));
        }
      }
      default -> {
        // numbers, booleans, nulls: never URI-shaped, nothing to do.
      }
    }
  }
}

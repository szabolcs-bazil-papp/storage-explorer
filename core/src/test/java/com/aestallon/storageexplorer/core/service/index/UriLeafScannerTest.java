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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.stream.Stream;
import static java.util.stream.Collectors.toSet;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import com.aestallon.storageexplorer.common.util.Pair;
import com.aestallon.storageexplorer.core.model.entry.UriProperty;
import com.aestallon.storageexplorer.core.util.ObjectMaps;
import com.aestallon.storageexplorer.core.util.Uris;

/**
 * Regression safety net for T2.4, run while the pre-M4 code it's checked against still exists
 * side by side: for a representative set of JSON documents, {@link UriLeafScanner}'s streaming
 * traversal must find exactly the same {@link UriProperty} set as today's map-based path
 * ({@code ObjectEntry.initUriProperties}, reproduced here as {@link #legacyUriProperties} via its
 * two building blocks, {@link ObjectMaps#flatten} and {@link Uris#parseStr}) — this is the one
 * place a silent correctness bug in the rework would be easy to miss, per the implementation
 * plan's own note on this task.
 */
class UriLeafScannerTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @SuppressWarnings("unchecked")
  private static Set<UriProperty> legacyUriProperties(final String json) throws IOException {
    final LinkedHashMap<String, Object> map = MAPPER.readValue(json, LinkedHashMap.class);
    return ObjectMaps.flatten(map)
        .filter(it -> !UriProperty.Segment.isOwnUri(it.a()))
        .map(Pair.onB(Uris::parse))
        .flatMap(Pair.streamOnB())
        .map(it -> UriProperty.of(it.a(), it.b()))
        .collect(toSet());
  }

  private static Set<UriProperty> scannedUriProperties(final String json) throws IOException {
    return UriLeafScanner.scan(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
  }

  static Stream<String> fixtures() {
    return Stream.of(
        // own "uri" field is skipped; a timestamp-shaped nested reference is kept
        """
        {"uri":"s:/a/self","owner":"s:/a/2026/1/2/3/other-uuid"}
        """,
        // each of the four stored-collection markers
        """
        {"a":"s-collections:/storedlist/items-s"}
        """,
        """
        {"a":"s-collections:/storedmap/items-s"}
        """,
        """
        {"a":"s-collections:/storedRef/items-s"}
        """,
        """
        {"a":"s:/storedSeq/counter-s"}
        """,
        // ObjectReferenceById shape
        """
        {"a":"x:/org_smartbit4all_api_object_bean_ObjectReferenceById/1/1/1/1/u"}
        """,
        // a ":/"-containing string that matches none of the three required sub-patterns
        """
        {"a":"http://example.com","b":"not-a-uri-at-all"}
        """,
        // null and non-string leaves never produce a property
        """
        {"a":null,"b":42,"c":true,"d":3.14}
        """,
        // nested objects and arrays, including an array of objects - path tracking
        """
        {
          "nested": {"child": "s:/a/2026/1/2/3/nested-child"},
          "list": ["s:/a/2026/1/2/3/list-0", "plain string", "s:/a/2026/1/2/3/list-2"],
          "objects": [
            {"ref": "s:/a/2026/1/2/3/obj-0"},
            {"ref": "s:/a/2026/1/2/3/obj-1"}
          ],
          "deep": {"nested": {"uri": "s:/a/2026/1/2/3/deep-not-own"}}
        }
        """,
        // a trailing "." / ".v" otherwise-URI-shaped string is excluded
        """
        {"a":"s:/a/2026/1/2/3/trailing.","b":"s:/a/2026/1/2/3/trailing.v"}
        """,
        // empty object, no leaves at all
        "{}"
    );
  }

  @ParameterizedTest
  @MethodSource("fixtures")
  void matchesTodaysMapBasedBehavior(final String json) throws IOException {
    assertThat(scannedUriProperties(json)).isEqualTo(legacyUriProperties(json));
  }

  /** A sanity check independent of {@link #legacyUriProperties}: confirms the scanner actually
   *  finds references rather than both sides trivially agreeing on empty sets. */
  @org.junit.jupiter.api.Test
  void findsExactlyTheExpectedReferences_notJustAgreeingOnEmpty() throws IOException {
    final String json = """
        {
          "nested": {"child": "s:/a/2026/1/2/3/nested-child"},
          "list": ["s:/a/2026/1/2/3/list-0", "plain string", "s:/a/2026/1/2/3/list-2"]
        }
        """;
    final Set<UriProperty> found = scannedUriProperties(json);
    assertThat(found).hasSize(3);
    assertThat(found)
        .extracting(p -> p.uri().toString())
        .containsExactlyInAnyOrder(
            "s:/a/2026/1/2/3/nested-child",
            "s:/a/2026/1/2/3/list-0",
            "s:/a/2026/1/2/3/list-2");
  }
}

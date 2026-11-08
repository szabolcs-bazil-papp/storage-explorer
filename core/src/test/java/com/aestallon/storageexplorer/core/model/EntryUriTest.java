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

package com.aestallon.storageexplorer.core.model;

import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Mirrors {@code sb4a-fs}'s own {@code uri} module test suite
 * ({@code ../sb4a-fs/crates/sb4a-fs/src/uri.rs}), so the Java port is checked against the same
 * cases the Rust library already validates.
 */
class EntryUriTest {

  private static final String SAMPLE =
      "account-ticket:/hu_it4all_eji_fiok_domain_model_AccountTicket/2025/12/19/16/18/5baf7e92-4abe-414b-81dd-e8018f1a99c5";

  private static EntryUri parse(final String text) {
    return EntryUri.parse(text).orElseThrow(() -> new AssertionError("expected " + text + " to parse"));
  }

  @Test
  @DisplayName("Parses a head URI with no version")
  void parsesHeadUri() {
    final EntryUri u = parse(SAMPLE);
    assertThat(u.version()).isEmpty();
    assertThat(u.scheme()).isEqualTo("account-ticket");
    assertThat(u.id()).isEqualTo("5baf7e92-4abe-414b-81dd-e8018f1a99c5");
    assertThat(u.segments()).hasSize(7);
    assertThat(u.isSingleVersion()).isFalse();
    assertThat(u.toString()).isEqualTo(SAMPLE);
  }

  @Test
  @DisplayName("Parses a versioned URI and strips the version back off via latest()")
  void parsesVersion() {
    final EntryUri u = parse(SAMPLE + ".v12");
    assertThat(u.version()).hasValue(12L);
    assertThat(u.latest().toString()).isEqualTo(SAMPLE);
    assertThat(u.toString()).isEqualTo(SAMPLE + ".v12");
    assertThat(parse(SAMPLE + ".v0").latest().toString()).isEqualTo(SAMPLE);
  }

  @Test
  @DisplayName("A non-numeric '.v' postfix is just part of the id, not a version")
  void nonNumericVersionPostfixIsPartOfTheId() {
    EntryUri u = parse("s:/a/b.vx");
    assertThat(u.version()).isEmpty();
    assertThat(u.id()).isEqualTo("b.vx");

    u = parse("s:/a/b.v");
    assertThat(u.version()).isEmpty();
    assertThat(u.id()).isEqualTo("b.v");
  }

  @Test
  @DisplayName("An id may contain dots, as long as it doesn't end with one")
  void idMayContainDots() {
    final EntryUri u = parse("apis:/hu/it4all/X/hu.it4all.X.v3");
    assertThat(u.version()).hasValue(3L);
    assertThat(u.id()).isEqualTo("hu.it4all.X");
  }

  @Test
  @DisplayName("The '-s' suffix marks a single-version entry")
  void singleVersionMarker() {
    final EntryUri u = parse("sv:/a/b/05df003f-46dd-4a5a-bf35-a1e34189be94-s");
    assertThat(u.isSingleVersion()).isTrue();
  }

  @Test
  @DisplayName("A trailing fragment is dropped")
  void fragmentIsDropped() {
    final EntryUri u = parse("s:/a/b#relations");
    assertThat(u.toString()).isEqualTo("s:/a/b");
  }

  @Test
  @DisplayName("Percent-encoding round-trips through parse and toString")
  void percentEncodingRoundTrips() {
    final EntryUri u = parse("s:/a/b%20c");
    assertThat(u.id()).isEqualTo("b c");
    assertThat(u.toString()).isEqualTo("s:/a/b%20c");
  }

  @ParameterizedTest
  @DisplayName("Rejects unusable URIs")
  @ValueSource(strings = {
      "",
      "nocolon",
      ":/a",
      "s:a/b",
      "s:/",
      "s://a",
      "s:/a//b",
      "s:/a/../b",
      "s:/a/./b",
      "s:/a/%2e%2e/b",
      "s:/a/b/",
      "s:/a\\b",
      "s:/a/b:c",
      "s:/a/%zz",
      "s:/a/b.",
      "s:/.v1",
      "bad scheme:/a",
      "s:/a/%00",
  })
  void rejectsUnusableUris(final String bad) {
    assertThat(EntryUri.parse(bad)).as("`%s` should be rejected", bad).isEmpty();
  }

  @Test
  @DisplayName("fromSegments builds a URI literally, with no decoding or version splitting")
  void fromSegmentsBuildsLiterally() {
    final Optional<EntryUri> u = EntryUri.fromSegments("org", java.util.List.of("a", "b.v3"));
    assertThat(u).isPresent();
    assertThat(u.get().toString()).isEqualTo("org:/a/b.v3");
    assertThat(u.get().version()).isEmpty();
  }
}

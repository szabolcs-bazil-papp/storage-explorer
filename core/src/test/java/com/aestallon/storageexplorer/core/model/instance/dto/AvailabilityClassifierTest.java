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

package com.aestallon.storageexplorer.core.model.instance.dto;

import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AvailabilityClassifierTest {

  @Test
  @DisplayName("a null location is misconfigured")
  void nullLocationIsMisconfigured() {
    assertThat(AvailabilityClassifier.isMisconfigured(null)).isTrue();
  }

  @Test
  @DisplayName("an FS location with a real-looking path is not misconfigured")
  void fsLocationWithARealLookingPathIsFine() {
    final FsStorageLocation location = new FsStorageLocation().path(Path.of("C:", "storage"));
    assertThat(AvailabilityClassifier.isMisconfigured(location)).isFalse();
    assertThat(AvailabilityClassifier.misconfiguredReason(location)).isEmpty();
  }

  @Test
  @DisplayName("an FS location with no path is misconfigured")
  void fsLocationWithNoPathIsMisconfigured() {
    final FsStorageLocation location = new FsStorageLocation();
    assertThat(AvailabilityClassifier.isMisconfigured(location)).isTrue();
    assertThat(AvailabilityClassifier.misconfiguredReason(location).orElseThrow())
        .contains("no path given");
  }

  @Test
  @DisplayName("an FS location with a blank path is misconfigured")
  void fsLocationWithABlankPathIsMisconfigured() {
    final FsStorageLocation location = new FsStorageLocation().path(Path.of(""));
    assertThat(AvailabilityClassifier.isMisconfigured(location)).isTrue();
  }

  @Test
  @DisplayName("a SQL location with a well-formed JDBC URL is not misconfigured")
  void sqlLocationWithAWellFormedJdbcUrlIsFine() {
    final SqlStorageLocation location = new SqlStorageLocation()
        .dbConnectionData(new DatabaseConnectionData().url("jdbc:postgresql://localhost:5432/db"));
    assertThat(AvailabilityClassifier.isMisconfigured(location)).isFalse();
  }

  @Test
  @DisplayName("a SQL location with no connection data is misconfigured")
  void sqlLocationWithNoConnectionDataIsMisconfigured() {
    final SqlStorageLocation location = new SqlStorageLocation();
    assertThat(AvailabilityClassifier.isMisconfigured(location)).isTrue();
    assertThat(AvailabilityClassifier.misconfiguredReason(location).orElseThrow())
        .contains("no connection data given");
  }

  @Test
  @DisplayName("a SQL location with a blank URL is misconfigured")
  void sqlLocationWithABlankUrlIsMisconfigured() {
    final SqlStorageLocation location = new SqlStorageLocation()
        .dbConnectionData(new DatabaseConnectionData().url("  "));
    assertThat(AvailabilityClassifier.isMisconfigured(location)).isTrue();
  }

  @Test
  @DisplayName("a SQL location with a syntactically-off JDBC URL is misconfigured")
  void sqlLocationWithASyntacticallyOffUrlIsMisconfigured() {
    final SqlStorageLocation location = new SqlStorageLocation()
        .dbConnectionData(new DatabaseConnectionData().url("postgresql://localhost:5432/db"));
    assertThat(AvailabilityClassifier.isMisconfigured(location)).isTrue();
    assertThat(AvailabilityClassifier.misconfiguredReason(location).orElseThrow())
        .contains("must start with");
  }
}

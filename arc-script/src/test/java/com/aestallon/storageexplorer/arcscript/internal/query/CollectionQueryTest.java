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

package com.aestallon.storageexplorer.arcscript.internal.query;

import org.junit.jupiter.api.Test;
import com.aestallon.storageexplorer.arcscript.api.Arc;
import com.aestallon.storageexplorer.arcscript.internal.ArcScriptImpl;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * DSL-level tests of the {@code list}/{@code map} collection source specifiers and their mutual
 * exclusion with type-based sourcing.
 */
class CollectionQueryTest {

  private static QueryInstructionImpl soleQuery(final String script) {
    final var arcScript = Arc.compile(script);
    assertThat(arcScript).isInstanceOf(ArcScriptImpl.class);
    final var instructions = ((ArcScriptImpl) arcScript).instructions;
    assertThat(instructions).hasSize(1);
    assertThat(instructions.getFirst()).isInstanceOf(QueryInstructionImpl.class);
    return (QueryInstructionImpl) instructions.getFirst();
  }

  @Test
  void listSource_isRecordedOnTheInstruction() {
    final var query = soleQuery("""
        query {
          list 'active-users'
          from 'org'
        }""");

    assertThat(query._collectionKind).isEqualTo(QueryInstructionImpl.CollectionSourceKind.LIST);
    assertThat(query._collectionName).isEqualTo("active-users");
    assertThat(query._types).isEmpty();
    assertThat(query._schemas).containsExactly("org");
    assertThat(query.toString()).contains("list active-users");
  }

  @Test
  void mapSource_isRecordedOnTheInstruction() {
    final var query = soleQuery("""
        query {
          map 'contentsById'
          from 'documentdossier'
        }""");

    assertThat(query._collectionKind).isEqualTo(QueryInstructionImpl.CollectionSourceKind.MAP);
    assertThat(query._collectionName).isEqualTo("contentsById");
    assertThat(query.toString()).contains("map contentsById");
  }

  @Test
  void typeSourceAfterCollectionSource_isRejected() {
    assertThatThrownBy(() -> Arc.compile("""
        query {
          list 'active-users'
          every 'User'
          from 'org'
        }"""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("already targets a stored collection");
  }

  @Test
  void collectionSourceAfterTypeSource_isRejected() {
    assertThatThrownBy(() -> Arc.compile("""
        query {
          every 'User'
          list 'active-users'
          from 'org'
        }"""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("already targets types");
  }

  @Test
  void singleEntryFormAfterCollectionSource_isRejected() {
    assertThatThrownBy(() -> Arc.compile("""
        query {
          map 'contentsById'
          a 'User'
          from 'documentdossier'
        }"""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("already targets a stored collection");
  }

  @Test
  void multipleCollectionSources_areRejected() {
    assertThatThrownBy(() -> Arc.compile("""
        query {
          list 'active-users'
          map 'contentsById'
          from 'org'
        }"""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("already targets a stored collection");
  }

  @Test
  void blankCollectionName_isRejected() {
    assertThatThrownBy(() -> Arc.compile("""
        query {
          list '  '
          from 'org'
        }"""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("cannot be null or blank");
  }

}

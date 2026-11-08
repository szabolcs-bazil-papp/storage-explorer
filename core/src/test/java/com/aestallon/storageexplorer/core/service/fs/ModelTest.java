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

package com.aestallon.storageexplorer.core.service.fs;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.aestallon.storageexplorer.core.service.engine.EngineResult;

/**
 * Mirrors {@code sb4a-fs}'s own {@code model} module test suite
 * ({@code ../sb4a-fs/crates/sb4a-fs/src/model.rs}), using the exact descriptor bytes from its
 * fixture corpus (inlined here rather than read from the sibling checkout — reading the shared
 * corpus from disk is T1.9's job; this is a faithful but self-contained unit test).
 *
 * <p>The full {@code every_fixture_document_round_trips_byte_for_byte} test, and
 * {@code relation_data_accessors} against a real {@code .r} file, need multipart framing (T1.4)
 * to pull part 0 / part 1 out of a version or relation file first — deferred there.
 */
class ModelTest {

  private static final String SUBJECT_SELECTION_DESCRIPTOR =
      "{\"uri\":\"aclSubjectModel:/org_smartbit4all_api_org_bean_SubjectSelection/2026/9/2/13/43/45ad7bb3-618b-4460-9299-7c84816bea5c\",\"currentVersion\":{\"serialNoData\":3,\"serialNoRelation\":null,\"transactionId\":\"047d9c24-61d2-43f0-a080-e0e3b6232ae2\",\"createdAt\":\"2026-10-16T12:21:00.4558573+02:00\",\"createdByUri\":null,\"createdBy\":\"N/A\",\"operation\":null,\"rebasedFromUri\":null,\"commonAncestorUri\":null,\"mergedWithUri\":null,\"aspects\":{},\"encryptionRule\":\"none\"},\"className\":\"org.smartbit4all.api.org.bean.SubjectSelection\",\"pendingVersion\":null,\"deleted\":false,\"strategy\":\"INDEXED\",\"indexSize\":256,\"objectStreams\":[],\"encryptionRule\":\"none\",\"transactionUri\":null}";

  private static final String ACCOUNT_TICKET_DESCRIPTOR =
      "{\"uri\":\"account-ticket:/hu_it4all_eji_fiok_domain_model_AccountTicket/2025/12/19/16/18/5baf7e92-4abe-414b-81dd-e8018f1a99c5\",\"currentVersion\":{\"serialNoData\":2,\"serialNoRelation\":null,\"transactionId\":\"80c325d2-bef6-43c7-a5ef-af3d404b0f9b\",\"createdAt\":\"2025-12-19T16:18:20.7656935+01:00\",\"createdByUri\":null,\"createdBy\":\"N/A\",\"operation\":null,\"rebasedFromUri\":null,\"commonAncestorUri\":null,\"mergedWithUri\":null,\"aspects\":{}},\"className\":\"hu.it4all.eji.fiok.domain.model.AccountTicket\",\"pendingVersion\":null,\"deleted\":false,\"strategy\":\"CLASSIC\",\"indexSize\":null,\"objectStreams\":[]}";

  private static byte[] bytes(final String text) {
    return text.getBytes(StandardCharsets.UTF_8);
  }

  private static <T> T ok(final EngineResult<T> result) {
    if (result instanceof EngineResult.Ok<T> ok) {
      return ok.value();
    }
    throw new AssertionError("expected Ok, got " + result);
  }

  @Test
  @DisplayName("Every fixture descriptor round-trips byte-for-byte")
  void fixtureDescriptorsRoundTripByteForByte() {
    for (final String text : new String[] {SUBJECT_SELECTION_DESCRIPTOR, ACCOUNT_TICKET_DESCRIPTOR}) {
      final ObjectData data = ok(ObjectData.parse(bytes(text)));
      assertThat(new String(data.toBytes(), StandardCharsets.UTF_8)).isEqualTo(text);
    }
  }

  @Test
  @DisplayName("Descriptor accessors read the SubjectSelection fixture correctly")
  void descriptorAccessors() {
    final ObjectData data = ok(ObjectData.parse(bytes(SUBJECT_SELECTION_DESCRIPTOR)));
    assertThat(ok(data.strategy())).isEqualTo(Strategy.INDEXED);
    assertThat(ok(data.indexSize())).hasValue(256);
    assertThat(ok(data.className())).hasValue("org.smartbit4all.api.org.bean.SubjectSelection");
    assertThat(ok(data.deleted())).isFalse();
    assertThat(ok(data.encryptionRule())).isEqualTo("none");
    assertThat(ok(data.transactionUri())).isEmpty();

    final ObjectVersion version = ok(data.currentVersion()).orElseThrow();
    assertThat(ok(version.serialNoData())).hasValue(3L);
    assertThat(ok(version.serialNoRelation())).isEmpty();
    assertThat(ok(version.createdAt())).hasValue("2026-10-16T12:21:00.4558573+02:00");
    assertThat(ok(version.createdBy())).hasValue("N/A");
  }

  @Test
  @DisplayName("An old descriptor without the newer fields still reads correctly, defaults applied")
  void oldDescriptorWithoutNewerFields() {
    final ObjectData data = ok(ObjectData.parse(bytes(ACCOUNT_TICKET_DESCRIPTOR)));
    assertThat(ok(data.strategy())).isEqualTo(Strategy.CLASSIC);
    assertThat(ok(data.indexSize())).isEmpty();
    assertThat(ok(data.encryptionRule())).isEqualTo("none");
    assertThat(data.raw()).doesNotContainKey("transactionUri");

    final ObjectVersion version = ok(data.currentVersion()).orElseThrow();
    assertThat(ok(version.encryptionRule())).isEqualTo("none");
    assertThat(ok(version.serialNoData())).hasValue(2L);
  }

  @Test
  @DisplayName("Building a descriptor from scratch gives the exact platform bytes")
  void buildingADescriptorGivesThePlatformBytes() {
    final ObjectVersion version = ObjectVersion.of(
        "047d9c24-61d2-43f0-a080-e0e3b6232ae2",
        "2026-10-16T12:21:00.4558573+02:00",
        "N/A");
    version.setSerialNoData(3L);
    final ObjectData data = ObjectData.of(
        "aclSubjectModel:/org_smartbit4all_api_org_bean_SubjectSelection/2026/9/2/13/43/45ad7bb3-618b-4460-9299-7c84816bea5c",
        "org.smartbit4all.api.org.bean.SubjectSelection",
        Strategy.INDEXED,
        256);
    data.setCurrentVersion(version);
    assertThat(new String(data.toBytes(), StandardCharsets.UTF_8)).isEqualTo(SUBJECT_SELECTION_DESCRIPTOR);
  }

  @Test
  @DisplayName("Modifying a descriptor keeps unknown fields and field order")
  void modifyingKeepsUnknownFieldsAndOrder() {
    final String text =
        "{\"uri\":\"s:/a\",\"futureField\":{\"x\":[1,2]},\"currentVersion\":{\"serialNoData\":1,\"extra\":true,\"serialNoRelation\":null},\"strategy\":\"CLASSIC\"}";
    final ObjectData data = ok(ObjectData.parse(bytes(text)));
    final ObjectVersion version = ok(data.currentVersion()).orElseThrow();
    version.setSerialNoData(2L);
    version.setSerialNoRelation(0L);
    data.setCurrentVersion(version);

    final String expected =
        "{\"uri\":\"s:/a\",\"futureField\":{\"x\":[1,2]},\"currentVersion\":{\"serialNoData\":2,\"extra\":true,\"serialNoRelation\":0},\"strategy\":\"CLASSIC\"}";
    assertThat(new String(data.toBytes(), StandardCharsets.UTF_8)).isEqualTo(expected);
  }

  @Test
  @DisplayName("Rejects documents and fields of the wrong shape")
  void rejectsWrongShapes() {
    assertThat(ObjectData.parse(bytes("[]"))).isInstanceOf(EngineResult.Err.class);
    assertThat(ObjectData.parse(bytes(""))).isInstanceOf(EngineResult.Err.class);
    assertThat(ObjectData.parse(bytes("{"))).isInstanceOf(EngineResult.Err.class);

    final ObjectData data = ok(ObjectData.parse(
        bytes("{\"strategy\":\"WEIRD\",\"indexSize\":-1,\"deleted\":\"yes\",\"currentVersion\":3}")));
    assertThat(data.strategy()).isInstanceOf(EngineResult.Err.class);
    assertThat(data.indexSize()).isInstanceOf(EngineResult.Err.class);
    assertThat(data.deleted()).isInstanceOf(EngineResult.Err.class);
    assertThat(data.currentVersion()).isInstanceOf(EngineResult.Err.class);

    final ObjectVersion version = ok(ObjectVersion.parse(bytes("{\"serialNoData\":\"1\"}")));
    assertThat(version.serialNoData()).isInstanceOf(EngineResult.Err.class);
  }

  @Test
  @DisplayName("Relation data accessors read references/collections")
  void relationDataAccessors() {
    final String text =
        "{\"uri\":\"org:/org_smartbit4all_api_storage_bean_StorageSettings/settings#relations\",\"references\":{\"owner\":{\"uri\":\"org:/x/1\"}},\"collections\":{}}";
    final RelationData relations = ok(RelationData.parse(bytes(text)));
    assertThat(ok(relations.uri())).hasValueSatisfying(uri -> assertThat(uri).endsWith("#relations"));
    final Optional<Map<String, Object>> references = ok(relations.references());
    final Optional<Map<String, Object>> collections = ok(relations.collections());
    assertThat(references.isPresent() || collections.isPresent()).isTrue();
  }
}

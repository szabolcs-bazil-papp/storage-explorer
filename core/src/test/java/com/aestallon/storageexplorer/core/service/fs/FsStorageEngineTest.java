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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.aestallon.storageexplorer.core.model.EntryUri;
import com.aestallon.storageexplorer.core.service.engine.EngineResult;
import com.aestallon.storageexplorer.core.service.engine.StorageEngineError;

/**
 * Mirrors {@code sb4a-fs}'s own {@code store} module test suite (its read-path half)
 * ({@code ../sb4a-fs/crates/sb4a-fs/src/store.rs}).
 */
class FsStorageEngineTest {

  private static final Path FIXTURES = Path.of("..", "..", "sb4a-fs", "crates", "sb4a-fs", "tests",
      "fixtures", "dev-fs");

  private static final String CLASSIC =
      "account-ticket:/hu_it4all_eji_fiok_domain_model_AccountTicket/2025/12/19/16/18/5baf7e92-4abe-414b-81dd-e8018f1a99c5";
  private static final String SETTINGS = "org:/org_smartbit4all_api_storage_bean_StorageSettings/settings";
  private static final String INDEXED4 =
      "aclSubjectModel:/org_smartbit4all_api_org_bean_SubjectSelection/2026/9/2/13/43/45ad7bb3-618b-4460-9299-7c84816bea5c";
  private static final String API =
      "apis:/hu/it4all/customerportal/api/CustomerPortalInternalApi/hu.it4all.customerportal.api.CustomerPortalInternalApi";
  private static final String SINGLE =
      "asyncChannelRegistry-sv:/org_smartbit4all_api_invocation_bean_RuntimeAsyncChannel/2026/10/15/19/48/05df003f-46dd-4a5a-bf35-a1e34189be94-s";
  private static final String STORED_REF =
      "documentdossier-collections:/org_smartbit4all_documentdossier_domain_DocumentFile/2026/10/15/21/26/08476c48-f129-49d7-a84b-ebac11af8d8c/storedRef/DOCUMENT_STORAGE_ITEM_HISTORY_REF-s";

  private static FsStorageEngine fixtures() {
    return ok(FsStorageEngine.open(FIXTURES));
  }

  private static EntryUri uri(final String text) {
    return EntryUri.parse(text).orElseThrow();
  }

  private static <T> T ok(final EngineResult<T> result) {
    if (result instanceof EngineResult.Ok<T> ok) {
      return ok.value();
    }
    throw new AssertionError("expected Ok, got " + result);
  }

  private static void assertErr(final EngineResult<?> result, final Class<? extends StorageEngineError> kind) {
    if (!(result instanceof EngineResult.Err<?> err)) {
      throw new AssertionError("expected Err, got " + result);
    }
    assertThat(err.error()).isInstanceOf(kind);
  }

  @Test
  @DisplayName("Classic object: head and versions")
  void classicObjectHeadAndVersions() {
    final FsStorageEngine st = fixtures();
    final Head head = ok(st.head(uri(CLASSIC)));
    final ObjectData descriptor = head.descriptor().orElseThrow();
    assertThat(ok(descriptor.strategy())).isEqualTo(Strategy.CLASSIC);
    assertThat(head.pendingTransaction()).isFalse();

    final Entity latest = ok(st.read(uri(CLASSIC)));
    assertThat(latest.serial()).hasValue(2L);
    assertThat(latest).isEqualTo(ok(st.read(uri(CLASSIC + ".v2"))));
    assertThat(latest.payload()[0]).isEqualTo((byte) '{');
    assertThat(latest.encryptionRule()).isEqualTo("none");

    final Entity first = ok(st.readVersion(uri(CLASSIC), 0));
    assertThat(first.serial()).hasValue(0L);
    assertThat(ok(first.version().orElseThrow().serialNoData())).hasValue(0L);
    assertThat(first.payload()).isNotEqualTo(latest.payload());

    // above the head: not found, whatever is on disk
    assertErr(st.read(uri(CLASSIC + ".v3")), StorageEngineError.NotFound.class);
  }

  @Test
  @DisplayName("History lists every data version")
  void historyListsEveryDataVersion() {
    final FsStorageEngine st = fixtures();
    final List<OptionalLong> serials = new ArrayList<>();
    final FsStorageEngine.History history = ok(st.history(uri(CLASSIC)));
    while (history.hasNext()) {
      serials.add(ok(history.next()).serial());
    }
    assertThat(serials).containsExactly(OptionalLong.of(0), OptionalLong.of(1), OptionalLong.of(2));

    int count = 0;
    final FsStorageEngine.History indexedHistory = ok(st.history(uri(INDEXED4)));
    while (indexedHistory.hasNext()) {
      indexedHistory.next();
      count++;
    }
    assertThat(count).isEqualTo(4);

    assertThat(ok(st.history(uri(API))).remaining()).isEqualTo(1);
    assertErr(st.history(uri(SINGLE)), StorageEngineError.Unsupported.class);
  }

  @Test
  @DisplayName("Indexed objects")
  void indexedObjects() {
    final FsStorageEngine st = fixtures();
    for (long serial = 0; serial < 4; serial++) {
      final Entity e = ok(st.readVersion(uri(INDEXED4), serial));
      assertThat(e.serial()).hasValue(serial);
      assertThat(ok(e.version().orElseThrow().serialNoData())).hasValue(serial);
      assertThat(e.payload()[0]).isEqualTo((byte) '{');
    }
    assertErr(st.readVersion(uri(INDEXED4), 4), StorageEngineError.NotFound.class);

    final Entity api = ok(st.read(uri(API)));
    assertThat(api.serial()).hasValue(0L);
    assertThat(api.payload()[0]).isEqualTo((byte) '{');
  }

  @Test
  @DisplayName("Relations of the head and of older versions")
  void relationsOfTheHeadAndOfOlderVersions() {
    final FsStorageEngine st = fixtures();
    final Relations head = ok(st.relations(uri(SETTINGS))).orElseThrow();
    assertThat(head.serial()).isEqualTo(4);
    assertThat(ok(head.version().serialNoRelation())).hasValue(4L);
    assertThat(ok(head.data().uri()).orElseThrow()).endsWith("#relations");
    for (long serial = 0; serial <= 4; serial++) {
      final Relations r = ok(st.relationsAt(uri(SETTINGS), serial));
      assertThat(ok(r.version().serialNoRelation())).hasValue(serial);
    }
    assertErr(st.relationsAt(uri(SETTINGS), 5), StorageEngineError.NotFound.class);
    // objects without relations
    assertThat(ok(st.relations(uri(CLASSIC)))).isEmpty();
    assertThat(ok(st.relations(uri(SINGLE)))).isEmpty();
    // data serial 2 and relation serial 4 live side by side
    assertThat(ok(st.read(uri(SETTINGS))).serial()).hasValue(2L);
  }

  @Test
  @DisplayName("Single-version object")
  void singleVersionObject() {
    final FsStorageEngine st = fixtures();
    final Entity e = ok(st.read(uri(SINGLE)));
    assertThat(e.serial()).isEmpty();
    assertThat(new String(e.payload(), StandardCharsets.UTF_8)).startsWith("{\"uri\":\"asyncChannelRegistry-sv:");
    assertThat(ok(st.head(uri(SINGLE))).descriptor()).isPresent();
    assertErr(st.read(uri(SINGLE + ".v1")), StorageEngineError.Unsupported.class);
  }

  @Test
  @DisplayName("Missing objects and roots")
  void missingObjectsAndRoots() {
    final FsStorageEngine st = fixtures();
    assertThat(st.exists(uri("org:/nothing/here"))).isFalse();
    assertThat(st.exists(uri(SETTINGS))).isTrue();
    assertErr(st.head(uri("org:/nothing/here")), StorageEngineError.NotFound.class);
    assertErr(st.read(uri("org:/nothing/here")), StorageEngineError.NotFound.class);
    assertThat(FsStorageEngine.open(Path.of("/definitely/not/a/folder"))).isInstanceOf(EngineResult.Err.class);
  }

  @Test
  @DisplayName("pathOf is deterministic from the address alone, and opening via FsStorageLocation works")
  void pathOfAndFsStorageLocationOpen() {
    final var location = new com.aestallon.storageexplorer.core.model.instance.dto.FsStorageLocation()
        .path(FIXTURES);
    final FsStorageEngine st = ok(FsStorageEngine.open(location));

    assertThat(ok(st.head(uri(SETTINGS))).descriptor()).isPresent();
    assertThat(st.pathOf(uri(SETTINGS)))
        .contains(FIXTURES.resolve("org/org_smartbit4all_api_storage_bean_StorageSettings/settings.o"));
    // deterministic from the address alone - present even for an object that doesn't exist, and
    // the same regardless of which version of the URI is asked about:
    assertThat(st.pathOf(uri("org:/nothing/here"))).isPresent();
    assertThat(st.pathOf(uri(CLASSIC))).isEqualTo(st.pathOf(uri(CLASSIC + ".v0")));
  }

  /** A storage in a temp folder with hand-made files. */
  private static final class Scratch {
    private final Path dir;

    Scratch(final Path dir) {
      this.dir = dir;
    }

    FsStorageEngine storage() {
      return ok(FsStorageEngine.open(dir));
    }

    void write(final String relative, final byte[] bytes) {
      final Path path = dir.resolve(relative);
      try {
        Files.createDirectories(path.getParent());
        Files.write(path, bytes);
      } catch (final IOException e) {
        throw new UncheckedIOException(e);
      }
    }
  }

  private static ObjectData descriptor(final Long serialData, final String rule) {
    final ObjectVersion version = ObjectVersion.of("tx", "2026-01-01T00:00:00Z", "test");
    version.setSerialNoData(serialData);
    version.setEncryptionRule(rule);
    final ObjectData data = ObjectData.of("s:/a/b", "x.Y", Strategy.CLASSIC, null);
    data.setCurrentVersion(version);
    return data;
  }

  @Test
  @DisplayName("Encrypted payload needs a key")
  void encryptedPayloadNeedsAKey(@TempDir final Path dir) {
    final Scratch scratch = new Scratch(dir);
    scratch.write("s/a/b.o", descriptor(0L, "aesRule").toBytes());
    final ObjectVersion version = ObjectVersion.of("tx", "2026-01-01T00:00:00Z", "test");
    version.setSerialNoData(0L);
    version.setEncryptionRule("aesRule");
    final byte[] ciphertext = {0x0c, 'C', 'I', 'P', 'H', 'E', 'R', 'T', 'E', 'X', 'T'};
    scratch.write("s/a/b/00", Multipart.serialize(List.of(version.toBytes(), ciphertext)));

    final FsStorageEngine st = scratch.storage();
    final EngineResult<Entity> read = st.read(uri("s:/a/b"));
    assertErr(read, StorageEngineError.MissingKey.class);
    assertThat(((StorageEngineError.MissingKey) ((EngineResult.Err<Entity>) read).error()).rule())
        .isEqualTo("aesRule");

    final Entity stored = ok(st.readStored(uri("s:/a/b")));
    assertThat(stored.encryptionRule()).isEqualTo("aesRule");
    assertThat(stored.payload()).isEqualTo(ciphertext);
  }

  @Test
  @DisplayName("A pending transaction file is reported and ignored")
  void pendingTransactionFileIsReportedAndIgnored(@TempDir final Path dir) {
    final Scratch scratch = new Scratch(dir);
    scratch.write("s/a/b.o", descriptor(0L, "none").toBytes());
    scratch.write("s/a/b.t", "whatever".getBytes(StandardCharsets.UTF_8));
    final Head head = ok(scratch.storage().head(uri("s:/a/b")));
    assertThat(head.pendingTransaction()).isTrue();
  }

  @Test
  @DisplayName("An object without a data version")
  void objectWithoutDataVersion(@TempDir final Path dir) {
    final Scratch scratch = new Scratch(dir);
    scratch.write("s/a/b.o", descriptor(null, "none").toBytes());
    final FsStorageEngine st = scratch.storage();
    assertErr(st.read(uri("s:/a/b")), StorageEngineError.NoData.class);
    int count = 0;
    final FsStorageEngine.History history = ok(st.history(uri("s:/a/b")));
    while (history.hasNext()) {
      history.next();
      count++;
    }
    assertThat(count).isEqualTo(0);
  }

  @Test
  @DisplayName("Damaged files are reported with their path")
  void damagedFilesAreReportedWithTheirPath(@TempDir final Path dir) {
    final Scratch scratch = new Scratch(dir);
    scratch.write("s/a/b.o", "{not json".getBytes(StandardCharsets.UTF_8));
    assertErr(scratch.storage().head(uri("s:/a/b")), StorageEngineError.Format.class);

    scratch.write("s/a/c.o", descriptor(0L, "none").toBytes());
    scratch.write("s/a/c/00", Multipart.serialize(List.of("{}".getBytes(StandardCharsets.UTF_8))));
    assertErr(scratch.storage().read(uri("s:/a/c")), StorageEngineError.Format.class);

    scratch.write("s/a/d.o", descriptor(0L, "none").toBytes());
    assertErr(scratch.storage().read(uri("s:/a/d")), StorageEngineError.NotFound.class);
  }

  @Test
  @DisplayName("A single-version object with an empty descriptor part")
  void singleVersionWithEmptyDescriptorPart(@TempDir final Path dir) {
    final Scratch scratch = new Scratch(dir);
    scratch.write("s/a/x-s.o", Multipart.serialize(
        List.of(new byte[0], "{\"k\":1}".getBytes(StandardCharsets.UTF_8))));
    final FsStorageEngine st = scratch.storage();
    final Entity e = ok(st.read(uri("s:/a/x-s")));
    assertThat(e.payload()).isEqualTo("{\"k\":1}".getBytes(StandardCharsets.UTF_8));
    assertThat(e.version()).isEmpty();
    assertThat(ok(st.head(uri("s:/a/x-s"))).descriptor()).isEmpty();
  }

  @Test
  @DisplayName("Object streams of a real object")
  void objectStreamsOfARealObject() {
    final FsStorageEngine st = fixtures();
    final List<StreamRef> streams = ok(st.streams(uri(STORED_REF)));
    assertThat(streams).containsExactly(new StreamRef("events", 626));

    final StreamScan events = ok(st.readStream(uri(STORED_REF), "events"));
    assertThat(events.entries()).isNotEmpty();
    assertThat(events.end()).isEqualTo(626);
    assertThat(events.trailingBytes()).isEqualTo(0);
    assertThat(events.entries()).allSatisfy(e -> assertThat(e.data()[0]).isEqualTo((byte) '{'));

    assertErr(st.readStream(uri(STORED_REF), "nothing"), StorageEngineError.NotFound.class);
    // objects without streams
    assertThat(ok(st.streams(uri(CLASSIC)))).isEmpty();
    assertThat(ok(st.streams(uri(SINGLE)))).isEmpty();
  }

  @Test
  @DisplayName("A stream that was never written is empty")
  void aStreamThatWasNeverWrittenIsEmpty(@TempDir final Path dir) {
    final Scratch scratch = new Scratch(dir);
    final ObjectData data = descriptor(0L, "none");
    data.raw().put("objectStreams", List.of(java.util.Map.of("name", "later", "headPosition", 0)));
    scratch.write("s/a/b.o", data.toBytes());
    final FsStorageEngine st = scratch.storage();
    final StreamScan scan = ok(st.readStream(uri("s:/a/b"), "later"));
    assertThat(scan.entries()).isEmpty();

    // a head position past the end of the file is damage, not an empty stream
    data.raw().put("objectStreams", List.of(java.util.Map.of("name", "later", "headPosition", 10)));
    scratch.write("s/a/b.o", data.toBytes());
    scratch.write("s/a/b/later.os", new byte[0]);
    assertErr(st.readStream(uri("s:/a/b"), "later"), StorageEngineError.Format.class);
  }

  @Test
  @DisplayName("Table data of a real object")
  void tableDataOfARealObject() {
    final FsStorageEngine st = fixtures();
    final TableData table = ok(st.readTableData(
        uri("tabledatacontents:/2025/12/19/16/10/43/51fa609a-9a85-40ea-abf3-32d9333bc5d6")));
    assertThat(table.entity()).isPresent();
    assertThat(table.entity().get()).startsWith("entity://account-ticket/");
    assertThat(table.columns()).hasSize(8);
    assertThat(table.columns().get(0)).endsWith("#subject");
    assertErr(st.readTableData(uri("tabledatacontents:/nothing/here")), StorageEngineError.NotFound.class);
  }
}

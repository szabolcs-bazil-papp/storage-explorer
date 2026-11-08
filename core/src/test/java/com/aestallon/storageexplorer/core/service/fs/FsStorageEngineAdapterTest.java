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
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.aestallon.storageexplorer.core.model.EntryUri;
import com.aestallon.storageexplorer.core.model.loading.IndexingTarget;
import com.aestallon.storageexplorer.core.service.engine.ContentHandle;
import com.aestallon.storageexplorer.core.service.engine.EngineResult;
import com.aestallon.storageexplorer.core.model.EntryKind;
import com.aestallon.storageexplorer.core.service.engine.EntryMetadata;
import com.aestallon.storageexplorer.core.service.engine.LoadedContent;
import com.aestallon.storageexplorer.core.service.engine.StorageEngine;

/** {@link FsStorageEngineAdapter}, exercised through the shared {@link StorageEngine} contract
 *  against the real fixture corpus — the same data Milestone 1's own tests already use. */
class FsStorageEngineAdapterTest {

  private static final Path FIXTURES = Path.of("..", "..", "sb4a-fs", "crates", "sb4a-fs", "tests",
      "fixtures", "dev-fs");

  private static final String SETTINGS = "org:/org_smartbit4all_api_storage_bean_StorageSettings/settings";

  private static StorageEngine fixtures() {
    final EngineResult<FsStorageEngine> result = FsStorageEngine.open(FIXTURES);
    if (result instanceof EngineResult.Ok<FsStorageEngine> ok) {
      return ok.value().asStorageEngine();
    }
    throw new AssertionError("expected Ok, got " + result);
  }

  private static EntryUri uri(final String text) {
    return EntryUri.parse(text).orElseThrow();
  }

  @Test
  @DisplayName("read gives a ContentHandle whose stream and materialized form agree")
  void readGivesAUsableContentHandle() throws IOException {
    final StorageEngine engine = fixtures();
    final ContentHandle handle;
    switch (engine.read(uri(SETTINGS))) {
      case EngineResult.Ok<ContentHandle> ok -> handle = ok.value();
      case EngineResult.Err<ContentHandle> err -> throw new AssertionError(err.error().toString());
    }

    final byte[] raw;
    try (InputStream in = handle.openStream()) {
      raw = in.readAllBytes();
    }
    assertThat(raw[0]).isEqualTo((byte) '{');

    final LoadedContent content = handle.materialize();
    assertThat(content).isInstanceOf(LoadedContent.JsonObject.class);
    final LoadedContent.JsonObject json = (LoadedContent.JsonObject) content;
    assertThat(json.data()).containsKey("uri");
  }

  @Test
  @DisplayName("read of a missing entry fails with the engine's own error, not a crash")
  void readOfAMissingEntryFails() {
    final StorageEngine engine = fixtures();
    assertThat(engine.read(uri("org:/nothing/here"))).isInstanceOf(EngineResult.Err.class);
  }

  @Test
  @DisplayName("head translates into the shared EntryMetadata shape")
  void headTranslatesIntoEntryMetadata() {
    final StorageEngine engine = fixtures();
    final EntryMetadata metadata;
    switch (engine.head(uri(SETTINGS))) {
      case EngineResult.Ok<EntryMetadata> ok -> metadata = ok.value();
      case EngineResult.Err<EntryMetadata> err -> throw new AssertionError(err.error().toString());
    }
    assertThat(metadata.uri()).isEqualTo(uri(SETTINGS));
    assertThat(metadata.qualifiedName()).isEqualTo("org.smartbit4all.api.storage.bean.StorageSettings");
    assertThat(metadata.versionNr()).isEqualTo(2L);
    assertThat(metadata.createdAt()).isNotNull();
  }

  @Test
  @DisplayName("list translates FS kinds into the shared EntryKind")
  void listTranslatesKinds() {
    final StorageEngine engine = fixtures();
    final List<StorageEngine.Listed> listed;
    try (var stream = engine.list(new IndexingTarget(Set.of("tabledatacontents"), Set.of()))) {
      listed = stream.toList();
    }
    assertThat(listed).hasSize(1);
    assertThat(listed.get(0).kind()).isEqualTo(EntryKind.TABLE_DATA);
  }

  @Test
  @DisplayName("exists and pathOf delegate straight through")
  void existsAndPathOfDelegateThrough() {
    final StorageEngine engine = fixtures();
    assertThat(engine.exists(uri(SETTINGS))).isTrue();
    assertThat(engine.exists(uri("org:/nothing/here"))).isFalse();
    assertThat(engine.pathOf(uri(SETTINGS))).isPresent();
  }

  /**
   * Proves the laziness this test's own class javadoc now needs to call out: {@code read(uri)}
   * only ever did the cheap {@code exists(uri)} check, so it succeeds even for an entry it can
   * never actually serve content for - the engine has no key for this {@code encryptionRule} -
   * and the real failure only surfaces once a caller tries to use the handle. Before this fix,
   * {@code read(uri)} itself called {@code engine.read(uri)} eagerly and would have failed right
   * here with {@code StorageEngineError.MissingKey}, synchronously.
   */
  @Test
  @DisplayName("read defers the actual content read: an unreadable entry's handle still comes back Ok")
  void readDefersTheActualContentRead(@TempDir final Path dir) throws IOException {
    writeEncryptedSingleVersionEntry(dir);
    final StorageEngine engine = ((EngineResult.Ok<FsStorageEngine>) FsStorageEngine.open(dir))
        .value()
        .asStorageEngine();

    final ContentHandle handle;
    switch (engine.read(uri("s:/a/b"))) {
      case EngineResult.Ok<ContentHandle> ok -> handle = ok.value();
      case EngineResult.Err<ContentHandle> err ->
          throw new AssertionError("expected a deferred Ok handle, got " + err.error());
    }

    // the handle exists, but nothing has actually been read from disk yet; only now, on the
    // first real access, does the missing-key failure surface - as an IOException from the
    // stream, exactly where callers already catch one:
    assertThatThrownBy(() -> {
      try (InputStream in = handle.openStream()) {
        in.readAllBytes();
      }
    }).isInstanceOf(IOException.class).hasMessageContaining("aesRule");

    // materialize() has no failure channel (T2.1) - the same deferred failure renders as empty,
    // not a thrown exception, on this path:
    final LoadedContent content = handle.materialize();
    assertThat(content).isInstanceOf(LoadedContent.JsonObject.class);
    assertThat(((LoadedContent.JsonObject) content).data()).isEmpty();
  }

  private static void writeEncryptedSingleVersionEntry(final Path dir) {
    final ObjectVersion descriptorVersion = ObjectVersion.of("tx", "2026-01-01T00:00:00Z", "test");
    descriptorVersion.setSerialNoData(0L);
    descriptorVersion.setEncryptionRule("aesRule");
    final ObjectData descriptor = ObjectData.of("s:/a/b", "x.Y", Strategy.CLASSIC, null);
    descriptor.setCurrentVersion(descriptorVersion);
    write(dir, "s/a/b.o", descriptor.toBytes());

    final ObjectVersion version = ObjectVersion.of("tx", "2026-01-01T00:00:00Z", "test");
    version.setSerialNoData(0L);
    version.setEncryptionRule("aesRule");
    final byte[] ciphertext = { 0x0c, 'C', 'I', 'P', 'H', 'E', 'R', 'T', 'E', 'X', 'T' };
    write(dir, "s/a/b/00", Multipart.serialize(List.of(version.toBytes(), ciphertext)));
  }

  private static void write(final Path dir, final String relative, final byte[] bytes) {
    final Path path = dir.resolve(relative);
    try {
      Files.createDirectories(path.getParent());
      Files.write(path, bytes);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}

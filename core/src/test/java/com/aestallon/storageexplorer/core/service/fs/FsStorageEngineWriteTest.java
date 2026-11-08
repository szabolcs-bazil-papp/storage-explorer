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
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.stream.Stream;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.aestallon.storageexplorer.core.model.EntryUri;
import com.aestallon.storageexplorer.core.service.engine.EngineResult;
import com.aestallon.storageexplorer.core.service.engine.StorageEngineError;

/**
 * Mirrors {@code sb4a-fs}'s own {@code write} module test suite
 * ({@code ../sb4a-fs/crates/sb4a-fs/src/write.rs}).
 */
class FsStorageEngineWriteTest {

  private static final Path FIXTURES = Path.of("..", "..", "sb4a-fs", "crates", "sb4a-fs", "tests",
      "fixtures", "dev-fs");

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

  private static byte[] payload(final long n) {
    return ("{\"n\":" + n + "}").getBytes(StandardCharsets.UTF_8);
  }

  private static byte[] read(final FsStorageEngine st, final EntryUri u, final Long serial) {
    final EntryUri target = (serial == null) ? u : u.withVersion(serial);
    return ok(st.readStored(target)).payload();
  }

  /** A storage in a temp folder with a short lock timeout, like the Rust suite's {@code scratch}. */
  private static FsStorageEngine scratch(final Path dir) {
    final FsStorageEngine opened = ok(FsStorageEngine.open(dir));
    return opened.withWriteOptions(new WriteOptions(new LockOptions(false, Duration.ZERO),
        WriteOptions.DEFAULT_CREATED_BY, java.util.UUID.randomUUID().toString()));
  }

  @Test
  @DisplayName("create then new versions (classic)")
  void createThenNewVersionsClassic(@TempDir final Path dir) {
    final FsStorageEngine st = scratch(dir);
    final EntryUri u = uri("s:/alias/2026/1/1/a1");
    final Committed c = ok(st.create(u, "x.Thing", Strategy.CLASSIC, null, payload(0)));
    assertThat(c.serial()).hasValue(0L);
    assertThat(dir.resolve("s/alias/2026/1/1/a1.o")).isRegularFile();
    assertThat(dir.resolve("s/alias/2026/1/1/a1/00")).isRegularFile();
    assertThat(readBytes(dir.resolve("s/alias/2026/1/1/a1.l"))).isEmpty();

    for (long n = 1; n <= 3; n++) {
      assertThat(ok(st.newVersion(u, payload(n))).serial()).hasValue(n);
    }
    assertThat(read(st, u, null)).isEqualTo(payload(3));
    assertThat(read(st, u, 1L)).isEqualTo(payload(1));

    final List<OptionalLong> serials = new java.util.ArrayList<>();
    final FsStorageEngine.History history = ok(st.history(u));
    while (history.hasNext()) {
      serials.add(ok(history.next()).serial());
    }
    assertThat(serials).containsExactly(
        OptionalLong.of(0), OptionalLong.of(1), OptionalLong.of(2), OptionalLong.of(3));

    final ObjectData head = ok(st.head(u)).descriptor().orElseThrow();
    assertThat(ok(head.className())).hasValue("x.Thing");
    assertThat(ok(head.strategy())).isEqualTo(Strategy.CLASSIC);
    assertThat(ok(ok(head.currentVersion()).orElseThrow().serialNoData())).hasValue(3L);

    try (Stream<Path> entries = Files.list(dir.resolve("s/alias/2026/1/1"))) {
      assertThat(entries.count()).isEqualTo(3);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Test
  @DisplayName("create refuses an existing object")
  void createRefusesAnExistingObject(@TempDir final Path dir) {
    final FsStorageEngine st = scratch(dir);
    final EntryUri u = uri("s:/a/b");
    ok(st.create(u, "x.T", Strategy.CLASSIC, null, "{}".getBytes(StandardCharsets.UTF_8)));
    assertErr(st.create(u, "x.T", Strategy.CLASSIC, null, "{}".getBytes(StandardCharsets.UTF_8)),
        StorageEngineError.AlreadyExists.class);
    assertErr(st.newVersion(uri("s:/a/missing"), "{}".getBytes(StandardCharsets.UTF_8)),
        StorageEngineError.NotFound.class);
    assertThat(dir.resolve("s/a/missing.l")).doesNotExist();
  }

  @Test
  @DisplayName("indexed versions roll over into the next file")
  void indexedVersionsRollOverIntoTheNextFile(@TempDir final Path dir) {
    final FsStorageEngine st = scratch(dir);
    final EntryUri u = uri("s:/a/idx");
    ok(st.create(u, "x.T", Strategy.INDEXED, 4, payload(0)));
    for (long n = 1; n < 10; n++) {
      ok(st.newVersion(u, payload(n)));
    }
    for (final String file : new String[] {"00.iv", "01.iv", "02.iv"}) {
      assertThat(dir.resolve("s/a/idx").resolve(file)).isRegularFile();
    }
    assertThat(dir.resolve("s/a/idx/03.iv")).doesNotExist();
    for (long n = 0; n < 10; n++) {
      assertThat(read(st, u, n)).isEqualTo(payload(n));
    }
    final ObjectData head = ok(st.head(u)).descriptor().orElseThrow();
    assertThat(ok(head.indexSize())).hasValue(4);
  }

  @Test
  @DisplayName("indexed default index size is 256")
  void indexedDefaultIndexSizeIs256(@TempDir final Path dir) throws IOException {
    final FsStorageEngine st = scratch(dir);
    final EntryUri u = uri("s:/a/idx");
    ok(st.create(u, "x.T", Strategy.INDEXED, null, "{}".getBytes(StandardCharsets.UTF_8)));
    assertThat(ok(ok(st.head(u)).descriptor().orElseThrow().indexSize())).hasValue(256);
    assertThat(Files.size(dir.resolve("s/a/idx/00.iv"))).isGreaterThan(4096);
  }

  @Test
  @DisplayName("classic versions nest after 255")
  void classicVersionsNestAfter255(@TempDir final Path dir) {
    final FsStorageEngine st = scratch(dir);
    final EntryUri u = uri("s:/a/many");
    ok(st.create(u, "x.T", Strategy.CLASSIC, null, payload(0)));
    for (long n = 1; n <= 257; n++) {
      ok(st.newVersion(u, payload(n)));
    }
    final Path base = dir.resolve("s/a/many");
    assertThat(base.resolve("FF")).isRegularFile();
    assertThat(base.resolve("1/00")).isRegularFile();
    assertThat(base.resolve("1/01")).isRegularFile();
    assertThat(read(st, u, 256L)).isEqualTo(payload(256));
    assertThat(read(st, u, null)).isEqualTo(payload(257));
  }

  @Test
  @DisplayName("relations travel with every new version")
  void relationsTravelWithEveryNewVersion(@TempDir final Path dir) {
    final FsStorageEngine st = scratch(dir);
    final EntryUri u = uri("s:/a/rel");
    ok(st.create(u, "x.T", Strategy.CLASSIC, null, payload(0)));

    final byte[] relations = ("{\"uri\":\"s:/a/rel#relations\",\"references\":{\"owner\":"
        + "{\"uri\":\"s:/a/other\",\"referenceId\":null,\"expirationTime\":null}},\"collections\":{}}")
        .getBytes(StandardCharsets.UTF_8);
    final ObjectVersion version = ok(ok(st.head(u)).descriptor().orElseThrow().currentVersion())
        .orElseThrow();
    version.setSerialNoRelation(0L);
    ok(Multipart.writeFile(st.root().relationFile(u, 0), List.of(version.toBytes(), relations)));
    final ObjectData descriptor = ok(st.head(u)).descriptor().orElseThrow();
    descriptor.setCurrentVersion(version);
    ok(FsUtil.writeFileAtomic(st.root().objectFile(u), descriptor.toBytes()));

    ok(st.newVersion(u, payload(1)));
    ok(st.newVersion(u, payload(2)));

    final Relations head = ok(st.relations(u)).orElseThrow();
    assertThat(head.serial()).isEqualTo(2);
    assertThat(head.data().toBytes()).isEqualTo(relations);
    assertThat(ok(head.version().serialNoData())).hasValue(2L);
    assertThat(ok(ok(st.relationsAt(u, 0)).version().serialNoData())).hasValue(0L);
    assertThat(ok(ok(st.relationsAt(u, 1)).version().serialNoData())).hasValue(1L);
    final ObjectVersion newVersion = ok(ok(st.head(u)).descriptor().orElseThrow().currentVersion())
        .orElseThrow();
    assertThat(ok(newVersion.serialNoRelation())).hasValue(2L);
  }

  @Test
  @DisplayName("aspects and unknown descriptor fields survive")
  void aspectsAndUnknownDescriptorFieldsSurvive(@TempDir final Path dir) {
    final FsStorageEngine st = scratch(dir);
    final EntryUri u = uri("s:/a/asp");
    ok(st.create(u, "x.T", Strategy.CLASSIC, null, "{}".getBytes(StandardCharsets.UTF_8)));
    final ObjectData descriptor = ok(st.head(u)).descriptor().orElseThrow();
    final ObjectVersion version = ok(descriptor.currentVersion()).orElseThrow();
    version.raw().put("aspects", java.util.Map.of("k",
        java.util.Map.of("typeQualifiedName", "t", "objectAsMap", java.util.Map.of("a", 1))));
    descriptor.setCurrentVersion(version);
    descriptor.raw().put("futureField", List.of(1, 2));
    ok(FsUtil.writeFileAtomic(st.root().objectFile(u), descriptor.toBytes()));

    ok(st.newVersion(u, "{}".getBytes(StandardCharsets.UTF_8)));
    final ObjectData after = ok(st.head(u)).descriptor().orElseThrow();
    assertThat(after.raw().get("futureField")).isEqualTo(List.of(1L, 2L));
    final ObjectVersion newVersion = ok(after.currentVersion()).orElseThrow();
    @SuppressWarnings("unchecked")
    final java.util.Map<String, Object> aspects = (java.util.Map<String, Object>) newVersion.raw().get("aspects");
    @SuppressWarnings("unchecked")
    final java.util.Map<String, Object> k = (java.util.Map<String, Object>) aspects.get("k");
    @SuppressWarnings("unchecked")
    final java.util.Map<String, Object> objectAsMap = (java.util.Map<String, Object>) k.get("objectAsMap");
    assertThat(objectAsMap.get("a")).isEqualTo(1L);
    assertThat(ok(newVersion.serialNoData())).hasValue(1L);
  }

  @Test
  @DisplayName("amend classic head and older versions")
  void amendClassicHeadAndOlderVersions(@TempDir final Path dir) throws IOException {
    final FsStorageEngine st = scratch(dir);
    final EntryUri u = uri("s:/a/am");
    ok(st.create(u, "x.T", Strategy.CLASSIC, null, payload(0)));
    ok(st.newVersion(u, payload(1)));
    ok(st.newVersion(u, payload(2)));

    final ObjectVersion recordBefore = ok(st.readVersion(u, 1)).version().orElseThrow();
    final byte[] descriptorBefore = Files.readAllBytes(st.root().objectFile(u));
    ok(st.amend(u, 1, "{\"n\":\"amended\"}".getBytes(StandardCharsets.UTF_8), AmendOptions.defaults()));
    assertThat(read(st, u, 1L)).isEqualTo("{\"n\":\"amended\"}".getBytes(StandardCharsets.UTF_8));
    assertThat(ok(st.readVersion(u, 1)).version().orElseThrow()).isEqualTo(recordBefore);
    assertThat(read(st, u, 0L)).isEqualTo(payload(0));
    assertThat(read(st, u, null)).isEqualTo(payload(2));
    assertThat(Files.readAllBytes(st.root().objectFile(u))).isEqualTo(descriptorBefore);

    ok(st.amend(u, 2, "{\"n\":\"head\"}".getBytes(StandardCharsets.UTF_8), AmendOptions.defaults()));
    assertThat(read(st, u, null)).isEqualTo("{\"n\":\"head\"}".getBytes(StandardCharsets.UTF_8));
    assertErr(st.amend(u, 3, "{}".getBytes(StandardCharsets.UTF_8), AmendOptions.defaults()),
        StorageEngineError.NotFound.class);
  }

  @Test
  @DisplayName("amend indexed leaves the other slots alone")
  void amendIndexedLeavesTheOtherSlotsAlone(@TempDir final Path dir) {
    final FsStorageEngine st = scratch(dir);
    final EntryUri u = uri("s:/a/ami");
    ok(st.create(u, "x.T", Strategy.INDEXED, 4, payload(0)));
    for (long n = 1; n < 6; n++) {
      ok(st.newVersion(u, payload(n)));
    }
    ok(st.amend(u, 2, "{\"n\":\"two\"}".getBytes(StandardCharsets.UTF_8), AmendOptions.defaults()));
    ok(st.amend(u, 5, "{\"n\":\"five\"}".getBytes(StandardCharsets.UTF_8), AmendOptions.defaults()));
    for (long n = 0; n < 6; n++) {
      final byte[] expected = (n == 2) ? "{\"n\":\"two\"}".getBytes(StandardCharsets.UTF_8)
          : (n == 5) ? "{\"n\":\"five\"}".getBytes(StandardCharsets.UTF_8)
          : payload(n);
      assertThat(read(st, u, n)).isEqualTo(expected);
    }
    final IndexedFile file = ok(IndexedFile.open(dir.resolve("s/a/ami/00.iv"), 4)).orElseThrow();
    try {
      assertThat(ok(file.deadSpace())).isGreaterThan(0L);
    } finally {
      try {
        file.close();
      } catch (final IOException e) {
        throw new UncheckedIOException(e);
      }
    }
  }

  @Test
  @DisplayName("amend touches the head unless told not to")
  void amendTouchesTheHeadUnlessToldNotTo(@TempDir final Path dir) throws IOException {
    final FsStorageEngine st = scratch(dir);
    final EntryUri u = uri("s:/a/touch");
    ok(st.create(u, "x.T", Strategy.CLASSIC, null, "{}".getBytes(StandardCharsets.UTF_8)));
    final Path head = st.root().objectFile(u);
    final Instant old = Instant.now().minus(Duration.ofHours(1));
    Files.setLastModifiedTime(head, java.nio.file.attribute.FileTime.from(old));

    ok(st.amend(u, 0, "{\"a\":1}".getBytes(StandardCharsets.UTF_8), new AmendOptions(false)));
    assertThat(Files.getLastModifiedTime(head).toInstant())
        .isBefore(Instant.now().minus(Duration.ofSeconds(3000)));
    ok(st.amend(u, 0, "{\"a\":2}".getBytes(StandardCharsets.UTF_8), AmendOptions.defaults()));
    assertThat(Files.getLastModifiedTime(head).toInstant())
        .isAfter(Instant.now().minus(Duration.ofSeconds(60)));
  }

  @Test
  @DisplayName("single-version objects get their payload replaced")
  void singleVersionObjectsGetTheirPayloadReplaced(@TempDir final Path dir) throws IOException {
    final FsStorageEngine st = scratch(dir);
    final EntryUri u = uri("sv:/a/one-s");
    final byte[] descriptor = ("{\"uri\":\"sv:/a/one-s\",\"className\":\"x.T\",\"objectStreams\":[],"
        + "\"encryptionRule\":\"none\"}").getBytes(StandardCharsets.UTF_8);
    final Path path = st.root().objectFile(u);
    ok(FsUtil.ensureParent(path));
    Files.write(path, Multipart.serialize(List.of(descriptor, "{\"v\":1}".getBytes(StandardCharsets.UTF_8))));

    ok(st.newVersion(u, "{\"v\":2}".getBytes(StandardCharsets.UTF_8)));
    assertThat(read(st, u, null)).isEqualTo("{\"v\":2}".getBytes(StandardCharsets.UTF_8));
    ok(st.amend(u, 0, "{\"v\":3}".getBytes(StandardCharsets.UTF_8), AmendOptions.defaults()));
    assertThat(read(st, u, null)).isEqualTo("{\"v\":3}".getBytes(StandardCharsets.UTF_8));
    final List<byte[]> parts = ok(Multipart.readFile(path)).orElseThrow();
    assertThat(parts.get(0)).isEqualTo(descriptor);
  }

  @Test
  @DisplayName("encrypted objects are refused until a key is given")
  void encryptedObjectsAreRefusedUntilAKeyIsGiven(@TempDir final Path dir) {
    final FsStorageEngine st = scratch(dir);
    final EntryUri u = uri("s:/a/enc");
    ok(st.create(u, "x.T", Strategy.CLASSIC, null, "{}".getBytes(StandardCharsets.UTF_8)));
    final ObjectData descriptor = ok(st.head(u)).descriptor().orElseThrow();
    final ObjectVersion version = ok(descriptor.currentVersion()).orElseThrow();
    version.setEncryptionRule("aesRule");
    descriptor.setCurrentVersion(version);
    ok(FsUtil.writeFileAtomic(st.root().objectFile(u), descriptor.toBytes()));
    ok(Multipart.writeFile(st.root().versionDir(u).resolve("00"),
        List.of(version.toBytes(), "cipher".getBytes(StandardCharsets.UTF_8))));

    final EngineResult<Committed> newVersion = st.newVersion(u, "{}".getBytes(StandardCharsets.UTF_8));
    assertErr(newVersion, StorageEngineError.MissingKey.class);
    assertThat(((StorageEngineError.MissingKey) ((EngineResult.Err<Committed>) newVersion).error()).rule())
        .isEqualTo("aesRule");
    assertErr(st.amend(u, 0, "{}".getBytes(StandardCharsets.UTF_8), AmendOptions.defaults()),
        StorageEngineError.MissingKey.class);
  }

  @Test
  @DisplayName("a pending .t file stops every write")
  void aPendingTFileStopsEveryWrite(@TempDir final Path dir) throws IOException {
    final FsStorageEngine st = scratch(dir);
    final EntryUri u = uri("s:/a/pend");
    ok(st.create(u, "x.T", Strategy.CLASSIC, null, "{}".getBytes(StandardCharsets.UTF_8)));
    Files.write(st.root().transactionFile(u), "x".getBytes(StandardCharsets.UTF_8));
    assertErr(st.newVersion(u, "{}".getBytes(StandardCharsets.UTF_8)), StorageEngineError.InDoubt.class);
    assertErr(st.amend(u, 0, "{}".getBytes(StandardCharsets.UTF_8), AmendOptions.defaults()),
        StorageEngineError.InDoubt.class);
    assertErr(st.create(uri("s:/a/pend"), "x.T", Strategy.CLASSIC, null, "{}".getBytes(StandardCharsets.UTF_8)),
        StorageEngineError.InDoubt.class);
  }

  @Test
  @DisplayName("a held or foreign lock stops writes, and force overrides a foreign record")
  void aHeldOrForeignLockStopsWritesAndForceOverridesAForeignRecord(@TempDir final Path dir) throws IOException {
    final FsStorageEngine st = scratch(dir);
    final EntryUri u = uri("s:/a/lck");
    ok(st.create(u, "x.T", Strategy.CLASSIC, null, "{}".getBytes(StandardCharsets.UTF_8)));

    // a platform node holding the logical lock: its record is in the file, no OS lock is held
    final byte[] record = ObjectLock.encodeOwner(new LockOwner("node", ""));
    Files.write(st.root().lockFile(u), record);
    assertErr(st.newVersion(u, "{}".getBytes(StandardCharsets.UTF_8)), StorageEngineError.Locked.class);
    assertThat(Files.readAllBytes(st.root().lockFile(u))).isEqualTo(record);

    final FsStorageEngine forced = ok(FsStorageEngine.open(dir)).withWriteOptions(
        new WriteOptions(new LockOptions(true, Duration.ZERO), WriteOptions.DEFAULT_CREATED_BY,
            java.util.UUID.randomUUID().toString()));
    ok(forced.newVersion(u, "{\"f\":1}".getBytes(StandardCharsets.UTF_8)));
    assertThat(Files.readAllBytes(forced.root().lockFile(u))).isEmpty();
    assertThat(read(st, u, null)).isEqualTo("{\"f\":1}".getBytes(StandardCharsets.UTF_8));

    // a lock held by this very process (another writer) is refused as well
    final ObjectLock held = ok(ObjectLock.acquire(st.root().lockFile(u), new LockOwner("other", ""),
        new LockOptions(false, Duration.ZERO)));
    try {
      assertErr(st.newVersion(u, "{}".getBytes(StandardCharsets.UTF_8)), StorageEngineError.Locked.class);
    } finally {
      held.close();
    }
  }

  /** A failure at any step before the descriptor swap leaves the head exactly as it was, releases
   *  the lock, and the next write succeeds. */
  @Test
  @DisplayName("a crash before the head moves changes nothing")
  void aCrashBeforeTheHeadMovesChangesNothing(@TempDir final Path dir) throws IOException {
    for (final Strategy strategy : Strategy.values()) {
      for (final String point : new String[] {"after-relation-file", "before-descriptor"}) {
        final Path sub = dir.resolve(strategy + "-" + point);
        Files.createDirectories(sub);
        final FsStorageEngine st = scratch(sub);
        final EntryUri u = uri("s:/a/crash");
        ok(st.create(u, "x.T", strategy, 4, payload(0)));
        ok(st.newVersion(u, payload(1)));
        final byte[] descriptorBefore = Files.readAllBytes(st.root().objectFile(u));

        FailPoint.arm(point);
        final EngineResult<Committed> failed = st.newVersion(u, payload(2));
        FailPoint.disarm();
        assertThat(failed).as(strategy + " " + point).isInstanceOf(EngineResult.Err.class);

        assertThat(Files.readAllBytes(st.root().objectFile(u))).isEqualTo(descriptorBefore);
        assertThat(read(st, u, null)).isEqualTo(payload(1));
        assertErr(st.read(u.withVersion(2)), StorageEngineError.NotFound.class);
        assertThat(Files.readAllBytes(st.root().lockFile(u))).isEmpty();

        assertThat(ok(st.newVersion(u, payload(22))).serial()).hasValue(2L);
        assertThat(read(st, u, null)).isEqualTo(payload(22));
      }
    }
  }

  @Test
  @DisplayName("new version records look like the platform's")
  void newVersionRecordsLookLikeThePlatforms(@TempDir final Path dir) {
    final FsStorageEngine st = scratch(dir);
    final EntryUri u = uri("s:/a/rec");
    ok(st.create(u, "x.T", Strategy.CLASSIC, null, "{}".getBytes(StandardCharsets.UTF_8)));
    final Committed c = ok(st.newVersion(u, "{}".getBytes(StandardCharsets.UTF_8)));
    final ObjectVersion version = c.version().orElseThrow();
    assertThat(version.raw().keySet()).containsExactly(
        "serialNoData", "serialNoRelation", "transactionId", "createdAt", "createdByUri", "createdBy",
        "operation", "rebasedFromUri", "commonAncestorUri", "mergedWithUri", "aspects", "encryptionRule");
    assertThat(ok(version.createdBy())).hasValue(WriteOptions.DEFAULT_CREATED_BY);
    assertThat(ok(version.transactionId()).orElseThrow()).hasSize(36);
    assertThat(ok(st.readVersion(u, 1)).version().orElseThrow()).isEqualTo(version);
  }

  private static void copyDir(final Path from, final Path to) {
    try {
      Files.createDirectories(to);
      try (Stream<Path> entries = Files.list(from)) {
        for (final Path entry : entries.toList()) {
          final Path target = to.resolve(entry.getFileName().toString());
          if (Files.isDirectory(entry)) {
            copyDir(entry, target);
          } else {
            Files.copy(entry, target);
          }
        }
      }
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** The same operations on copies of real platform data. */
  @Test
  @DisplayName("writes on real platform objects")
  void writesOnRealPlatformObjects(@TempDir final Path dir) {
    copyDir(FIXTURES, dir);
    final FsStorageEngine st = scratch(dir);

    // classic with relations: data 2 / relation 4 -> data 3 / relation 5
    final EntryUri settings = uri("org:/org_smartbit4all_api_storage_bean_StorageSettings/settings");
    final Relations relationsBefore = ok(st.relations(settings)).orElseThrow();
    final byte[] oldHead = read(st, settings, null);
    final Committed c = ok(st.newVersion(settings, "{\"settings\":\"new\"}".getBytes(StandardCharsets.UTF_8)));
    assertThat(c.serial()).hasValue(3L);
    assertThat(ok(c.version().orElseThrow().serialNoRelation())).hasValue(5L);
    final Relations relationsAfter = ok(st.relations(settings)).orElseThrow();
    assertThat(relationsAfter.serial()).isEqualTo(5);
    assertThat(relationsAfter.data()).isEqualTo(relationsBefore.data());
    assertThat(read(st, settings, 2L)).isEqualTo(oldHead);
    assertThat(read(st, settings, null)).isEqualTo("{\"settings\":\"new\"}".getBytes(StandardCharsets.UTF_8));
    int settingsHistory = 0;
    final FsStorageEngine.History sh = ok(st.history(settings));
    while (sh.hasNext()) {
      sh.next();
      settingsHistory++;
    }
    assertThat(settingsHistory).isEqualTo(4);

    // classic without relations: amend a middle version
    final EntryUri ticket = uri(
        "account-ticket:/hu_it4all_eji_fiok_domain_model_AccountTicket/2025/12/19/16/18/5baf7e92-4abe-414b-81dd-e8018f1a99c5");
    final ObjectVersion record = ok(st.readVersion(ticket, 1)).version().orElseThrow();
    ok(st.amend(ticket, 1, "{\"amended\":true}".getBytes(StandardCharsets.UTF_8), AmendOptions.defaults()));
    final Entity amended = ok(st.readVersion(ticket, 1));
    assertThat(amended.payload()).isEqualTo("{\"amended\":true}".getBytes(StandardCharsets.UTF_8));
    assertThat(amended.version().orElseThrow()).isEqualTo(record);
    assertThat(read(st, ticket, 2L)[0]).isEqualTo((byte) '{');

    // indexed (real .iv written by the platform): new version into the next slot, amend slot 1
    final EntryUri acl = uri(
        "aclSubjectModel:/org_smartbit4all_api_org_bean_SubjectSelection/2026/9/2/13/43/45ad7bb3-618b-4460-9299-7c84816bea5c");
    final byte[] slot2 = read(st, acl, 2L);
    assertThat(ok(st.newVersion(acl, "{\"four\":4}".getBytes(StandardCharsets.UTF_8))).serial()).hasValue(4L);
    ok(st.amend(acl, 1, "{\"one\":1}".getBytes(StandardCharsets.UTF_8), AmendOptions.defaults()));
    assertThat(read(st, acl, 1L)).isEqualTo("{\"one\":1}".getBytes(StandardCharsets.UTF_8));
    assertThat(read(st, acl, 2L)).isEqualTo(slot2);
    assertThat(read(st, acl, null)).isEqualTo("{\"four\":4}".getBytes(StandardCharsets.UTF_8));
    int aclHistory = 0;
    final FsStorageEngine.History ah = ok(st.history(acl));
    while (ah.hasNext()) {
      ah.next();
      aclHistory++;
    }
    assertThat(aclHistory).isEqualTo(5);
    final String descriptor = new String(ok(FsUtil.readFile(st.root().objectFile(acl))).orElseThrow(),
        StandardCharsets.UTF_8);
    assertThat(descriptor).contains(
        "\"strategy\":\"INDEXED\",\"indexSize\":256,\"objectStreams\":[],\"encryptionRule\":\"none\","
            + "\"transactionUri\":null}");

    // single version object
    final EntryUri single = uri(
        "asyncChannelRegistry-sv:/org_smartbit4all_api_invocation_bean_RuntimeAsyncChannel/2026/10/15/19/48/05df003f-46dd-4a5a-bf35-a1e34189be94-s");
    ok(st.newVersion(single, "{\"replaced\":1}".getBytes(StandardCharsets.UTF_8)));
    assertThat(read(st, single, null)).isEqualTo("{\"replaced\":1}".getBytes(StandardCharsets.UTF_8));
    assertThat(ok(st.head(single)).descriptor()).isPresent();
  }

  private static byte[] readBytes(final Path path) {
    try {
      return Files.readAllBytes(path);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}

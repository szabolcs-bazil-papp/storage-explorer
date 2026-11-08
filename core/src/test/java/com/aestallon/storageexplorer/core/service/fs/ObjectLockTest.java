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
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.aestallon.storageexplorer.core.service.engine.EngineResult;
import com.aestallon.storageexplorer.core.service.engine.StorageEngineError;

/**
 * Mirrors {@code sb4a-fs}'s own {@code lock} module test suite
 * ({@code ../sb4a-fs/crates/sb4a-fs/src/lock.rs}).
 */
class ObjectLockTest {

  private static LockOwner owner(final String runtime) {
    return new LockOwner(runtime, "");
  }

  private static LockOptions noWait() {
    return new LockOptions(false, Duration.ZERO);
  }

  private static <T> T ok(final EngineResult<T> result) {
    if (result instanceof EngineResult.Ok<T> ok) {
      return ok.value();
    }
    throw new AssertionError("expected Ok, got " + result);
  }

  @Test
  @DisplayName("Lock writes the owner, release empties the file")
  void lockWritesTheOwnerAndReleaseEmptiesTheFile(@TempDir final Path dir) throws IOException {
    final Path path = dir.resolve("a/b.l");
    final ObjectLock lock = ok(ObjectLock.acquire(path, owner("run-1"), noWait()));

    // the record is in the platform's format (not readable here: the OS lock is held)
    final byte[] expected = ByteBuffer.allocate(4 + 5 + 4)
        .putInt(5).put("run-1".getBytes(java.nio.charset.StandardCharsets.UTF_8)).putInt(0).array();
    assertThat(ObjectLock.encodeOwner(owner("run-1"))).isEqualTo(expected);
    assertThat(ObjectLock.parseOwner(expected)).contains(owner("run-1"));

    assertThat(lock.release()).isInstanceOf(EngineResult.Ok.class);
    assertThat(Files.readAllBytes(path)).isEmpty();
    assertThat(ok(ObjectLock.readOwner(path))).isEmpty();
  }

  @Test
  @DisplayName("A held lock refuses the second taker, even forced")
  void aHeldLockRefusesTheSecondTaker(@TempDir final Path dir) {
    final Path path = dir.resolve("x.l");
    final ObjectLock first = ok(ObjectLock.acquire(path, owner("one"), noWait()));

    final EngineResult<ObjectLock> second = ObjectLock.acquire(path, owner("two"), noWait());
    assertThat(second).isInstanceOf(EngineResult.Err.class);
    assertThat(((EngineResult.Err<ObjectLock>) second).error()).isInstanceOf(StorageEngineError.Locked.class);

    // even forcing cannot get past the OS lock
    final EngineResult<ObjectLock> forced = ObjectLock.acquire(path, owner("two"),
        new LockOptions(true, Duration.ZERO));
    assertThat(forced).isInstanceOf(EngineResult.Err.class);

    first.close();
    // closing released it, the file is empty again
    final ObjectLock third = ok(ObjectLock.acquire(path, owner("three"), noWait()));
    assertThat(third.release()).isInstanceOf(EngineResult.Ok.class);
  }

  @Test
  @DisplayName("Waits for the lock to be released")
  void waitsForTheLockToBeReleased(@TempDir final Path dir) throws InterruptedException {
    final Path path = dir.resolve("x.l");
    final ObjectLock first = ok(ObjectLock.acquire(path, owner("one"), noWait()));
    final Thread releaser = new Thread(() -> {
      sleep(Duration.ofMillis(150));
      first.close();
    });
    releaser.start();

    final EngineResult<ObjectLock> second = ObjectLock.acquire(path, owner("two"),
        new LockOptions(false, Duration.ofSeconds(5)));
    assertThat(second).as("%s", second).isInstanceOf(EngineResult.Ok.class);
    releaser.join();
  }

  @Test
  @DisplayName("A foreign owner record blocks unless forced")
  void aForeignOwnerRecordBlocksUnlessForced(@TempDir final Path dir) throws IOException {
    final Path path = dir.resolve("x.l");
    // what a platform node leaves while it holds the logical lock
    final byte[] id = "7d2b8f3e-0000-4000-8000-000000000001".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    final byte[] platform = ByteBuffer.allocate(4 + id.length + 4).putInt(id.length).put(id).putInt(0).array();
    Files.write(path, platform);

    assertThat(ok(ObjectLock.readOwner(path)).orElseThrow().runtimeId())
        .isEqualTo("7d2b8f3e-0000-4000-8000-000000000001");

    final EngineResult<ObjectLock> refused = ObjectLock.acquire(path, owner("rust"), noWait());
    assertThat(refused).isInstanceOf(EngineResult.Err.class);
    final StorageEngineError error = ((EngineResult.Err<ObjectLock>) refused).error();
    assertThat(error).isInstanceOf(StorageEngineError.Locked.class);
    assertThat(((StorageEngineError.Locked) error).owner()).contains("7d2b8f3e");
    // refusing must not have changed the record
    assertThat(Files.readAllBytes(path)).isEqualTo(platform);

    final ObjectLock forced = ok(ObjectLock.acquire(path, owner("rust"), new LockOptions(true, Duration.ZERO)));
    assertThat(forced.release()).isInstanceOf(EngineResult.Ok.class);
    assertThat(Files.readAllBytes(path)).isEmpty();
  }

  @Test
  @DisplayName("Waits for a foreign owner record to go away, and gives up naming the owner")
  void waitsForAForeignOwnerRecordToGoAway(@TempDir final Path dir) throws IOException, InterruptedException {
    final Path path = dir.resolve("x.l");
    Files.write(path, ObjectLock.encodeOwner(owner("platform")));

    // a platform node unlocking: the record is truncated after a while
    final Thread clearer = new Thread(() -> {
      sleep(Duration.ofMillis(200));
      try {
        Files.write(path, new byte[0]);
      } catch (final IOException e) {
        throw new java.io.UncheckedIOException(e);
      }
    });
    clearer.start();

    final Instant started = Instant.now();
    final ObjectLock lock = ok(ObjectLock.acquire(path, owner("rust"),
        new LockOptions(false, Duration.ofSeconds(10))));
    assertThat(Duration.between(started, Instant.now())).isGreaterThanOrEqualTo(Duration.ofMillis(150));
    clearer.join();
    assertThat(lock.release()).isInstanceOf(EngineResult.Ok.class);

    // and it gives up with the owner named when the record stays
    Files.write(path, ObjectLock.encodeOwner(owner("platform")));
    final EngineResult<ObjectLock> refused = ObjectLock.acquire(path, owner("rust"),
        new LockOptions(false, Duration.ofMillis(100)));
    assertThat(refused).isInstanceOf(EngineResult.Err.class);
    final StorageEngineError error = ((EngineResult.Err<ObjectLock>) refused).error();
    assertThat(((StorageEngineError.Locked) error).owner()).contains("platform");
  }

  @Test
  @DisplayName("Owner parsing is lenient")
  void ownerParsingIsLenient() {
    assertThat(ObjectLock.parseOwner(new byte[0])).isEmpty();

    final byte[] withoutTransaction = ByteBuffer.allocate(4 + 3).putInt(3)
        .put("abc".getBytes(java.nio.charset.StandardCharsets.UTF_8)).array();
    assertThat(ObjectLock.parseOwner(withoutTransaction)).contains(owner("abc"));

    final byte[] clamped = ByteBuffer.allocate(4 + 5).putInt(100)
        .put("short".getBytes(java.nio.charset.StandardCharsets.UTF_8)).array();
    assertThat(ObjectLock.parseOwner(clamped).orElseThrow().runtimeId()).isEqualTo("short");
  }

  private static void sleep(final Duration duration) {
    try {
      Thread.sleep(duration.toMillis());
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}

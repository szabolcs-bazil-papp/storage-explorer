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
import java.nio.channels.FileChannel;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import com.aestallon.storageexplorer.core.service.engine.EngineResult;
import com.aestallon.storageexplorer.core.service.engine.StorageEngineError;

/**
 * Small file-system helpers shared by the read/write/codec layers.
 *
 * <p>Ported from the {@code sb4a-fs} Rust library's {@code fsutil} module
 * ({@code ../sb4a-fs/crates/sb4a-fs/src/fsutil.rs}).
 *
 * <p><strong>Open risk, not yet validated against a real sharing violation</strong> (flagged, not
 * silently assumed): Rust's transient-error detection checks {@code io::ErrorKind::PermissionDenied}
 * / {@code ResourceBusy}, which map cleanly onto specific Rust error kinds. Java's NIO has no
 * dedicated exception type for a Windows sharing violation — {@link #isTransient} treats
 * {@link AccessDeniedException} and any other {@link FileSystemException} (other than
 * {@link NoSuchFileException}) as transient, which is a reasonable but unverified heuristic. This
 * should be exercised against a real concurrent-write scenario before being trusted, the same way
 * T1.5's locking code's platform-lock-semantics assumption needs to be.
 */
final class FsUtil {

  /** On Windows the platform's replace fails for as long as any reader holds the target open. */
  private static final Duration REPLACE_MAX_WAIT = Duration.ofSeconds(10);
  private static final Duration READ_MAX_WAIT = Duration.ofSeconds(5);

  private FsUtil() {}

  /**
   * Reads a whole file; empty if it does not exist. Retries with a growing pause while the file
   * is transiently unreadable (a writer is replacing it, sharing violations on Windows).
   */
  static EngineResult<Optional<byte[]>> readFile(final Path path) {
    final Instant started = Instant.now();
    Duration pause = Duration.ofMillis(2);
    while (true) {
      try {
        return new EngineResult.Ok<>(Optional.of(Files.readAllBytes(path)));
      } catch (final NoSuchFileException e) {
        return new EngineResult.Ok<>(Optional.empty());
      } catch (final IOException e) {
        if (isTransient(e) && Duration.between(started, Instant.now()).compareTo(READ_MAX_WAIT) < 0) {
          sleep(pause);
          pause = growBy(pause, Duration.ofMillis(100));
          continue;
        }
        return new EngineResult.Err<>(new StorageEngineError.Io(path, e));
      }
    }
  }

  /** Creates the parent folders of {@code path}. */
  static EngineResult<Void> ensureParent(final Path path) {
    final Path parent = path.getParent();
    if (parent == null) {
      return new EngineResult.Ok<>(null);
    }
    try {
      Files.createDirectories(parent);
      return new EngineResult.Ok<>(null);
    } catch (final IOException e) {
      return new EngineResult.Err<>(new StorageEngineError.Io(parent, e));
    }
  }

  /**
   * Moves {@code from} over {@code to} (replacing it) atomically, retrying with a growing pause
   * while the target is held open by somebody else (sharing violations on Windows).
   */
  static EngineResult<Void> replaceFile(final Path from, final Path to) {
    final Instant started = Instant.now();
    Duration pause = Duration.ofMillis(2);
    while (true) {
      try {
        Files.move(from, to, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        return new EngineResult.Ok<>(null);
      } catch (final IOException e) {
        if (isTransient(e) && Duration.between(started, Instant.now()).compareTo(REPLACE_MAX_WAIT) < 0) {
          sleep(pause);
          pause = growBy(pause, Duration.ofMillis(200));
          continue;
        }
        return new EngineResult.Err<>(new StorageEngineError.Io(to, e));
      }
    }
  }

  /**
   * Writes {@code bytes} to a sibling temp file, flushes it to disk and moves it over
   * {@code path}: a reader sees either the old or the new content, never a mix, and a crash
   * leaves the old content in place.
   */
  static EngineResult<Void> writeFileAtomic(final Path path, final byte[] bytes) {
    if (ensureParent(path) instanceof EngineResult.Err<Void> err) {
      return err;
    }
    final Path tmp = tempSibling(path);
    try (FileChannel channel = FileChannel.open(tmp, StandardOpenOption.CREATE,
        StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
      channel.write(ByteBuffer.wrap(bytes));
      channel.force(true);
    } catch (final IOException e) {
      deleteQuietly(tmp);
      return new EngineResult.Err<>(new StorageEngineError.Io(tmp, e));
    }
    if (replaceFile(tmp, path) instanceof EngineResult.Err<Void> err) {
      deleteQuietly(tmp);
      return err;
    }
    return new EngineResult.Ok<>(null);
  }

  private static Path tempSibling(final Path path) {
    final String name = path.getFileName() + "." + ProcessHandle.current().pid() + ".tmp";
    return path.resolveSibling(name);
  }

  private static void deleteQuietly(final Path path) {
    try {
      Files.deleteIfExists(path);
    } catch (final IOException ignored) {
      // best-effort cleanup only
    }
  }

  private static boolean isTransient(final IOException e) {
    if (e instanceof NoSuchFileException) {
      return false;
    }
    return e instanceof AccessDeniedException || e instanceof FileSystemException;
  }

  private static Duration growBy(final Duration pause, final Duration cap) {
    final Duration doubled = pause.multipliedBy(2);
    return (doubled.compareTo(cap) > 0) ? cap : doubled;
  }

  private static void sleep(final Duration duration) {
    try {
      Thread.sleep(duration.toMillis());
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}

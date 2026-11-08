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
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardOpenOption;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import com.aestallon.storageexplorer.core.service.engine.EngineResult;
import com.aestallon.storageexplorer.core.service.engine.StorageEngineError;

/**
 * The object lock, the {@code .l} file.
 *
 * <p>The platform's lock is a <em>logical</em> lock: the {@code .l} file holds the owner (runtime
 * id and transaction id) while the object is locked and is empty at rest; an OS file lock is taken
 * only for the short moment of checking and claiming. This implementation is simpler and stricter
 * (the agreed policy): it keeps the <strong>OS lock for the whole update</strong>, writes its own
 * owner record, and, if the file already names a foreign owner, waits (up to the configured
 * timeout) for that record to go away and then refuses, unless forced. A dead platform node cannot
 * be detected from the file alone — that is what {@link LockOptions#force()} is for.
 *
 * <p>Content format (when locked): {@code [i32 BE length][UTF-8 runtime id][i32 BE
 * length][UTF-8 transaction id]}.
 *
 * <p><strong>Flagged, not yet validated against a live platform node</strong> (per the
 * implementation plan's Milestone 1 notes): on Windows, this and the platform both use
 * {@code LockFileEx}-family primitives and exclude each other properly. On Unix, the platform
 * takes POSIX ({@code fcntl}) locks while this implementation's underlying {@code FileChannel}
 * lock does not necessarily see those — there, the owner record is the only real protection
 * against a running platform node. This needs a real cross-process test before being trusted in
 * anger, the same way T1.1 flagged for the lock mechanism generally.
 *
 * <p>Ported from the {@code sb4a-fs} Rust library's {@code lock} module
 * ({@code ../sb4a-fs/crates/sb4a-fs/src/lock.rs}).
 */
public final class ObjectLock implements AutoCloseable {

  private FileChannel channel;
  private final Path path;

  private ObjectLock(final FileChannel channel, final Path path) {
    this.channel = channel;
    this.path = path;
  }

  /** Locks the object whose lock file is {@code path}. */
  public static EngineResult<ObjectLock> acquire(final Path path, final LockOwner owner,
                                                   final LockOptions options) {
    if (FsUtil.ensureParent(path) instanceof EngineResult.Err<Void> err) {
      return new EngineResult.Err<>(err.error());
    }
    final FileChannel channel;
    try {
      channel = FileChannel.open(path, StandardOpenOption.READ, StandardOpenOption.WRITE,
          StandardOpenOption.CREATE);
    } catch (final IOException e) {
      return new EngineResult.Err<>(new StorageEngineError.Io(path, e));
    }

    final Instant deadline = Instant.now().plus(options.timeout());
    Duration pause = Duration.ofMillis(2);
    // Take the OS lock, then look at the owner record. A record of somebody else means that
    // owner holds the logical lock: give the OS lock back and wait for the record to go away,
    // until the deadline. Waiting for the OS lock and for the owner share the one deadline.
    while (true) {
      final FileLock osLock;
      try {
        osLock = tryLockOrNull(channel);
      } catch (final IOException e) {
        closeQuietly(channel);
        return new EngineResult.Err<>(new StorageEngineError.Io(path, e));
      }
      if (osLock == null) {
        if (Instant.now().isBefore(deadline)) {
          sleep(pause);
          pause = growBy(pause, Duration.ofMillis(50));
          continue;
        }
        closeQuietly(channel);
        return new EngineResult.Err<>(new StorageEngineError.Locked(path,
            "another process (the file is locked)"));
      }

      final byte[] existing;
      try {
        existing = readAll(channel);
      } catch (final IOException e) {
        releaseQuietly(osLock);
        closeQuietly(channel);
        return new EngineResult.Err<>(new StorageEngineError.Io(path, e));
      }

      if (existing.length == 0 || options.force()) {
        // From here on the OS lock is ours; write the owner record and hand back the guard.
        if (writeOwnerRecord(channel, owner, path) instanceof EngineResult.Err<Void> err) {
          releaseQuietly(osLock);
          closeQuietly(channel);
          return new EngineResult.Err<>(err.error());
        }
        return new EngineResult.Ok<>(new ObjectLock(channel, path));
      }

      releaseQuietly(osLock);
      if (!Instant.now().isBefore(deadline)) {
        final String ownerText = parseOwner(existing)
            .map(LockOwner::toString)
            .orElse("an unknown owner (unreadable lock data)");
        closeQuietly(channel);
        return new EngineResult.Err<>(new StorageEngineError.Locked(path, ownerText));
      }
      sleep(pause);
      pause = growBy(pause, Duration.ofMillis(20));
    }
  }

  public Path path() {
    return path;
  }

  /** Releases the lock; unlike {@link #close()} this reports a failure. */
  public EngineResult<Void> release() {
    return unlock();
  }

  /** Releases the lock, swallowing any failure — the {@code try}-with-resources safety net. */
  @Override
  public void close() {
    unlock();
  }

  private EngineResult<Void> unlock() {
    final FileChannel c = channel;
    if (c == null) {
      return new EngineResult.Ok<>(null);
    }
    channel = null;
    try {
      c.truncate(0);
    } catch (final IOException e) {
      closeQuietly(c);
      return new EngineResult.Err<>(new StorageEngineError.Io(path, e));
    }
    try {
      c.close();
    } catch (final IOException e) {
      return new EngineResult.Err<>(new StorageEngineError.Io(path, e));
    }
    return new EngineResult.Ok<>(null);
  }

  /**
   * Who owns the lock file right now. Empty if it is empty (not locked) or missing. A lock file
   * held through a mandatory OS lock (Windows) may be unreadable: that is reported as an error,
   * not as "unlocked".
   */
  public static EngineResult<Optional<LockOwner>> readOwner(final Path path) {
    final Optional<byte[]> bytes;
    switch (FsUtil.readFile(path)) {
      case EngineResult.Ok<Optional<byte[]>> ok -> bytes = ok.value();
      case EngineResult.Err<Optional<byte[]>> err -> {
        return new EngineResult.Err<>(err.error());
      }
    }
    if (bytes.isEmpty() || bytes.get().length == 0) {
      return new EngineResult.Ok<>(Optional.empty());
    }
    final LockOwner owner = parseOwner(bytes.get())
        .orElse(new LockOwner("<unreadable lock data>", ""));
    return new EngineResult.Ok<>(Optional.of(owner));
  }

  static byte[] encodeOwner(final LockOwner owner) {
    final byte[] runtimeId = owner.runtimeId().getBytes(StandardCharsets.UTF_8);
    final byte[] transactionId = owner.transactionId().getBytes(StandardCharsets.UTF_8);
    final ByteBuffer out = ByteBuffer.allocate(4 + runtimeId.length + 4 + transactionId.length);
    out.putInt(runtimeId.length).put(runtimeId);
    out.putInt(transactionId.length).put(transactionId);
    return out.array();
  }

  /**
   * Lenient: the platform writes the length of the string in UTF-16 units, which is the byte
   * length only for ASCII, so a length that does not fit is clamped and the text read lossily.
   */
  static Optional<LockOwner> parseOwner(final byte[] bytes) {
    final int[] offset = {0};
    final String runtimeId = parseLenientString(bytes, offset);
    if (runtimeId == null) {
      return Optional.empty();
    }
    final String transactionId = parseLenientString(bytes, offset);
    return Optional.of(new LockOwner(runtimeId, (transactionId == null) ? "" : transactionId));
  }

  private static String parseLenientString(final byte[] bytes, final int[] offset) {
    if (offset[0] + 4 > bytes.length) {
      return null;
    }
    final int length = ByteBuffer.wrap(bytes, offset[0], 4).getInt();
    offset[0] += 4;
    if (length < 0) {
      return null;
    }
    final int end = Math.min(offset[0] + length, bytes.length);
    final String text = new String(bytes, offset[0], end - offset[0], StandardCharsets.UTF_8);
    offset[0] = end;
    return text;
  }

  private static EngineResult<Void> writeOwnerRecord(final FileChannel channel, final LockOwner owner,
                                                       final Path path) {
    final byte[] record = encodeOwner(owner);
    try {
      channel.truncate(0);
      channel.write(ByteBuffer.wrap(record), 0);
      channel.force(false);
      return new EngineResult.Ok<>(null);
    } catch (final IOException e) {
      return new EngineResult.Err<>(new StorageEngineError.Io(path, e));
    }
  }

  private static byte[] readAll(final FileChannel channel) throws IOException {
    final long size = channel.size();
    final ByteBuffer buf = ByteBuffer.allocate((int) size);
    long position = 0;
    while (buf.hasRemaining()) {
      final int n = channel.read(buf, position);
      if (n < 0) {
        break;
      }
      position += n;
    }
    return buf.array();
  }

  /** Same-process re-acquisition overlaps rather than blocking in Java; treat it identically to
   *  "held by someone else" rather than letting the exception escape. */
  private static FileLock tryLockOrNull(final FileChannel channel) throws IOException {
    try {
      return channel.tryLock();
    } catch (final OverlappingFileLockException e) {
      return null;
    }
  }

  private static void releaseQuietly(final FileLock lock) {
    try {
      lock.release();
    } catch (final IOException ignored) {
      // best effort only, mirrors the Rust port's "let _ = file.unlock();"
    }
  }

  private static void closeQuietly(final FileChannel channel) {
    try {
      channel.close();
    } catch (final IOException ignored) {
      // best effort only
    }
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

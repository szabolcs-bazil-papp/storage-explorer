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

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.zip.Deflater;
import com.aestallon.storageexplorer.core.service.engine.EngineResult;
import com.aestallon.storageexplorer.core.service.engine.StorageEngineError;

/**
 * The indexed version file, {@code .iv}: {@code slots} versions in one file.
 *
 * <pre>
 * +-------------------------------+ 0
 * | slot 0: i64 start, i64 length |   index region: slots * 16 bytes, -1/-1 = never written
 * | ...                           |
 * +-------------------------------+ slots * 16
 * | segments, appended            |   a segment = parts: [i64 compressed length][raw DEFLATE bytes]...
 * +-------------------------------+
 * </pre>
 *
 * <p>Rewriting a slot appends a new segment and repoints the index entry; the old segment becomes
 * dead space that is never reclaimed (like the platform does). There is no protection against
 * concurrent writers: callers hold the object lock (T1.5).
 *
 * <p>Ported from the {@code sb4a-fs} Rust library's {@code indexed} module
 * ({@code ../sb4a-fs/crates/sb4a-fs/src/indexed.rs}).
 */
public final class IndexedFile implements Closeable {

  private static final long UNSET = -1L;
  private static final long ENTRY = 16L;
  private static final int HEADER = Long.BYTES;

  private final FileChannel channel;
  private final Path path;
  private final int slots;

  private IndexedFile(final FileChannel channel, final Path path, final int slots) {
    this.channel = channel;
    this.path = path;
    this.slots = slots;
  }

  /**
   * Opens an existing file for reading. Empty if the file does not exist (every slot of a
   * missing file is unset).
   */
  public static EngineResult<Optional<IndexedFile>> open(final Path path, final int slots) {
    return openWith(path, slots, StandardOpenOption.READ);
  }

  /**
   * Opens an existing file for reading and writing. Empty if it does not exist (it is not
   * created).
   */
  public static EngineResult<Optional<IndexedFile>> openRw(final Path path, final int slots) {
    return openWith(path, slots, StandardOpenOption.READ, StandardOpenOption.WRITE);
  }

  private static EngineResult<Optional<IndexedFile>> openWith(final Path path, final int slots,
                                                                final StandardOpenOption... options) {
    final FileChannel channel;
    try {
      channel = FileChannel.open(path, options);
    } catch (final NoSuchFileException e) {
      return new EngineResult.Ok<>(Optional.empty());
    } catch (final IOException e) {
      return new EngineResult.Err<>(new StorageEngineError.Io(path, e));
    }
    return switch (checked(channel, path, slots)) {
      case EngineResult.Ok<IndexedFile> ok -> new EngineResult.Ok<>(Optional.of(ok.value()));
      case EngineResult.Err<IndexedFile> err -> new EngineResult.Err<>(err.error());
    };
  }

  /**
   * Opens the file for reading and writing, creating it (with the parent folders and an empty
   * index) if it does not exist.
   */
  public static EngineResult<IndexedFile> openOrCreate(final Path path, final int slots) {
    if (slots == 0) {
      return new EngineResult.Err<>(
          new StorageEngineError.Malformed("an indexed file needs at least one slot"));
    }
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
    try {
      if (channel.size() == 0) {
        // UNSET is -1, so the whole index region is 0xFF bytes.
        final byte[] region = new byte[(int) ((long) slots * ENTRY)];
        Arrays.fill(region, (byte) 0xFF);
        channel.write(ByteBuffer.wrap(region), 0);
        channel.force(true);
      }
    } catch (final IOException e) {
      return new EngineResult.Err<>(new StorageEngineError.Io(path, e));
    }
    return checked(channel, path, slots);
  }

  private static EngineResult<IndexedFile> checked(final FileChannel channel, final Path path,
                                                     final int slots) {
    final long length;
    try {
      length = channel.size();
    } catch (final IOException e) {
      return new EngineResult.Err<>(new StorageEngineError.Io(path, e));
    }
    final long region = (long) slots * ENTRY;
    if (slots == 0 || length < region) {
      return new EngineResult.Err<>(new StorageEngineError.Format(path,
          "the file has " + length + " bytes, less than the index region of " + slots
              + " slots (" + region + " bytes)"));
    }
    return new EngineResult.Ok<>(new IndexedFile(channel, path, slots));
  }

  public Path path() {
    return path;
  }

  public int slots() {
    return slots;
  }

  private long indexRegion() {
    return (long) slots * ENTRY;
  }

  private EngineResult<Long> fileLength() {
    try {
      return new EngineResult.Ok<>(channel.size());
    } catch (final IOException e) {
      return new EngineResult.Err<>(new StorageEngineError.Io(path, e));
    }
  }

  private Optional<StorageEngineError> checkSlot(final int slot) {
    if (slot < 0 || slot >= slots) {
      return Optional.of(new StorageEngineError.Malformed(
          "slot " + slot + " is out of range (the file has " + slots + " slots)"));
    }
    return Optional.empty();
  }

  /** The index entry of a slot, empty if the slot was never written. */
  public EngineResult<Optional<SlotEntry>> entry(final int slot) {
    final Optional<StorageEngineError> badSlot = checkSlot(slot);
    if (badSlot.isPresent()) {
      return new EngineResult.Err<>(badSlot.get());
    }
    final byte[] raw = new byte[(int) ENTRY];
    if (readExactAt((long) slot * ENTRY, raw) instanceof EngineResult.Err<Void> err) {
      return new EngineResult.Err<>(err.error());
    }
    final ByteBuffer buf = ByteBuffer.wrap(raw);
    final long start = buf.getLong(0);
    final long length = buf.getLong(8);
    if (start == UNSET || length == UNSET) {
      return new EngineResult.Ok<>(Optional.empty());
    }
    if (start < 0 || length < 0) {
      return new EngineResult.Err<>(new StorageEngineError.Format(path,
          "slot " + slot + " has a negative start or length (" + start + ", " + length + ")"));
    }
    final long end = start + length;
    final long len;
    switch (fileLength()) {
      case EngineResult.Ok<Long> ok -> len = ok.value();
      case EngineResult.Err<Long> err -> {
        return new EngineResult.Err<>(err.error());
      }
    }
    if (start < indexRegion() || end > len) {
      return new EngineResult.Err<>(new StorageEngineError.Format(path,
          "the segment of slot " + slot + " (" + start + "+" + length + ") is outside of the file"));
    }
    return new EngineResult.Ok<>(Optional.of(new SlotEntry(slot, start, length)));
  }

  /** All the written slots, in slot order. */
  public EngineResult<List<SlotEntry>> entries() {
    final List<SlotEntry> all = new ArrayList<>();
    for (int slot = 0; slot < slots; slot++) {
      switch (entry(slot)) {
        case EngineResult.Ok<Optional<SlotEntry>> ok -> ok.value().ifPresent(all::add);
        case EngineResult.Err<Optional<SlotEntry>> err -> {
          return new EngineResult.Err<>(err.error());
        }
      }
    }
    return new EngineResult.Ok<>(all);
  }

  /** The bytes of the file that no slot points to: the segments overwritten by later writes. */
  public EngineResult<Long> deadSpace() {
    final List<SlotEntry> all;
    switch (entries()) {
      case EngineResult.Ok<List<SlotEntry>> ok -> all = ok.value();
      case EngineResult.Err<List<SlotEntry>> err -> {
        return new EngineResult.Err<>(err.error());
      }
    }
    long live = 0L;
    for (final SlotEntry e : all) {
      live += e.length();
    }
    final long len;
    switch (fileLength()) {
      case EngineResult.Ok<Long> ok -> len = ok.value();
      case EngineResult.Err<Long> err -> {
        return new EngineResult.Err<>(err.error());
      }
    }
    final long withoutIndex = Math.max(0, len - indexRegion());
    return new EngineResult.Ok<>(Math.max(0, withoutIndex - live));
  }

  /** Reads and inflates the parts of a slot; empty if the slot was never written. */
  public EngineResult<Optional<List<byte[]>>> readSlot(final int slot) {
    final Optional<SlotEntry> maybeEntry;
    switch (entry(slot)) {
      case EngineResult.Ok<Optional<SlotEntry>> ok -> maybeEntry = ok.value();
      case EngineResult.Err<Optional<SlotEntry>> err -> {
        return new EngineResult.Err<>(err.error());
      }
    }
    if (maybeEntry.isEmpty()) {
      return new EngineResult.Ok<>(Optional.empty());
    }
    final SlotEntry slotEntry = maybeEntry.get();
    if (slotEntry.length() > Integer.MAX_VALUE) {
      return new EngineResult.Err<>(
          new StorageEngineError.Malformed("the segment of slot " + slot + " is too large")
              .at(path));
    }
    final byte[] segment = new byte[(int) slotEntry.length()];
    if (readExactAt(slotEntry.start(), segment) instanceof EngineResult.Err<Void> err) {
      return new EngineResult.Err<>(err.error());
    }
    return switch (parseSegment(segment)) {
      case EngineResult.Ok<List<byte[]>> ok -> new EngineResult.Ok<>(Optional.of(ok.value()));
      case EngineResult.Err<List<byte[]>> err -> {
        final StorageEngineError error = (err.error() instanceof StorageEngineError.Malformed m)
            ? new StorageEngineError.Malformed("slot " + slot + ": " + m.why())
            : err.error();
        yield new EngineResult.Err<>(error.at(path));
      }
    };
  }

  /**
   * Writes the parts as the new content of a slot: the segment is appended and flushed first,
   * the index entry is repointed and flushed second, so a crash in between leaves the previous
   * content of the slot valid.
   */
  public EngineResult<Void> writeSlot(final int slot, final List<byte[]> parts) {
    final Optional<StorageEngineError> badSlot = checkSlot(slot);
    if (badSlot.isPresent()) {
      return new EngineResult.Err<>(badSlot.get());
    }
    if (parts.isEmpty()) {
      return new EngineResult.Err<>(new StorageEngineError.Malformed("a slot needs at least one part"));
    }
    final byte[] segment = buildSegment(parts);
    final long currentLength;
    switch (fileLength()) {
      case EngineResult.Ok<Long> ok -> currentLength = ok.value();
      case EngineResult.Err<Long> err -> {
        return new EngineResult.Err<>(err.error());
      }
    }
    // The data is only ever appended, so the end of the file is the append offset.
    final long start = Math.max(currentLength, indexRegion());

    if (seekWrite(start, segment) instanceof EngineResult.Err<Void> err) {
      return new EngineResult.Err<>(err.error());
    }
    try {
      channel.force(false);
    } catch (final IOException e) {
      return new EngineResult.Err<>(new StorageEngineError.Io(path, e));
    }

    final ByteBuffer entryBuf = ByteBuffer.allocate((int) ENTRY);
    entryBuf.putLong(start);
    entryBuf.putLong(segment.length);
    entryBuf.flip();
    if (seekWrite((long) slot * ENTRY, entryBuf.array()) instanceof EngineResult.Err<Void> err) {
      return new EngineResult.Err<>(err.error());
    }
    try {
      channel.force(true);
    } catch (final IOException e) {
      return new EngineResult.Err<>(new StorageEngineError.Io(path, e));
    }
    return new EngineResult.Ok<>(null);
  }

  private EngineResult<Void> readExactAt(final long offset, final byte[] buf) {
    final ByteBuffer buffer = ByteBuffer.wrap(buf);
    try {
      long position = offset;
      while (buffer.hasRemaining()) {
        final int n = channel.read(buffer, position);
        if (n < 0) {
          return new EngineResult.Err<>(new StorageEngineError.Io(path,
              new java.io.EOFException("unexpected end of file at offset " + position)));
        }
        position += n;
      }
      return new EngineResult.Ok<>(null);
    } catch (final IOException e) {
      return new EngineResult.Err<>(new StorageEngineError.Io(path, e));
    }
  }

  private EngineResult<Void> seekWrite(final long offset, final byte[] bytes) {
    final ByteBuffer buffer = ByteBuffer.wrap(bytes);
    try {
      long position = offset;
      while (buffer.hasRemaining()) {
        position += channel.write(buffer, position);
      }
      return new EngineResult.Ok<>(null);
    } catch (final IOException e) {
      return new EngineResult.Err<>(new StorageEngineError.Io(path, e));
    }
  }

  @Override
  public void close() throws IOException {
    channel.close();
  }

  // --- segment codec: [i64 compressed length][raw deflate bytes]... ----------------------

  /** Splits a segment into its inflated parts. */
  private static EngineResult<List<byte[]>> parseSegment(final byte[] segment) {
    final List<byte[]> parts = new ArrayList<>();
    int offset = 0;
    while (offset < segment.length) {
      if (offset + HEADER > segment.length) {
        return new EngineResult.Err<>(
            new StorageEngineError.Malformed("truncated part header at offset " + offset));
      }
      final long length = ByteBuffer.wrap(segment, offset, HEADER).getLong();
      offset += HEADER;
      if (length < 0 || length > Integer.MAX_VALUE || offset + length > segment.length) {
        return new EngineResult.Err<>(new StorageEngineError.Malformed(
            "invalid compressed part length " + length + " at offset " + offset));
      }
      final int end = offset + (int) length;
      final EngineResult<byte[]> inflated = Deflate.decompress(Arrays.copyOfRange(segment, offset, end));
      if (inflated instanceof EngineResult.Err<byte[]> err) {
        return new EngineResult.Err<>(err.error());
      }
      parts.add(((EngineResult.Ok<byte[]>) inflated).value());
      offset = end;
    }
    return new EngineResult.Ok<>(parts);
  }

  /**
   * Compresses the parts into a segment: {@code [i64 compressed length][raw deflate]} for each
   * part, at the fastest level (like the platform).
   */
  private static byte[] buildSegment(final List<byte[]> parts) {
    final ByteArrayOutputStream segment = new ByteArrayOutputStream();
    for (final byte[] part : parts) {
      final byte[] compressed = Deflate.compress(part, Deflater.BEST_SPEED);
      segment.write(ByteBuffer.allocate(HEADER).putLong(compressed.length).array(), 0, HEADER);
      segment.write(compressed, 0, compressed.length);
    }
    return segment.toByteArray();
  }
}

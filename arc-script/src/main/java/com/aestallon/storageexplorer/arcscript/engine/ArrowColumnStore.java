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

package com.aestallon.storageexplorer.arcscript.engine;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.vector.BigIntVector;
import org.apache.arrow.vector.BitVector;
import org.apache.arrow.vector.FieldVector;
import org.apache.arrow.vector.Float8Vector;
import org.apache.arrow.vector.VarCharVector;
import org.apache.arrow.vector.VectorSchemaRoot;
import org.apache.arrow.vector.dictionary.DictionaryProvider;
import org.apache.arrow.vector.ipc.ArrowFileReader;
import org.apache.arrow.vector.ipc.ArrowFileWriter;
import org.apache.arrow.vector.types.Types;
import org.apache.arrow.vector.types.pojo.Field;
import org.apache.arrow.vector.types.pojo.FieldType;
import org.apache.arrow.vector.types.pojo.Schema;
import groovy.json.JsonOutput;
import com.aestallon.storageexplorer.core.model.entry.StorageEntry;
import com.aestallon.storageexplorer.core.service.StorageInstanceExaminer;

/**
 * Columnar projection store backing {@link ArrowQueryEngine}.
 *
 * <p>
 * Every property referenced by a query's {@code where} (single-valued assertions only - list
 * quantifiers are evaluated the same way the other engines evaluate them, directly against the
 * examiner), {@code order} and {@code yield}/{@code show} clauses is discovered <em>exactly once</em>
 * per source entry and appended here as a row, instead of the separate {@code where}/{@code order}/
 * {@code yield} passes of {@link QueryEngineImpl}/{@link PipelinedQueryEngine} each re-discovering
 * whichever properties they individually need.
 *
 * <p>
 * Each property gets a typed Arrow "fast lane" vector - {@code BIGINT}/{@code FLOAT8}/
 * {@code VARCHAR}/{@code BIT}, decided from the first scalar value observed for that column - plus
 * an always-present {@code VARCHAR} "json lane" holding a JSON-serialised fallback representation,
 * used for {@code json}/{@code list}-valued properties and for the rare case where a later row's
 * value doesn't match the column's already-fixed fast-lane kind. A column's kind, once decided,
 * never changes; a column still undecided when the schema must be fixed (see below) defaults to
 * {@code STRING}.
 *
 * <p>
 * Rows accumulate resident in memory. Once {@link QueryEngineSettings#arrowSpillRowLimit()} rows
 * have been produced, the nascent in-memory table is serialised as the first Arrow IPC record batch
 * of a temp file and its vectors are cleared; every further {@link QueryEngineSettings#arrowBatchSize()}
 * rows are flushed the same way, bounding peak memory to roughly one batch regardless of how large
 * the projected result grows. After {@link #finish()}, a store is either <em>entirely</em> resident
 * (never crossed the limit) or <em>entirely</em> spilled (the final partial batch is flushed too) -
 * never a mix of both, which keeps read-back a simple two-way branch.
 *
 * <p>
 * {@link StorageEntry} references themselves are kept resident for every row regardless of spill
 * state - consistent with {@link QueryEngineImpl}, which already collects the full source set into
 * a {@code Set<StorageEntry>} regardless of result size. Entry handles are cheap (a URI plus lazy
 * metadata); it is the discovered property <em>values</em>, which fan out with every additional
 * {@code where}/{@code order}/{@code show} column, that Arrow exists to bound.
 *
 * <p>
 * Known trade-offs, accepted for the sake of a tractable implementation:
 * <ul>
 * <li>a property whose value is {@code null} for the first {@code arrowSpillRowLimit} rows and only
 * later turns out to be numeric/boolean is defaulted to the {@code STRING} fast lane and any
 * later scalar value of a different shape is diverted to the json lane instead of upgrading the
 * column - correct, just not maximally fast for that specific column;</li>
 * <li>JSON round-tripping of {@code json}/{@code list}-valued properties does not preserve exact
 * numeric subtypes (e.g. a stored {@code Integer} may read back as a {@code Long}); this only
 * affects such properties' involvement in predicates/sort/yield, and is no worse than JSON's own
 * native numeric model.</li>
 * </ul>
 */
final class ArrowColumnStore implements AutoCloseable {

  private static final String FAST_SUFFIX = "#fast";
  private static final String JSON_SUFFIX = "#json";

  enum Kind { UNKNOWN, INTEGRAL, FLOATING, STRING, BOOLEAN }


  @FunctionalInterface
  interface RowConsumer {
    void accept(long globalRowIndex, StorageEntry entry, PropertySource propertySource)
        throws Exception;
  }


  private static final class Column {
    final String name;
    Kind kind = Kind.UNKNOWN;
    BigIntVector integralVector;
    Float8Vector floatingVector;
    VarCharVector stringVector;
    BitVector booleanVector;
    VarCharVector jsonVector;

    Column(final String name) {
      this.name = name;
    }

    FieldVector fastVector() {
      return switch (kind) {
        case INTEGRAL -> integralVector;
        case FLOATING -> floatingVector;
        case STRING -> stringVector;
        case BOOLEAN -> booleanVector;
        case UNKNOWN -> null;
      };
    }
  }


  private final BufferAllocator allocator;
  private final List<String> properties;
  private final Map<String, Column> columns;
  private final List<StorageEntry> entries = new ArrayList<>();
  private final long spillRowLimit;
  private final int batchSize;
  private final Path spillDirectory;
  private final ReentrantLock lock = new ReentrantLock();

  private int residentRowCount = 0;
  private long totalRowCount = 0;
  private boolean spilling = false;
  private boolean finished = false;
  private Path spillFile;
  private ArrowFileWriter writer;
  private SeekableByteChannel writerChannel;
  private final List<Integer> flushedBatchSizes = new ArrayList<>();

  ArrowColumnStore(final BufferAllocator allocator,
                   final List<String> properties,
                   final long spillRowLimit,
                   final int batchSize,
                   final Path spillDirectory) {
    this.allocator = allocator;
    this.properties = List.copyOf(properties);
    this.columns = new LinkedHashMap<>();
    for (final String prop : this.properties) {
      columns.put(prop, new Column(prop));
    }
    this.spillRowLimit = spillRowLimit;
    this.batchSize = batchSize;
    this.spillDirectory = spillDirectory;
  }

  List<String> properties() {
    return properties;
  }

  long rowCount() {
    return totalRowCount;
  }

  boolean spilled() {
    return spilling;
  }

  Path spillFile() {
    return spillFile;
  }

  StorageEntry entry(final long globalRowIndex) {
    return entries.get((int) globalRowIndex);
  }

  /**
   * Appends a fully-discovered row. {@code discovered} must carry an entry for every property in
   * {@link #properties()}.
   *
   * @return the new row's global index
   */
  long append(final StorageEntry entry,
              final Map<String, StorageInstanceExaminer.PropertyDiscoveryResult> discovered) {
    lock.lock();
    try {
      if (finished) {
        throw new IllegalStateException("Cannot append to a finished ArrowColumnStore!");
      }

      final int local = residentRowCount;
      final long global = totalRowCount;
      entries.add(entry);

      if (!properties.isEmpty()) {
        for (final String prop : properties) {
          final var pdr = discovered.get(prop);
          final Object raw = (pdr instanceof StorageInstanceExaminer.Some some) ? some.val() : null;
          setCell(columns.get(prop), local, raw);
        }
      }

      residentRowCount++;
      totalRowCount++;

      if (!properties.isEmpty()) {
        if (!spilling && spillRowLimit > 0L && totalRowCount > spillRowLimit) {
          spilling = true;
          flush();
        } else if (spilling && residentRowCount >= batchSize) {
          flush();
        }
      }

      return global;
    } finally {
      lock.unlock();
    }
  }

  /**
   * Finalises accumulation: if spilling was ever engaged, flushes the remaining resident tail so
   * the store ends up entirely on disk; otherwise fixes the resident vectors' value counts so they
   * may be read directly.
   */
  void finish() {
    lock.lock();
    try {
      if (finished) {
        return;
      }

      if (!properties.isEmpty()) {
        if (spilling) {
          if (residentRowCount > 0) {
            flush();
          }
          closeWriter();
        } else {
          for (final Column c : columns.values()) {
            finalizeColumn(c, residentRowCount);
          }
        }
      }
      finished = true;
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    } finally {
      lock.unlock();
    }
  }

  /**
   * Streams every appended row, resident or spilled, invoking {@code consumer} once per row with a
   * {@link PropertySource} reading that row's planned columns.
   */
  void forEachRow(final RowConsumer consumer) throws Exception {
    if (properties.isEmpty()) {
      for (long i = 0; i < totalRowCount; i++) {
        consumer.accept(i, entries.get((int) i), prop -> new StorageInstanceExaminer.NoValue());
      }
      return;
    }

    if (!spilling) {
      for (int local = 0; local < residentRowCount; local++) {
        final long global = local;
        final int fLocal = local;
        final StorageEntry entry = entries.get(local);
        consumer.accept(
            global, entry, prop -> readCellResident(columns.get(prop), fLocal, entry));
      }
      return;
    }

    try (var channel = Files.newByteChannel(spillFile, StandardOpenOption.READ);
         var reader = new ArrowFileReader(channel, allocator)) {
      long base = 0;
      while (reader.loadNextBatch()) {
        final VectorSchemaRoot batchRoot = reader.getVectorSchemaRoot();
        final int n = batchRoot.getRowCount();
        for (int local = 0; local < n; local++) {
          final long global = base + local;
          final StorageEntry entry = entries.get((int) global);
          final int fLocal = local;
          consumer.accept(
              global,
              entry,
              prop -> readCellFromRoot(batchRoot, prop, fLocal, entry));
        }
        base += n;
      }
    }
  }

  /**
   * Materialises the requested {@code show}/sort columns for a - typically small, already
   * filtered+sorted+limited - set of global row indices, in the same order they were requested.
   * Reads directly from resident vectors, or groups the request by spilled batch and performs
   * random-access reads via {@link ArrowFileReader#loadRecordBatch}.
   */
  List<Map<String, StorageInstanceExaminer.PropertyDiscoveryResult>> materialize(
      final long[] globalRowIndices) throws IOException {
    final int n = globalRowIndices.length;
    final var result = new ArrayList<Map<String, StorageInstanceExaminer.PropertyDiscoveryResult>>(n);
    for (int i = 0; i < n; i++) {
      result.add(null);
    }

    if (properties.isEmpty()) {
      for (int i = 0; i < n; i++) {
        result.set(i, Map.of());
      }
      return result;
    }

    if (!spilling) {
      for (int i = 0; i < n; i++) {
        final int local = (int) globalRowIndices[i];
        final StorageEntry entry = entries.get(local);
        final Map<String, StorageInstanceExaminer.PropertyDiscoveryResult> row = new HashMap<>();
        for (final String prop : properties) {
          row.put(prop, readCellResident(columns.get(prop), local, entry));
        }
        result.set(i, row);
      }
      return result;
    }

    // group requested positions by which spilled batch they fall into:
    final int[] batchStarts = new int[flushedBatchSizes.size()];
    int acc = 0;
    for (int b = 0; b < flushedBatchSizes.size(); b++) {
      batchStarts[b] = acc;
      acc += flushedBatchSizes.get(b);
    }
    final Map<Integer, List<Integer>> positionsByBatch = new LinkedHashMap<>();
    for (int i = 0; i < n; i++) {
      final long g = globalRowIndices[i];
      int batch = 0;
      for (int b = flushedBatchSizes.size() - 1; b >= 0; b--) {
        if (g >= batchStarts[b]) {
          batch = b;
          break;
        }
      }
      positionsByBatch.computeIfAbsent(batch, k -> new ArrayList<>()).add(i);
    }

    try (var channel = Files.newByteChannel(spillFile, StandardOpenOption.READ);
         var reader = new ArrowFileReader(channel, allocator)) {
      final var blocks = reader.getRecordBlocks();
      for (final var e : positionsByBatch.entrySet()) {
        final int batch = e.getKey();
        reader.loadRecordBatch(blocks.get(batch));
        final VectorSchemaRoot root = reader.getVectorSchemaRoot();
        for (final int i : e.getValue()) {
          final int local = (int) (globalRowIndices[i] - batchStarts[batch]);
          final StorageEntry entry = entries.get((int) globalRowIndices[i]);
          final Map<String, StorageInstanceExaminer.PropertyDiscoveryResult> row = new HashMap<>();
          for (final String prop : properties) {
            row.put(prop, readCellFromRoot(root, prop, local, entry));
          }
          result.set(i, row);
        }
      }
    }
    return result;
  }

  @Override
  public void close() {
    lock.lock();
    try {
      closeWriter();
      for (final Column c : columns.values()) {
        closeQuietly(c.integralVector);
        closeQuietly(c.floatingVector);
        closeQuietly(c.stringVector);
        closeQuietly(c.booleanVector);
        closeQuietly(c.jsonVector);
      }
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    } finally {
      lock.unlock();
    }
  }

  /**
   * Detaches ownership of the spill file (if any) from this store, so a caller (the pagination
   * registry) may keep reading it after this store is closed. Returns {@code null} if this store
   * never spilled.
   */
  Path detachSpillFile() {
    final Path f = spillFile;
    spillFile = null;
    return f;
  }

  List<Integer> flushedBatchSizes() {
    return List.copyOf(flushedBatchSizes);
  }

  /**
   * Retroactively spills an entirely-resident store to disk - used when a result needs pagination
   * but never crossed {@link QueryEngineSettings#arrowSpillRowLimit()} on its own. A no-op if the
   * store already spilled, holds no columns, or is empty.
   */
  void forceSpill() {
    lock.lock();
    try {
      if (spilling || properties.isEmpty() || residentRowCount == 0) {
        return;
      }
      spilling = true;
      flush();
      closeWriter();
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    } finally {
      lock.unlock();
    }
  }

  // -- column typing & cell access -------------------------------------------------------------

  private void setCell(final Column c, final int local, final Object raw) {
    if (raw == null) {
      return;
    }

    switch (raw) {
      case String s -> {
        if (c.kind == Kind.UNKNOWN) {
          c.kind = Kind.STRING;
          c.stringVector = allocateVarChar(c.name + FAST_SUFFIX);
        }
        if (c.kind == Kind.STRING) {
          writeString(c.stringVector, local, s);
          return;
        }
      }
      case Boolean b -> {
        if (c.kind == Kind.UNKNOWN) {
          c.kind = Kind.BOOLEAN;
          c.booleanVector = allocateBit(c.name + FAST_SUFFIX);
        }
        if (c.kind == Kind.BOOLEAN) {
          c.booleanVector.setSafe(local, b ? 1 : 0);
          return;
        }
      }
      case Number num -> {
        if (c.kind == Kind.UNKNOWN) {
          if (num instanceof Float || num instanceof Double) {
            c.kind = Kind.FLOATING;
            c.floatingVector = allocateFloat8(c.name + FAST_SUFFIX);
          } else {
            c.kind = Kind.INTEGRAL;
            c.integralVector = allocateBigInt(c.name + FAST_SUFFIX);
          }
        }
        if (c.kind == Kind.INTEGRAL && !(num instanceof Float) && !(num instanceof Double)) {
          c.integralVector.setSafe(local, num.longValue());
          return;
        }
        if (c.kind == Kind.FLOATING && (num instanceof Float || num instanceof Double)) {
          c.floatingVector.setSafe(local, num.doubleValue());
          return;
        }
      }
      default -> {}
    }

    // either a Map/List, or a scalar whose shape doesn't match this column's already-fixed kind:
    ensureJsonVector(c);
    writeJson(c.jsonVector, local, raw);
  }

  private void ensureJsonVector(final Column c) {
    if (c.jsonVector == null) {
      c.jsonVector = allocateVarChar(c.name + JSON_SUFFIX);
    }
  }

  private void finalizeColumn(final Column c, final int rowCount) {
    if (c.kind == Kind.UNKNOWN) {
      c.kind = Kind.STRING;
      c.stringVector = allocateVarChar(c.name + FAST_SUFFIX);
    }
    ensureJsonVector(c);
    c.fastVector().setValueCount(rowCount);
    c.jsonVector.setValueCount(rowCount);
  }

  private static void writeString(final VarCharVector v, final int local, final String s) {
    v.setSafe(local, s.getBytes(StandardCharsets.UTF_8));
  }

  private static void writeJson(final VarCharVector v, final int local, final Object raw) {
    final String json = JsonOutput.toJson(raw);
    v.setSafe(local, json.getBytes(StandardCharsets.UTF_8));
  }

  private StorageInstanceExaminer.PropertyDiscoveryResult readCellResident(
      final Column c, final int local, final StorageEntry entry) {
    if (c.jsonVector != null && !c.jsonVector.isNull(local)) {
      return jsonToPdr(new String(c.jsonVector.get(local), StandardCharsets.UTF_8), entry, c.name);
    }

    final FieldVector fast = c.fastVector();
    if (fast == null || fast.isNull(local)) {
      return new StorageInstanceExaminer.NoValue();
    }
    return switch (c.kind) {
      case STRING -> new StorageInstanceExaminer.StringFound(
          new String(c.stringVector.get(local), StandardCharsets.UTF_8), entry, c.name);
      case INTEGRAL -> new StorageInstanceExaminer.NumberFound(
          c.integralVector.get(local), entry, c.name);
      case FLOATING -> new StorageInstanceExaminer.NumberFound(
          c.floatingVector.get(local), entry, c.name);
      case BOOLEAN -> new StorageInstanceExaminer.BooleanFound(
          c.booleanVector.get(local) != 0, entry, c.name);
      case UNKNOWN -> new StorageInstanceExaminer.NoValue();
    };
  }

  private StorageInstanceExaminer.PropertyDiscoveryResult readCellFromRoot(
      final VectorSchemaRoot root, final String prop, final int local, final StorageEntry entry) {
    return ArrowCellCodec.read(root, prop, local, entry);
  }

  private static StorageInstanceExaminer.PropertyDiscoveryResult jsonToPdr(
      final String json, final StorageEntry entry, final String path) {
    return ArrowCellCodec.jsonToPdr(json, entry, path);
  }

  // -- vector allocation & spill ----------------------------------------------------------------

  private VarCharVector allocateVarChar(final String name) {
    final Field field = new Field(name, FieldType.nullable(Types.MinorType.VARCHAR.getType()), null);
    final var v = (VarCharVector) field.createVector(allocator);
    v.allocateNew();
    return v;
  }

  private BigIntVector allocateBigInt(final String name) {
    final Field field = new Field(name, FieldType.nullable(Types.MinorType.BIGINT.getType()), null);
    final var v = (BigIntVector) field.createVector(allocator);
    v.allocateNew();
    return v;
  }

  private Float8Vector allocateFloat8(final String name) {
    final Field field = new Field(name, FieldType.nullable(Types.MinorType.FLOAT8.getType()), null);
    final var v = (Float8Vector) field.createVector(allocator);
    v.allocateNew();
    return v;
  }

  private BitVector allocateBit(final String name) {
    final Field field = new Field(name, FieldType.nullable(Types.MinorType.BIT.getType()), null);
    final var v = (BitVector) field.createVector(allocator);
    v.allocateNew();
    return v;
  }

  private void flush() {
    try {
      for (final Column c : columns.values()) {
        finalizeColumn(c, residentRowCount);
      }

      if (writer == null) {
        openWriter();
      }
      currentRoot.setRowCount(residentRowCount);
      writer.writeBatch();
      flushedBatchSizes.add(residentRowCount);

      for (final Column c : columns.values()) {
        c.fastVector().reset();
        c.jsonVector.reset();
      }
      residentRowCount = 0;
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private VectorSchemaRoot currentRoot;

  private void openWriter() throws IOException {
    final List<Field> fields = new ArrayList<>();
    final List<FieldVector> vectors = new ArrayList<>();
    for (final Column c : columns.values()) {
      fields.add(c.fastVector().getField());
      vectors.add(c.fastVector());
      fields.add(c.jsonVector.getField());
      vectors.add(c.jsonVector);
    }
    currentRoot = new VectorSchemaRoot(new Schema(fields), vectors, residentRowCount);

    spillFile = (spillDirectory != null)
        ? Files.createTempFile(spillDirectory, "storage-explorer-arrow-", ".arrow")
        : Files.createTempFile("storage-explorer-arrow-", ".arrow");
    writerChannel = Files.newByteChannel(
        spillFile, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
    writer = new ArrowFileWriter(
        currentRoot, new DictionaryProvider.MapDictionaryProvider(), writerChannel);
    writer.start();
  }

  private void closeWriter() throws IOException {
    if (writer != null) {
      writer.end();
      writer.close();
      writer = null;
    }
    if (writerChannel != null) {
      writerChannel.close();
      writerChannel = null;
    }
    if (currentRoot != null) {
      currentRoot.close();
      currentRoot = null;
    }
  }

  private static void closeQuietly(final FieldVector v) {
    if (v != null) {
      v.close();
    }
  }

}

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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.vector.ipc.ArrowFileReader;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.aestallon.storageexplorer.core.model.entry.StorageEntry;
import com.aestallon.storageexplorer.core.service.StorageInstanceExaminer;

/**
 * Registry of paginated {@link ArrowQueryEngine} results.
 *
 * <p>
 * When a query's final (filtered, sorted, limited) row set is larger than one page,
 * {@link ArrowQueryEngine} force-spills its {@link ArrowColumnStore} (if it had not already spilled
 * on its own), detaches the spill file, and {@link #register registers} a handle here instead of
 * keeping the store - and its allocator - alive for the query's whole lifetime. A handle keeps only
 * the bounded, already-resolved final ordering (entries + their global row indices) resident;
 * {@code show} column values for any later page are read back from the spill file on demand via
 * {@link ArrowFileReader#loadRecordBatch}, touching only the batches the requested page actually
 * falls into.
 *
 * <p>
 * Handles expire {@link QueryEngineSettings#arrowResultRetention()} after their last access, at
 * which point their spill file is deleted and their allocator closed.
 */
public final class ArrowResultSetStore {

  private record Handle(
      Path spillFile,
      List<Integer> batchRowCounts,
      List<String> showColumns,
      Map<String, String> displayNames,
      long[] orderedGlobalIndices,
      StorageEntry[] orderedEntries,
      BufferAllocator allocator) {}

  private final Cache<String, Handle> handles;

  ArrowResultSetStore(final Duration retention) {
    this.handles = Caffeine.newBuilder()
        .expireAfterAccess(retention)
        .removalListener((String token, Handle h, RemovalCause cause) -> {
          if (h == null) {
            return;
          }
          try {
            Files.deleteIfExists(h.spillFile());
          } catch (final IOException ignored) {
            // best effort - a leftover temp file is a minor annoyance, not a correctness issue
          }
          h.allocator().close();
        })
        .build();
  }

  /**
   * Registers a handle for later pages of a query result, returning the token clients pass to
   * {@link #fetchPage}.
   *
   * @param showColumns the query's {@code yield}/{@code show} columns, in display order - only
   *     these (a subset of whatever {@link ArrowColumnStore} additionally holds for {@code where}/
   *     {@code order}) are read back per page
   * @param displayNames {@code prop -> title}, as in {@link ArcScriptResult.ColumnDescriptor}
   * @param orderedGlobalIndices the full final row ordering's global indices into the spilled
   *     store's append order
   * @param orderedEntries the corresponding {@link StorageEntry} per final row, same length/order
   */
  String register(final Path spillFile,
                  final List<Integer> batchRowCounts,
                  final List<String> showColumns,
                  final Map<String, String> displayNames,
                  final long[] orderedGlobalIndices,
                  final StorageEntry[] orderedEntries,
                  final BufferAllocator allocator) {
    final String token = UUID.randomUUID().toString();
    handles.put(token, new Handle(
        spillFile, batchRowCounts, showColumns, displayNames,
        orderedGlobalIndices, orderedEntries, allocator));
    return token;
  }

  public int totalRows(final String token) {
    final Handle h = handles.getIfPresent(token);
    return (h == null) ? 0 : h.orderedEntries().length;
  }

  /**
   * Fetches one page of a previously registered result. {@code pageNumber} is 0-based. Returns
   * {@link Optional#empty()} if the token is unknown or has expired.
   */
  public Optional<ArcScriptResult.ResultSet> fetchPage(final String token, final int pageNumber,
                                                       final int pageSize) {
    final Handle h = handles.getIfPresent(token);
    if (h == null) {
      return Optional.empty();
    }

    final var columns = h.showColumns().stream()
        .map(p -> new ArcScriptResult.ColumnDescriptor(p, h.displayNames().getOrDefault(p, p)))
        .toList();
    final int total = h.orderedEntries().length;
    final int size = Math.max(1, pageSize);
    final int from = Math.max(0, pageNumber) * size;
    if (from >= total) {
      return Optional.of(new ArcScriptResult.ResultSet(
          new ArcScriptResult.ResultSetMeta(columns, 0L), List.of()));
    }

    final int to = Math.min(from + size, total);
    final long[] indices = Arrays.copyOfRange(h.orderedGlobalIndices(), from, to);
    final StorageEntry[] pageEntries = Arrays.copyOfRange(h.orderedEntries(), from, to);
    try {
      final var rows = readRows(h, indices, pageEntries);
      return Optional.of(new ArcScriptResult.ResultSet(
          new ArcScriptResult.ResultSetMeta(columns, 0L), rows));
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private List<ArcScriptResult.QueryResultRow> readRows(final Handle h, final long[] globalIndices,
                                                        final StorageEntry[] entriesSlice)
      throws IOException {
    final List<Integer> batchRowCounts = h.batchRowCounts();
    final int[] batchStarts = new int[batchRowCounts.size()];
    int acc = 0;
    for (int b = 0; b < batchRowCounts.size(); b++) {
      batchStarts[b] = acc;
      acc += batchRowCounts.get(b);
    }

    final Map<Integer, List<Integer>> positionsByBatch = new LinkedHashMap<>();
    for (int i = 0; i < globalIndices.length; i++) {
      final long g = globalIndices[i];
      int batch = 0;
      for (int b = batchStarts.length - 1; b >= 0; b--) {
        if (g >= batchStarts[b]) {
          batch = b;
          break;
        }
      }
      positionsByBatch.computeIfAbsent(batch, k -> new ArrayList<>()).add(i);
    }

    final var rows = new ArcScriptResult.QueryResultRow[globalIndices.length];
    try (var channel = Files.newByteChannel(h.spillFile(), StandardOpenOption.READ);
         var reader = new ArrowFileReader(channel, h.allocator())) {
      final var blocks = reader.getRecordBlocks();
      for (final var e : positionsByBatch.entrySet()) {
        final int batch = e.getKey();
        reader.loadRecordBatch(blocks.get(batch));
        final var root = reader.getVectorSchemaRoot();
        final int batchStart = batchStarts[batch];
        for (final int i : e.getValue()) {
          final int local = (int) (globalIndices[i] - batchStart);
          final StorageEntry entry = entriesSlice[i];
          final Map<String, ArcScriptResult.DataCell> cells = new HashMap<>();
          for (final String prop : h.showColumns()) {
            cells.put(prop, toDataCell(ArrowCellCodec.read(root, prop, local, entry)));
          }
          rows[i] = new ArcScriptResult.QueryResultRow(entry, cells);
        }
      }
    }
    return Arrays.asList(rows);
  }

  private static ArcScriptResult.DataCell toDataCell(
      final StorageInstanceExaminer.PropertyDiscoveryResult pdr) {
    return switch (pdr) {
      case StorageInstanceExaminer.None none -> ArcScriptResult.DataCell.noValue();
      case StorageInstanceExaminer.Some some -> ArcScriptResult.DataCell.of(some.val());
    };
  }

}

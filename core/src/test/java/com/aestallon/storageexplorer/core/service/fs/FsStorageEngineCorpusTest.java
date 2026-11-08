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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import com.aestallon.storageexplorer.core.model.EntryUri;
import com.aestallon.storageexplorer.core.model.loading.IndexingTarget;
import com.aestallon.storageexplorer.core.service.engine.EngineResult;
import com.aestallon.storageexplorer.core.service.engine.StorageEngineError;

/**
 * Smoke test over a real storage tree. Not run by ordinary CI — it needs a real storage folder on
 * disk, pointed at by the {@code SB4A_FS_CORPUS} environment variable; without it, the test is
 * skipped (reported as "aborted", not failed). {@code SB4A_FS_CORPUS_HISTORY=1} also reads every
 * older version of every object (slower).
 *
 * <pre>
 * SB4A_FS_CORPUS=/path/to/a/real/storage ./gradlew core:test --tests "...FsStorageEngineCorpusTest"
 * </pre>
 *
 * <p>Mirrors the {@code sb4a-fs} Rust library's own {@code tests/corpus.rs} smoke test, including
 * its cross-check that {@link FsStorageEngine#list} finds exactly the object files a plain
 * recursive walk finds.
 */
class FsStorageEngineCorpusTest {

  private static void objectFiles(final Path dir, final List<Path> out) {
    filesWithExtension(dir, ".o", out);
  }

  private static void filesWithExtension(final Path dir, final String extension, final List<Path> out) {
    try (Stream<Path> entries = Files.list(dir)) {
      for (final Path path : entries.toList()) {
        if (Files.isDirectory(path)) {
          filesWithExtension(path, extension, out);
        } else if (path.getFileName().toString().endsWith(extension)) {
          out.add(path);
        }
      }
    } catch (final IOException ignored) {
      // matches the Rust test's own fs::read_dir(...).flatten() — an unreadable dir contributes
      // nothing rather than failing the whole walk.
    }
  }

  @Test
  void everyObjectOfTheCorpusReads() {
    final String root = System.getenv("SB4A_FS_CORPUS");
    assumeTrue(root != null, "SB4A_FS_CORPUS is not set, nothing to do");
    final boolean withHistory = "1".equals(System.getenv("SB4A_FS_CORPUS_HISTORY"));

    final Path rootPath = Path.of(root);
    final FsStorageEngine storage;
    switch (FsStorageEngine.open(rootPath)) {
      case EngineResult.Ok<FsStorageEngine> ok -> storage = ok.value();
      case EngineResult.Err<FsStorageEngine> err -> throw new AssertionError(err.error().toString());
    }

    final List<Path> files = new ArrayList<>();
    objectFiles(rootPath, files);

    int objects = 0;
    int single = 0;
    int indexed = 0;
    int withRelations = 0;
    int encrypted = 0;
    long pending = 0;
    long versions = 0;
    long noData = 0;
    final List<String> failures = new ArrayList<>();

    for (final Path file : files) {
      final Optional<EntryUri> maybeUri = storage.root().uriOfObjectFile(file);
      if (maybeUri.isEmpty()) {
        failures.add(file + ": not an object file path");
        continue;
      }
      final EntryUri uri = maybeUri.get();
      objects++;
      final Head head;
      switch (storage.head(uri)) {
        case EngineResult.Ok<Head> ok -> head = ok.value();
        case EngineResult.Err<Head> err -> {
          failures.add(uri + ": head: " + err.error());
          continue;
        }
      }
      if (head.pendingTransaction()) {
        pending++;
      }
      if (uri.isSingleVersion()) {
        single++;
      }
      if (head.descriptor().isPresent()
          && head.descriptor().get().strategy() instanceof EngineResult.Ok<Strategy> ok
          && ok.value() == Strategy.INDEXED) {
        indexed++;
      }

      switch (storage.readStored(uri.latest())) {
        case EngineResult.Ok<Entity> ok -> {
          versions++;
          if (!"none".equals(ok.value().encryptionRule())) {
            encrypted++;
          }
        }
        case EngineResult.Err<Entity> err -> {
          if (err.error() instanceof StorageEngineError.NoData) {
            noData++;
          } else {
            failures.add(uri + ": read: " + err.error());
          }
        }
      }

      if (!uri.isSingleVersion()) {
        switch (storage.relations(uri)) {
          case EngineResult.Ok<Optional<Relations>> ok -> {
            if (ok.value().isPresent()) {
              withRelations++;
            }
          }
          case EngineResult.Err<Optional<Relations>> err -> failures.add(uri + ": relations: " + err.error());
        }
        if (withHistory) {
          switch (storage.history(uri)) {
            case EngineResult.Ok<FsStorageEngine.History> ok -> {
              final FsStorageEngine.History history = ok.value();
              while (history.hasNext()) {
                switch (history.next()) {
                  case EngineResult.Ok<Entity> ignored -> versions++;
                  case EngineResult.Err<Entity> err -> failures.add(uri + ": history: " + err.error());
                }
              }
            }
            case EngineResult.Err<FsStorageEngine.History> err -> failures.add(uri + ": history: " + err.error());
          }
        }
      }
    }

    // the listing must find exactly the object files that the plain walk found
    int listed = 0;
    try (Stream<Path> schemes = Files.list(rootPath)) {
      for (final Path scheme : schemes.toList()) {
        if (!Files.isDirectory(scheme)) {
          continue;
        }
        final String name = scheme.getFileName().toString();
        try (Stream<FsStorageEngine.Listed> items =
            storage.list(new IndexingTarget(java.util.Set.of(name), java.util.Set.of()))) {
          for (final FsStorageEngine.Listed item : items.toList()) {
            if (item.kind() == FileSystemStorageWalker.Kind.OBJECT) {
              listed++;
            }
          }
        }
      }
    } catch (final IOException e) {
      failures.add("listing schemes: " + e.getMessage());
    }
    if (listed != files.size()) {
      failures.add("the listing found " + listed + " objects, the walk found " + files.size());
    }

    // every table data file must decode, every object stream must read up to its head position
    final List<Path> tables = new ArrayList<>();
    filesWithExtension(rootPath, ".td", tables);
    int tableRows = 0;
    int streams = 0;
    int streamEntries = 0;
    for (final Path file : tables) {
      switch (TableData.readFile(file)) {
        case EngineResult.Ok<Optional<TableData>> ok -> {
          if (ok.value().isPresent()) {
            tableRows += ok.value().get().rows().size();
          }
        }
        case EngineResult.Err<Optional<TableData>> err -> failures.add(file + ": " + err.error());
      }
    }
    for (final Path file : files) {
      final Optional<EntryUri> maybeUri = storage.root().uriOfObjectFile(file);
      if (maybeUri.isEmpty()) {
        continue;
      }
      final EntryUri uri = maybeUri.get();
      final List<StreamRef> objectStreams;
      switch (storage.streams(uri)) {
        case EngineResult.Ok<List<StreamRef>> ok -> objectStreams = ok.value();
        case EngineResult.Err<List<StreamRef>> ignored -> objectStreams = List.of();
      }
      for (final StreamRef stream : objectStreams) {
        switch (storage.readStream(uri, stream.name())) {
          case EngineResult.Ok<StreamScan> ok -> {
            streams++;
            streamEntries += ok.value().entries().size();
          }
          case EngineResult.Err<StreamScan> err ->
              failures.add(uri + ": stream " + stream.name() + ": " + err.error());
        }
      }
    }

    System.out.printf(
        "%d table data files (%d rows), %d object streams (%d entries)%n",
        tables.size(), tableRows, streams, streamEntries);
    System.out.printf(
        "%d objects (%d single version, %d indexed, %d with relations, %d encrypted, %d without "
            + "data, %d with a pending .t), %d versions read%n",
        objects, single, indexed, withRelations, encrypted, noData, pending, versions);
    for (final String failure : failures.subList(0, Math.min(40, failures.size()))) {
      System.out.println("FAILED " + failure);
    }
    assertTrue(failures.isEmpty(), failures.size() + " failures");
  }
}

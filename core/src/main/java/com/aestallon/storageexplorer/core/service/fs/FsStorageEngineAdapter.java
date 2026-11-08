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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.aestallon.storageexplorer.core.model.EntryKind;
import com.aestallon.storageexplorer.core.model.EntryUri;
import com.aestallon.storageexplorer.core.model.loading.IndexingTarget;
import com.aestallon.storageexplorer.core.service.engine.ContentHandle;
import com.aestallon.storageexplorer.core.service.engine.EngineResult;
import com.aestallon.storageexplorer.core.service.engine.EntryMetadata;
import com.aestallon.storageexplorer.core.service.engine.LoadedContent;
import com.aestallon.storageexplorer.core.service.engine.StorageEngine;
import com.aestallon.storageexplorer.core.service.engine.StorageEngineError;
import com.aestallon.storageexplorer.core.service.engine.VersionedContent;

/**
 * Adapts {@link FsStorageEngine} (T1.10's finished, FS-internal-detail-returning implementation)
 * to the shared {@link StorageEngine} contract (declared once {@link ContentHandle} existed —
 * T2.1 — per the sequencing correction recorded against T1.1/T1.10).
 *
 * <p>This is a separate class rather than {@code FsStorageEngine implements StorageEngine}
 * directly because {@code FsStorageEngine} already has its own {@code head}/{@code read}/
 * {@code list} methods (T1.6/T1.8), returning its richer, FS-internal types (built and tested
 * across all of Milestone 1) — methods of those names with {@code StorageEngine}'s shared-type
 * signatures would conflict with them, not override them (the return types aren't covariant).
 * Rather than rename already-tested Milestone 1 API, this adapter does the translation Milestone
 * 1's overview originally described happening inline — just realized as a wrapping layer instead,
 * found necessary while starting Milestone 3's T3.1.
 */
final class FsStorageEngineAdapter implements StorageEngine {

  private static final ObjectMapper MAPPER = new ObjectMapper()
      .configure(DeserializationFeature.USE_LONG_FOR_INTS, true);

  private final FsStorageEngine engine;

  FsStorageEngineAdapter(final FsStorageEngine engine) {
    this.engine = engine;
  }

  @Override
  public void close() {
    // nothing of its own to release - every FS access is a short-lived file open/close pair,
    // never a held connection/pool.
  }

  @Override
  public boolean exists(final EntryUri uri) {
    return engine.exists(uri);
  }

  @Override
  public Optional<Path> pathOf(final EntryUri uri) {
    return engine.pathOf(uri);
  }

  @Override
  public EngineResult<ContentHandle> read(final EntryUri uri) {
    // exists() is a bare file-presence stat - no content read - so a missing entry still fails
    // fast and synchronously here, exactly as before. For anything that DOES exist, the actual
    // read (locating/opening the version file, decrypting, materializing) is deferred all the way
    // to the returned handle's openStream()/materialize() - this is the point ContentHandle was
    // introduced for in the first place; engine.read(uri) used to be called right here, eagerly,
    // which defeated it.
    if (!engine.exists(uri)) {
      return new EngineResult.Err<>(new StorageEngineError.NotFound(uri.toString()));
    }
    return new EngineResult.Ok<>(new LazyEntityContentHandle(uri));
  }

  @Override
  public EngineResult<EntryMetadata> head(final EntryUri uri) {
    return switch (engine.head(uri)) {
      case EngineResult.Ok<Head> ok -> new EngineResult.Ok<>(toMetadata(ok.value()));
      case EngineResult.Err<Head> err -> new EngineResult.Err<>(err.error());
    };
  }

  @Override
  public Stream<Listed> list(final IndexingTarget target) {
    return engine.list(target).map(listed -> new Listed(listed.uri(), toEntryKind(listed.kind())));
  }

  @Override
  public EngineResult<Committed> newVersion(final EntryUri uri, final byte[] payload) {
    return switch (engine.newVersion(uri, payload)) {
      case EngineResult.Ok<com.aestallon.storageexplorer.core.service.fs.Committed> ok ->
          new EngineResult.Ok<>(new Committed(ok.value().serial()));
      case EngineResult.Err<com.aestallon.storageexplorer.core.service.fs.Committed> err ->
          new EngineResult.Err<>(err.error());
    };
  }

  @Override
  public EngineResult<Void> amend(final EntryUri uri, final long serial, final byte[] payload,
                                   final boolean touchHead) {
    return engine.amend(uri, serial, payload, new AmendOptions(touchHead));
  }

  /** Unlike {@link #read}, this one's per-version read is <strong>not</strong> deferred: {@link
   *  FsStorageEngine.History#next()} reads a version's header and payload together (the classic
   *  multipart/indexed-slot formats interleave them in the same file, with no cheap way to read
   *  one without the other), and the per-version {@link EntryMetadata} this method returns is
   *  read off that same header - so getting the metadata every {@code VersionedContent} carries
   *  unavoidably means reading every version's payload too, today. A real fix needs a header-only
   *  partial read added to the multipart/indexed-file format itself (reading just the first
   *  length-prefixed part via a seek instead of {@code FsUtil.readFile}'s whole-file read) - a
   *  deeper, separate change from {@link #read}'s fix, not attempted here. In practice this is a
   *  smaller concern than {@link #read}'s own eagerness: callers of this method browse a specific
   *  entry's version history one entry at a time (Swing's multi-tab inspector, T7.1), not across
   *  a whole storage, and the one entry kind whose payload can be large by design ({@code
   *  BinaryDataObject}) never reaches this method in the first place — the inspector factory
   *  routes it to a dedicated binary-stream download instead of the generic version browser. */
  @Override
  public EngineResult<List<VersionedContent>> history(final EntryUri uri) {
    final Head head;
    switch (engine.head(uri)) {
      case EngineResult.Ok<Head> ok -> head = ok.value();
      case EngineResult.Err<Head> err -> {
        return new EngineResult.Err<>(err.error());
      }
    }
    final String className = head.descriptor()
        .flatMap(d -> okOrNull(d.className()).orElse(Optional.empty()))
        .orElse(null);

    final FsStorageEngine.History history;
    switch (engine.history(uri)) {
      case EngineResult.Ok<FsStorageEngine.History> ok -> history = ok.value();
      case EngineResult.Err<FsStorageEngine.History> err -> {
        return new EngineResult.Err<>(err.error());
      }
    }

    final List<VersionedContent> versions = new ArrayList<>();
    while (history.hasNext()) {
      switch (history.next()) {
        case EngineResult.Ok<Entity> ok -> {
          final Entity entity = ok.value();
          final long serial = entity.serial().orElse(-1L);
          versions.add(new VersionedContent(
              serial,
              toMetadata(uri, className, entity),
              new EntityContentHandle(entity)));
        }
        case EngineResult.Err<Entity> err -> {
          return new EngineResult.Err<>(err.error());
        }
      }
    }
    return new EngineResult.Ok<>(versions);
  }

  /** A version's own header fields, as read off its {@link Entity} record — the per-version
   *  analog of {@link #toMetadata(Head)} (which only ever describes the current head). */
  private EntryMetadata toMetadata(final EntryUri uri, final String className, final Entity entity) {
    final Long versionNr = entity.serial().isPresent() ? entity.serial().getAsLong() : null;
    final OffsetDateTime createdAt = entity.version()
        .flatMap(v -> okOrNull(v.createdAt()).orElse(Optional.empty()))
        .map(FsStorageEngineAdapter::parseCreatedAt)
        .orElse(null);
    return new EntryMetadata(uri, className, versionNr, createdAt, null, null);
  }

  private static EntryKind toEntryKind(final FileSystemStorageWalker.Kind kind) {
    return switch (kind) {
      case OBJECT -> EntryKind.OBJECT;
      case TABLE_DATA -> EntryKind.TABLE_DATA;
      case OBJECT_STREAM -> EntryKind.OBJECT_STREAM;
    };
  }

  /** Best-effort: a field this engine can't account for (an absent descriptor, an unreadable
   *  sub-value) is reported as {@code null}, never failed — metadata is advisory. */
  private EntryMetadata toMetadata(final Head head) {
    final Optional<ObjectData> descriptor = head.descriptor();
    if (descriptor.isEmpty()) {
      return new EntryMetadata(head.uri(), null, null, null, null, null);
    }
    final ObjectData data = descriptor.get();
    final String qualifiedName = okOrNull(data.className()).orElse(Optional.empty()).orElse(null);
    final Optional<ObjectVersion> version = okOrNull(data.currentVersion()).orElse(Optional.empty());
    final Long versionNr = version.flatMap(v -> okOrNull(v.serialNoData()).orElse(Optional.empty()))
        .orElse(null);
    final OffsetDateTime createdAt = version
        .flatMap(v -> okOrNull(v.createdAt()).orElse(Optional.empty()))
        .map(FsStorageEngineAdapter::parseCreatedAt)
        .orElse(null);
    return new EntryMetadata(head.uri(), qualifiedName, versionNr, createdAt, null, null);
  }

  private static OffsetDateTime parseCreatedAt(final String text) {
    try {
      return OffsetDateTime.parse(text);
    } catch (final DateTimeParseException e) {
      return null;
    }
  }

  private static <T> Optional<T> okOrNull(final EngineResult<T> result) {
    return (result instanceof EngineResult.Ok<T> ok) ? Optional.ofNullable(ok.value()) : Optional.empty();
  }

  /** Materializes a plain JSON object — the only shape {@code FsStorageEngine.read} ever serves
   *  today (object-stream/table-data content goes through its own dedicated methods, not yet
   *  wired into this adapter; see {@link StorageEngine}'s own javadoc on growing it as needed).
   *
   * <p>Wraps an {@link Entity} that's <em>already been read</em> — used by {@link #history}, whose
   * own per-version metadata (createdAt/versionNr) is unavoidably read from the very same version
   * file as the payload (the format has no cheap way to read one without the other; see {@link
   * #history}'s own javadoc). {@link #read} uses {@link LazyEntityContentHandle} instead, which
   * defers that read entirely. */
  private record EntityContentHandle(Entity entity) implements ContentHandle {

    @Override
    public InputStream openStream() {
      return new ByteArrayInputStream(entity.payload());
    }

    @Override
    public LoadedContent materialize() {
      return materializeJson(entity.payload());
    }
  }

  /** Defers the actual read — locating the version file/slot, decrypting, reading its bytes — to
   *  {@link #openStream()}/{@link #materialize()}, resolving {@link #engine}{@code .read(uri)} at
   *  most once (memoized; concurrent callers block on the same in-flight read rather than each
   *  triggering their own, same discipline as {@code StorageInstanceExaminer
   *  .ObjectEntryLookupTable}). This is the handle {@link FsStorageEngineAdapter#read} hands out:
   *  by the time a caller has one, no disk I/O beyond the bare existence check has happened yet. */
  private final class LazyEntityContentHandle implements ContentHandle {

    private final EntryUri uri;
    private final ReentrantLock lock = new ReentrantLock();
    private volatile EngineResult<Entity> resolved;

    private LazyEntityContentHandle(final EntryUri uri) {
      this.uri = uri;
    }

    private EngineResult<Entity> resolve() {
      final var present = resolved;
      if (present != null) {
        return present;
      }

      lock.lock();
      try {
        if (resolved == null) {
          resolved = engine.read(uri);
        }
        return resolved;
      } finally {
        lock.unlock();
      }
    }

    @Override
    public InputStream openStream() {
      return new LazyStream();
    }

    @Override
    public LoadedContent materialize() {
      return switch (resolve()) {
        case EngineResult.Ok<Entity> ok -> materializeJson(ok.value().payload());
        // ContentHandle#materialize() has no failure channel (T2.1) - a deferred read that fails
        // (not found anymore, missing encryption key) renders as empty rather than throwing, same
        // as an unparsable payload always has:
        case EngineResult.Err<Entity> err -> new LoadedContent.JsonObject(new LinkedHashMap<>());
      };
    }

    /** Resolves on the first actual byte read, not on {@link #openStream()} itself (which never
     *  fails) - a failed resolution surfaces as {@link IOException}, exactly where every existing
     *  {@code openStream()} caller already expects to catch one. */
    private final class LazyStream extends InputStream {

      private InputStream delegate;

      private InputStream delegate() throws IOException {
        if (delegate == null) {
          delegate = switch (resolve()) {
            case EngineResult.Ok<Entity> ok -> new ByteArrayInputStream(ok.value().payload());
            case EngineResult.Err<Entity> err -> throw new IOException(
                "failed to read content at [ " + uri + " ]: " + err.error());
          };
        }
        return delegate;
      }

      @Override
      public int read() throws IOException {
        return delegate().read();
      }

      @Override
      public int read(final byte[] b, final int off, final int len) throws IOException {
        return delegate().read(b, off, len);
      }

      @Override
      public int available() throws IOException {
        return delegate().available();
      }

      @Override
      public void close() throws IOException {
        if (delegate != null) {
          delegate.close();
        }
      }
    }
  }

  @SuppressWarnings("unchecked")
  private static LoadedContent materializeJson(final byte[] payload) {
    try {
      final LinkedHashMap<String, Object> data = MAPPER.readValue(payload, LinkedHashMap.class);
      return new LoadedContent.JsonObject(data);
    } catch (final IOException e) {
      // ContentHandle#materialize() has no failure channel (T2.1) - an unparsable payload (e.g. a
      // non-JSON binary entry) renders as empty rather than throwing.
      return new LoadedContent.JsonObject(new LinkedHashMap<>());
    }
  }
}

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
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.stream.Stream;
import com.aestallon.storageexplorer.core.model.EntryUri;
import com.aestallon.storageexplorer.core.model.instance.dto.FsStorageLocation;
import com.aestallon.storageexplorer.core.model.loading.IndexingTarget;
import com.aestallon.storageexplorer.core.service.engine.EngineResult;
import com.aestallon.storageexplorer.core.service.engine.StorageEngineError;

/**
 * A storage folder (one sub-folder per scheme): addressing an entry by URI, getting its head, its
 * versions, their history and relations (the read side); creating an object, adding a version and
 * redirecting the head, or amending the content of an existing version (the write side).
 *
 * <p>Nothing in the read side locks. The descriptor ({@code .o}) is replaced atomically by
 * writers, so a reader sees either the old or the new one; the version files are immutable. A
 * leftover {@code .t} next to the {@code .o} is only reported ({@link Head#pendingTransaction()}),
 * the committed {@code .o} is what gets served.
 *
 * <p>Every write holds the object lock ({@link ObjectLock}) for its whole duration and refuses to
 * start if a {@code .t} file is next to the {@code .o} (a write is in flight or crashed; resolving
 * that is not this engine's job). The order of a new version is what keeps readers safe: relation
 * file, data version file, and only then the descriptor, replaced atomically (temp file in the
 * same folder + rename). Until that last step the head still points to the previous version, so a
 * crash anywhere earlier leaves a consistent object and at most an unreferenced file that the next
 * write over the same serial overwrites.
 *
 * <p>This class was assembled incrementally across Milestone 1's tasks (read path T1.6, write
 * path T1.7, listing T1.8, cross-verification T1.9, this final assembly pass T1.10) and is now
 * functionally complete — every method the implementation plan's overview lists for the
 * {@code StorageEngine} interface exists here (plus {@code create}, which the read/write path
 * needed but the overview's own list omitted). It still does not {@code implements StorageEngine}
 * — that interface isn't declarable until {@code ContentHandle} exists (T2.1, Milestone 2); this
 * is a deliberate sequencing deferral, not an oversight, matching the correction already recorded
 * against T1.1. Nothing in `core` references this class yet either — that wiring into
 * {@code StorageInstance} happens in Milestone 4, alongside the breaking swap that deletes the
 * old {@code FileSystemStorageIndex}/platform-{@code ObjectApi} path this replaces.
 *
 * <p>Ported from the {@code sb4a-fs} Rust library's {@code store} module's read half
 * ({@code ../sb4a-fs/crates/sb4a-fs/src/store.rs}) and the {@code write} module
 * ({@code ../sb4a-fs/crates/sb4a-fs/src/write.rs}), plus {@code objectstream.rs}/{@code
 * tabledata.rs} for {@link #readStream}/{@link #readTableData}.
 */
public final class FsStorageEngine {

  private final StorageRoot root;
  private final WriteOptions writeOptions;

  private FsStorageEngine(final StorageRoot root, final WriteOptions writeOptions) {
    this.root = root;
    this.writeOptions = writeOptions;
  }

  /** Opens the storage at the folder, which must exist. */
  public static EngineResult<FsStorageEngine> open(final Path rootPath) {
    try {
      final boolean isDir = Files.readAttributes(rootPath, BasicFileAttributes.class).isDirectory();
      if (!isDir) {
        return new EngineResult.Err<>(
            new StorageEngineError.Format(rootPath, "the storage root is not a folder"));
      }
      return new EngineResult.Ok<>(new FsStorageEngine(new StorageRoot(rootPath), WriteOptions.defaults()));
    } catch (final IOException e) {
      return new EngineResult.Err<>(new StorageEngineError.Io(rootPath, e));
    }
  }

  /** Opens the storage at the location's path. Convenience over {@link #open(Path)} for callers
   *  that already have a {@link FsStorageLocation} (e.g. {@code StorageInstance} construction,
   *  Milestone 4). */
  public static EngineResult<FsStorageEngine> open(final FsStorageLocation location) {
    return open(location.getPath());
  }

  /** Replaces the write behaviour (lock policy, creator, runtime id). */
  public FsStorageEngine withWriteOptions(final WriteOptions options) {
    return new FsStorageEngine(root, options);
  }

  public StorageRoot root() {
    return root;
  }

  /** This engine, viewed through the shared {@code StorageEngine} contract — see
   *  {@link FsStorageEngineAdapter}'s own javadoc for why this is a wrapper, not {@code
   *  implements StorageEngine} on this class directly. */
  public com.aestallon.storageexplorer.core.service.engine.StorageEngine asStorageEngine() {
    return new FsStorageEngineAdapter(this);
  }

  /** Whether the object exists (its {@code .o} is there). */
  public boolean exists(final EntryUri uri) {
    return Files.isRegularFile(root.objectFile(uri));
  }

  /**
   * The path of the entry's head descriptor on disk — deterministic from its address alone,
   * never conditioned on the file actually existing (matching the removed
   * {@code StorageEntry.path()}'s own semantics). Backs "reveal in system file explorer"-style
   * UI; a future RDB-backed {@code StorageEngine} always returns empty here.
   */
  public Optional<Path> pathOf(final EntryUri uri) {
    return Optional.of(root.objectFile(uri.latest()));
  }

  /** One item of a {@link #list} listing: an addressed entry, kind-tagged. */
  public record Listed(EntryUri uri, FileSystemStorageWalker.Kind kind) {}

  /**
   * Lists every persisted entry under this storage (or a schema/type-filtered subset of it) —
   * object descriptors, table data, and object streams — lazily: items come out as the tree is
   * read, in no particular order. Backed by {@link FileSystemStorageWalker} (moved into this
   * package and fixed up in T1.8 — see its class javadoc for why it recurses unconditionally
   * rather than stopping once a folder yields an entry). The returned stream must be consumed or
   * closed, like the walker's own.
   */
  public Stream<Listed> list(final IndexingTarget target) {
    return FileSystemStorageWalker.of(root.path().toAbsolutePath())
        .walkAll(target)
        .flatMap(found -> entryUriOf(found).map(uri -> new Listed(uri, found.kind())).stream());
  }

  /** The {@link EntryUri} a discovered file addresses, built from its path relative to the
   *  storage root — {@code <scheme>/<segments.../><stem><suffix>}, the suffix stripped exactly
   *  per {@link FileSystemStorageWalker.Kind}, not blindly. */
  private Optional<EntryUri> entryUriOf(final FileSystemStorageWalker.Found found) {
    final Path relative = root.path().toAbsolutePath().relativize(found.file());
    final int nameCount = relative.getNameCount();
    if (nameCount < 2) {
      return Optional.empty();
    }
    final String stem = stripSuffix(relative.getFileName().toString(), found.kind());
    if (stem == null || stem.isEmpty()) {
      return Optional.empty();
    }
    final String scheme = relative.getName(0).toString();
    final List<String> segments = new ArrayList<>(nameCount - 1);
    for (int i = 1; i < nameCount - 1; i++) {
      segments.add(relative.getName(i).toString());
    }
    segments.add(stem);
    return EntryUri.fromSegments(scheme, segments);
  }

  private static String stripSuffix(final String fileName, final FileSystemStorageWalker.Kind kind) {
    final String suffix = switch (kind) {
      case OBJECT -> ".o";
      case TABLE_DATA -> ".td";
      case OBJECT_STREAM -> ".os";
    };
    return fileName.endsWith(suffix) ? fileName.substring(0, fileName.length() - suffix.length()) : null;
  }

  /** The head of the object. */
  public EngineResult<Head> head(final EntryUri uri) {
    final Loaded loaded;
    switch (loadObjectFile(uri)) {
      case EngineResult.Ok<Loaded> ok -> loaded = ok.value();
      case EngineResult.Err<Loaded> err -> {
        return new EngineResult.Err<>(err.error());
      }
    }
    final Optional<ObjectData> descriptor = switch (loaded) {
      case Loaded.Versioned v -> Optional.of(v.descriptor());
      case Loaded.Single s -> s.descriptor();
    };
    return new EngineResult.Ok<>(new Head(uri, descriptor, Files.exists(root.transactionFile(uri))));
  }

  /**
   * Reads a version (the head if the URI has no version) and returns its payload ready to use.
   * Fails with {@link StorageEngineError.MissingKey} if the payload is encrypted.
   */
  public EngineResult<Entity> read(final EntryUri uri) {
    final Entity entity;
    switch (readStored(uri)) {
      case EngineResult.Ok<Entity> ok -> entity = ok.value();
      case EngineResult.Err<Entity> err -> {
        return new EngineResult.Err<>(err.error());
      }
    }
    if (!JsonDocuments.NO_ENCRYPTION.equals(entity.encryptionRule())) {
      return new EngineResult.Err<>(new StorageEngineError.MissingKey(entity.encryptionRule()));
    }
    return new EngineResult.Ok<>(entity);
  }

  /** Reads a version without interpreting the payload: it is returned exactly as stored. */
  public EngineResult<Entity> readStored(final EntryUri uri) {
    final Loaded loaded;
    switch (loadObjectFile(uri.latest())) {
      case EngineResult.Ok<Loaded> ok -> loaded = ok.value();
      case EngineResult.Err<Loaded> err -> {
        return new EngineResult.Err<>(err.error());
      }
    }
    if (loaded instanceof Loaded.Single single) {
      if (uri.version().isPresent()) {
        return new EngineResult.Err<>(new StorageEngineError.Unsupported(
            uri.latest() + " is a single version object, it has no versions to address"));
      }
      final String encryptionRule;
      if (single.descriptor().isPresent()) {
        switch (single.descriptor().get().encryptionRule()) {
          case EngineResult.Ok<String> ok -> encryptionRule = ok.value();
          case EngineResult.Err<String> err -> {
            return new EngineResult.Err<>(err.error());
          }
        }
      } else {
        encryptionRule = JsonDocuments.NO_ENCRYPTION;
      }
      final Optional<ObjectVersion> version;
      if (single.descriptor().isPresent()) {
        switch (single.descriptor().get().currentVersion()) {
          case EngineResult.Ok<Optional<ObjectVersion>> ok -> version = ok.value();
          case EngineResult.Err<Optional<ObjectVersion>> err -> {
            return new EngineResult.Err<>(err.error());
          }
        }
      } else {
        version = Optional.empty();
      }
      return new EngineResult.Ok<>(new Entity(OptionalLong.empty(), version, single.payload(), encryptionRule));
    }

    final ObjectData descriptor = ((Loaded.Versioned) loaded).descriptor();
    final long headSerial;
    switch (headDataSerial(descriptor, uri.latest())) {
      case EngineResult.Ok<Long> ok -> headSerial = ok.value();
      case EngineResult.Err<Long> err -> {
        return new EngineResult.Err<>(err.error());
      }
    }
    final long serial = uri.version().orElse(headSerial);
    if (serial > headSerial) {
      // A newer slot or file may exist (an uncommitted or rolled back write), but a version
      // only exists once the head points to it.
      return new EngineResult.Err<>(new StorageEngineError.NotFound(
          uri.latest().withVersion(serial) + " (the head is at version " + headSerial + ")"));
    }
    return readDataVersion(uri.latest(), descriptor, serial);
  }

  /** Reads one data version of an object by serial, as stored. */
  public EngineResult<Entity> readVersion(final EntryUri uri, final long serial) {
    return readStored(uri.latest().withVersion(serial));
  }

  /** The data versions of the object from the first to the head, oldest first. */
  public EngineResult<History> history(final EntryUri uri) {
    final Loaded loaded;
    switch (loadObjectFile(uri)) {
      case EngineResult.Ok<Loaded> ok -> loaded = ok.value();
      case EngineResult.Err<Loaded> err -> {
        return new EngineResult.Err<>(err.error());
      }
    }
    if (!(loaded instanceof Loaded.Versioned versioned)) {
      return new EngineResult.Err<>(new StorageEngineError.Unsupported(
          uri + " is a single version object, it has no history"));
    }
    final ObjectData descriptor = versioned.descriptor();
    final Optional<ObjectVersion> currentVersion;
    switch (descriptor.currentVersion()) {
      case EngineResult.Ok<Optional<ObjectVersion>> ok -> currentVersion = ok.value();
      case EngineResult.Err<Optional<ObjectVersion>> err -> {
        return new EngineResult.Err<>(err.error());
      }
    }
    long end = 0;
    if (currentVersion.isPresent()) {
      switch (currentVersion.get().serialNoData()) {
        case EngineResult.Ok<Optional<Long>> ok -> end = ok.value().map(s -> s + 1).orElse(0L);
        case EngineResult.Err<Optional<Long>> err -> {
          return new EngineResult.Err<>(err.error());
        }
      }
    }
    return new EngineResult.Ok<>(new History(this, uri, descriptor, 0, end));
  }

  /** The relations of the head version; empty if the head has none. */
  public EngineResult<Optional<Relations>> relations(final EntryUri uri) {
    final Loaded loaded;
    switch (loadObjectFile(uri)) {
      case EngineResult.Ok<Loaded> ok -> loaded = ok.value();
      case EngineResult.Err<Loaded> err -> {
        return new EngineResult.Err<>(err.error());
      }
    }
    if (!(loaded instanceof Loaded.Versioned versioned)) {
      return new EngineResult.Ok<>(Optional.empty());
    }
    final Optional<ObjectVersion> currentVersion;
    switch (versioned.descriptor().currentVersion()) {
      case EngineResult.Ok<Optional<ObjectVersion>> ok -> currentVersion = ok.value();
      case EngineResult.Err<Optional<ObjectVersion>> err -> {
        return new EngineResult.Err<>(err.error());
      }
    }
    if (currentVersion.isEmpty()) {
      return new EngineResult.Ok<>(Optional.empty());
    }
    final Optional<Long> relationSerial;
    switch (currentVersion.get().serialNoRelation()) {
      case EngineResult.Ok<Optional<Long>> ok -> relationSerial = ok.value();
      case EngineResult.Err<Optional<Long>> err -> {
        return new EngineResult.Err<>(err.error());
      }
    }
    if (relationSerial.isEmpty()) {
      return new EngineResult.Ok<>(Optional.empty());
    }
    return switch (relationsAt(uri, relationSerial.get())) {
      case EngineResult.Ok<Relations> ok -> new EngineResult.Ok<>(Optional.of(ok.value()));
      case EngineResult.Err<Relations> err -> new EngineResult.Err<>(err.error());
    };
  }

  /** The relation file with the given relation serial. */
  public EngineResult<Relations> relationsAt(final EntryUri uri, final long serial) {
    final Path path = root.relationFile(uri, serial);
    final Optional<List<byte[]>> parts;
    switch (Multipart.readFile(path)) {
      case EngineResult.Ok<Optional<List<byte[]>>> ok -> parts = ok.value();
      case EngineResult.Err<Optional<List<byte[]>>> err -> {
        return new EngineResult.Err<>(err.error());
      }
    }
    if (parts.isEmpty()) {
      return new EngineResult.Err<>(new StorageEngineError.NotFound(uri + " relation version " + serial));
    }
    final List<byte[]> p = parts.get();
    if (p.size() < 2) {
      return new EngineResult.Err<>(tooFewParts(path, p.size()));
    }
    final ObjectVersion version;
    switch (ObjectVersion.parse(p.get(0))) {
      case EngineResult.Ok<ObjectVersion> ok -> version = ok.value();
      case EngineResult.Err<ObjectVersion> err -> {
        return new EngineResult.Err<>(err.error().at(path));
      }
    }
    final RelationData data;
    switch (RelationData.parse(p.get(1))) {
      case EngineResult.Ok<RelationData> ok -> data = ok.value();
      case EngineResult.Err<RelationData> err -> {
        return new EngineResult.Err<>(err.error().at(path));
      }
    }
    return new EngineResult.Ok<>(new Relations(serial, version, data));
  }

  /** The object streams of the object ({@code objectStreams} of its descriptor). */
  public EngineResult<List<StreamRef>> streams(final EntryUri uri) {
    final Head head;
    switch (head(uri)) {
      case EngineResult.Ok<Head> ok -> head = ok.value();
      case EngineResult.Err<Head> err -> {
        return new EngineResult.Err<>(err.error());
      }
    }
    if (head.descriptor().isEmpty()) {
      return new EngineResult.Ok<>(List.of());
    }
    return head.descriptor().get().objectStreams();
  }

  /** The entries of the named object stream, up to the head position the descriptor gives. */
  public EngineResult<StreamScan> readStream(final EntryUri uri, final String name) {
    final List<StreamRef> streamRefs;
    switch (streams(uri)) {
      case EngineResult.Ok<List<StreamRef>> ok -> streamRefs = ok.value();
      case EngineResult.Err<List<StreamRef>> err -> {
        return new EngineResult.Err<>(err.error());
      }
    }
    final Optional<StreamRef> found = streamRefs.stream().filter(s -> s.name().equals(name)).findFirst();
    if (found.isEmpty()) {
      return new EngineResult.Err<>(new StorageEngineError.NotFound(
          uri + " has no object stream `" + name + "`"));
    }
    final StreamRef stream = found.get();
    final Path path = root.streamFile(uri, name);
    if (stream.headPosition() == 0 && !Files.exists(path)) {
      // announced but never written
      return new EngineResult.Ok<>(new StreamScan(List.of(), 0, 0));
    }
    final Optional<StreamScan> scan;
    switch (ObjectStream.readFile(path, OptionalLong.of(stream.headPosition()))) {
      case EngineResult.Ok<Optional<StreamScan>> ok -> scan = ok.value();
      case EngineResult.Err<Optional<StreamScan>> err -> {
        return new EngineResult.Err<>(err.error());
      }
    }
    if (scan.isEmpty()) {
      return new EngineResult.Err<>(new StorageEngineError.NotFound(
          uri + " object stream `" + name + "` (" + path + ")"));
    }
    return new EngineResult.Ok<>(scan.get());
  }

  /** The serialized table data stored under the URI ({@code <scheme>/<path>.td}). */
  public EngineResult<TableData> readTableData(final EntryUri uri) {
    final Path path = root.tableDataFile(uri);
    final Optional<TableData> table;
    switch (TableData.readFile(path)) {
      case EngineResult.Ok<Optional<TableData>> ok -> table = ok.value();
      case EngineResult.Err<Optional<TableData>> err -> {
        return new EngineResult.Err<>(err.error());
      }
    }
    if (table.isEmpty()) {
      return new EngineResult.Err<>(new StorageEngineError.NotFound(uri + " (no table data file)"));
    }
    return new EngineResult.Ok<>(table.get());
  }

  // --- write path --------------------------------------------------------------------------

  /**
   * Creates a new versioned object with its first version (serial 0).
   *
   * <p>{@code indexSize} is the number of versions per {@code .iv} file for the indexed layout
   * (the platform uses {@value StorageRoot#DEFAULT_INDEX_SIZE}) and ignored for the classic one.
   */
  public EngineResult<Committed> create(final EntryUri uri, final String className, final Strategy strategy,
                                         final Integer indexSize, final byte[] payload) {
    if (uri.isSingleVersion()) {
      return new EngineResult.Err<>(new StorageEngineError.Unsupported("creating single version objects"));
    }
    final ObjectLock lock;
    switch (lock(uri)) {
      case EngineResult.Ok<ObjectLock> ok -> lock = ok.value();
      case EngineResult.Err<ObjectLock> err -> {
        return new EngineResult.Err<>(err.error());
      }
    }
    try {
      if (refusePending(uri) instanceof EngineResult.Err<Void> err) {
        return new EngineResult.Err<>(err.error());
      }
      if (Files.exists(root.objectFile(uri))) {
        return new EngineResult.Err<>(new StorageEngineError.AlreadyExists(uri.toString()));
      }
      final Integer effectiveIndexSize = (strategy == Strategy.INDEXED)
          ? ((indexSize != null) ? indexSize : StorageRoot.DEFAULT_INDEX_SIZE)
          : null;
      final ObjectData descriptor = ObjectData.of(uri.toString(), className, strategy, effectiveIndexSize);
      final ObjectVersion version = stampVersion(Optional.empty(), 0, OptionalLong.empty(),
          JsonDocuments.NO_ENCRYPTION);
      if (writeDataVersion(uri, descriptor, 0, version, payload) instanceof EngineResult.Err<Void> err) {
        return new EngineResult.Err<>(err.error());
      }
      if (FailPoint.check("before-descriptor") instanceof EngineResult.Err<Void> err) {
        return new EngineResult.Err<>(err.error());
      }
      descriptor.setCurrentVersion(version);
      if (FsUtil.writeFileAtomic(root.objectFile(uri), descriptor.toBytes()) instanceof EngineResult.Err<Void> err) {
        return new EngineResult.Err<>(err.error());
      }
      return new EngineResult.Ok<>(new Committed(OptionalLong.of(0), Optional.of(version)));
    } finally {
      lock.close();
    }
  }

  /**
   * Adds a new data version with the payload and redirects the head to it. A single-version
   * object just gets its payload replaced.
   */
  public EngineResult<Committed> newVersion(final EntryUri uri, final byte[] payload) {
    if (requireExisting(uri) instanceof EngineResult.Err<Void> err) {
      return new EngineResult.Err<>(err.error());
    }
    final ObjectLock lock;
    switch (lock(uri)) {
      case EngineResult.Ok<ObjectLock> ok -> lock = ok.value();
      case EngineResult.Err<ObjectLock> err -> {
        return new EngineResult.Err<>(err.error());
      }
    }
    try {
      if (refusePending(uri) instanceof EngineResult.Err<Void> err) {
        return new EngineResult.Err<>(err.error());
      }
      final Loaded loaded;
      switch (loadObjectFile(uri)) {
        case EngineResult.Ok<Loaded> ok -> loaded = ok.value();
        case EngineResult.Err<Loaded> err -> {
          return new EngineResult.Err<>(err.error());
        }
      }
      if (loaded instanceof Loaded.Single) {
        return replaceSingle(uri, payload);
      }
      final ObjectData descriptor = ((Loaded.Versioned) loaded).descriptor();
      final Optional<ObjectVersion> currentOpt;
      switch (descriptor.currentVersion()) {
        case EngineResult.Ok<Optional<ObjectVersion>> ok -> currentOpt = ok.value();
        case EngineResult.Err<Optional<ObjectVersion>> err -> {
          return new EngineResult.Err<>(err.error());
        }
      }
      if (currentOpt.isEmpty()) {
        return new EngineResult.Err<>(new StorageEngineError.NoData(uri.toString()));
      }
      final ObjectVersion current = currentOpt.get();
      final String rule;
      switch (current.encryptionRule()) {
        case EngineResult.Ok<String> ok -> rule = ok.value();
        case EngineResult.Err<String> err -> {
          return new EngineResult.Err<>(err.error());
        }
      }
      if (!JsonDocuments.NO_ENCRYPTION.equals(rule)) {
        // writing needs the key too (encryption support itself is out of scope)
        return new EngineResult.Err<>(new StorageEngineError.MissingKey(rule));
      }
      final Optional<Long> currentSerialNoData;
      switch (current.serialNoData()) {
        case EngineResult.Ok<Optional<Long>> ok -> currentSerialNoData = ok.value();
        case EngineResult.Err<Optional<Long>> err -> {
          return new EngineResult.Err<>(err.error());
        }
      }
      final long serial = currentSerialNoData.map(s -> s + 1).orElse(0L);

      // The relations of the head travel with every new version.
      final Optional<Long> oldRelationSerial;
      switch (current.serialNoRelation()) {
        case EngineResult.Ok<Optional<Long>> ok -> oldRelationSerial = ok.value();
        case EngineResult.Err<Optional<Long>> err -> {
          return new EngineResult.Err<>(err.error());
        }
      }
      byte[] relationData = null;
      OptionalLong newRelationSerial = OptionalLong.empty();
      if (oldRelationSerial.isPresent()) {
        final long old = oldRelationSerial.get();
        switch (relationsPart(uri, old)) {
          case EngineResult.Ok<byte[]> ok -> relationData = ok.value();
          case EngineResult.Err<byte[]> err -> {
            return new EngineResult.Err<>(err.error());
          }
        }
        newRelationSerial = OptionalLong.of(old + 1);
      }
      final ObjectVersion version = stampVersion(Optional.of(current), serial, newRelationSerial, rule);

      // 1. the relation file, 2. the data version, 3. the descriptor
      if (newRelationSerial.isPresent()) {
        final Path path = root.relationFile(uri, newRelationSerial.getAsLong());
        if (Multipart.writeFile(path, List.of(version.toBytes(), relationData))
            instanceof EngineResult.Err<Void> err) {
          return new EngineResult.Err<>(err.error());
        }
      }
      if (FailPoint.check("after-relation-file") instanceof EngineResult.Err<Void> err) {
        return new EngineResult.Err<>(err.error());
      }
      if (writeDataVersion(uri, descriptor, serial, version, payload) instanceof EngineResult.Err<Void> err) {
        return new EngineResult.Err<>(err.error());
      }
      if (FailPoint.check("before-descriptor") instanceof EngineResult.Err<Void> err) {
        return new EngineResult.Err<>(err.error());
      }
      descriptor.setCurrentVersion(version);
      if (FsUtil.writeFileAtomic(root.objectFile(uri), descriptor.toBytes()) instanceof EngineResult.Err<Void> err) {
        return new EngineResult.Err<>(err.error());
      }
      return new EngineResult.Ok<>(new Committed(OptionalLong.of(serial), Optional.of(version)));
    } finally {
      lock.close();
    }
  }

  /**
   * Replaces the payload of an existing data version (not necessarily the head), in place: the
   * version record stays as it is, the head is not redirected.
   */
  public EngineResult<Void> amend(final EntryUri uri, final long serial, final byte[] payload,
                                   final AmendOptions options) {
    if (requireExisting(uri) instanceof EngineResult.Err<Void> err) {
      return new EngineResult.Err<>(err.error());
    }
    final ObjectLock lock;
    switch (lock(uri)) {
      case EngineResult.Ok<ObjectLock> ok -> lock = ok.value();
      case EngineResult.Err<ObjectLock> err -> {
        return new EngineResult.Err<>(err.error());
      }
    }
    try {
      if (refusePending(uri) instanceof EngineResult.Err<Void> err) {
        return new EngineResult.Err<>(err.error());
      }
      final Loaded loaded;
      switch (loadObjectFile(uri)) {
        case EngineResult.Ok<Loaded> ok -> loaded = ok.value();
        case EngineResult.Err<Loaded> err -> {
          return new EngineResult.Err<>(err.error());
        }
      }
      if (loaded instanceof Loaded.Single) {
        // a single version object has only one content: amending it is replacing it
        return switch (replaceSingle(uri, payload)) {
          case EngineResult.Ok<Committed> ok -> new EngineResult.Ok<>(null);
          case EngineResult.Err<Committed> err -> new EngineResult.Err<>(err.error());
        };
      }
      final ObjectData descriptor = ((Loaded.Versioned) loaded).descriptor();
      final Optional<ObjectVersion> currentVersion;
      switch (descriptor.currentVersion()) {
        case EngineResult.Ok<Optional<ObjectVersion>> ok -> currentVersion = ok.value();
        case EngineResult.Err<Optional<ObjectVersion>> err -> {
          return new EngineResult.Err<>(err.error());
        }
      }
      Long head = null;
      if (currentVersion.isPresent()) {
        switch (currentVersion.get().serialNoData()) {
          case EngineResult.Ok<Optional<Long>> ok -> head = ok.value().orElse(null);
          case EngineResult.Err<Optional<Long>> err -> {
            return new EngineResult.Err<>(err.error());
          }
        }
      }
      if (head == null) {
        return new EngineResult.Err<>(new StorageEngineError.NoData(uri.toString()));
      }
      if (serial > head) {
        return new EngineResult.Err<>(new StorageEngineError.NotFound(
            uri.withVersion(serial) + " (the head is at version " + head + ")"));
      }

      final Strategy strategy;
      switch (descriptor.strategy()) {
        case EngineResult.Ok<Strategy> ok -> strategy = ok.value();
        case EngineResult.Err<Strategy> err -> {
          return new EngineResult.Err<>(err.error());
        }
      }
      final Integer indexSize;
      switch (descriptor.indexSize()) {
        case EngineResult.Ok<Optional<Integer>> ok -> indexSize = ok.value().orElse(null);
        case EngineResult.Err<Optional<Integer>> err -> {
          return new EngineResult.Err<>(err.error());
        }
      }
      final VersionLocation location = root.locateVersion(uri, serial, strategy, indexSize);
      if (location instanceof VersionLocation.Classic classic) {
        final Path file = classic.file();
        final Optional<List<byte[]>> partsOpt;
        switch (Multipart.readFile(file)) {
          case EngineResult.Ok<Optional<List<byte[]>>> ok -> partsOpt = ok.value();
          case EngineResult.Err<Optional<List<byte[]>>> err -> {
            return new EngineResult.Err<>(err.error());
          }
        }
        if (partsOpt.isEmpty()) {
          return new EngineResult.Err<>(new StorageEngineError.NotFound(uri.withVersion(serial).toString()));
        }
        final List<byte[]> parts;
        switch (replacePayload(partsOpt.get(), payload, file)) {
          case EngineResult.Ok<List<byte[]>> ok -> parts = ok.value();
          case EngineResult.Err<List<byte[]>> err -> {
            return new EngineResult.Err<>(err.error());
          }
        }
        if (Multipart.writeFile(file, parts) instanceof EngineResult.Err<Void> err) {
          return new EngineResult.Err<>(err.error());
        }
      } else {
        final VersionLocation.Indexed indexed = (VersionLocation.Indexed) location;
        final Path file = indexed.file();
        final Optional<IndexedFile> openedOpt;
        switch (IndexedFile.openRw(file, indexed.slots())) {
          case EngineResult.Ok<Optional<IndexedFile>> ok -> openedOpt = ok.value();
          case EngineResult.Err<Optional<IndexedFile>> err -> {
            return new EngineResult.Err<>(err.error());
          }
        }
        if (openedOpt.isEmpty()) {
          return new EngineResult.Err<>(new StorageEngineError.NotFound(uri.withVersion(serial).toString()));
        }
        try (IndexedFile indexedFile = openedOpt.get()) {
          final Optional<List<byte[]>> slotPartsOpt;
          switch (indexedFile.readSlot(indexed.slot())) {
            case EngineResult.Ok<Optional<List<byte[]>>> ok -> slotPartsOpt = ok.value();
            case EngineResult.Err<Optional<List<byte[]>>> err -> {
              return new EngineResult.Err<>(err.error());
            }
          }
          if (slotPartsOpt.isEmpty()) {
            return new EngineResult.Err<>(new StorageEngineError.NotFound(uri.withVersion(serial).toString()));
          }
          final List<byte[]> parts;
          switch (replacePayload(slotPartsOpt.get(), payload, file)) {
            case EngineResult.Ok<List<byte[]>> ok -> parts = ok.value();
            case EngineResult.Err<List<byte[]>> err -> {
              return new EngineResult.Err<>(err.error());
            }
          }
          if (indexedFile.writeSlot(indexed.slot(), parts) instanceof EngineResult.Err<Void> err) {
            return new EngineResult.Err<>(err.error());
          }
        } catch (final IOException e) {
          return new EngineResult.Err<>(new StorageEngineError.Io(file, e));
        }
      }

      if (FailPoint.check("after-amend") instanceof EngineResult.Err<Void> err) {
        return new EngineResult.Err<>(err.error());
      }

      if (options.touchHead()) {
        final Path headFile = root.objectFile(uri);
        try {
          Files.setLastModifiedTime(headFile,
              java.nio.file.attribute.FileTime.from(java.time.Instant.now()));
        } catch (final IOException e) {
          return new EngineResult.Err<>(new StorageEngineError.Io(headFile, e));
        }
      }
      return new EngineResult.Ok<>(null);
    } finally {
      lock.close();
    }
  }

  EngineResult<Loaded> loadObjectFile(final EntryUri uri) {
    final Path path = root.objectFile(uri);
    final Optional<byte[]> bytesOpt;
    switch (FsUtil.readFile(path)) {
      case EngineResult.Ok<Optional<byte[]>> ok -> bytesOpt = ok.value();
      case EngineResult.Err<Optional<byte[]>> err -> {
        return new EngineResult.Err<>(err.error());
      }
    }
    if (bytesOpt.isEmpty()) {
      return new EngineResult.Err<>(new StorageEngineError.NotFound(uri.toString()));
    }
    final byte[] bytes = bytesOpt.get();
    if (!uri.isSingleVersion()) {
      return switch (ObjectData.parse(bytes)) {
        case EngineResult.Ok<ObjectData> ok -> new EngineResult.Ok<>(new Loaded.Versioned(ok.value()));
        case EngineResult.Err<ObjectData> err -> new EngineResult.Err<>(err.error().at(path));
      };
    }
    final List<byte[]> parts;
    switch (Multipart.parse(bytes)) {
      case EngineResult.Ok<List<byte[]>> ok -> parts = ok.value();
      case EngineResult.Err<List<byte[]>> err -> {
        return new EngineResult.Err<>(err.error().at(path));
      }
    }
    if (parts.size() < 2) {
      return new EngineResult.Err<>(tooFewParts(path, parts.size()));
    }
    final byte[] descriptorBytes = parts.get(0);
    final Optional<ObjectData> descriptor;
    if (descriptorBytes.length == 0) {
      descriptor = Optional.empty();
    } else {
      switch (ObjectData.parse(descriptorBytes)) {
        case EngineResult.Ok<ObjectData> ok -> descriptor = Optional.of(ok.value());
        case EngineResult.Err<ObjectData> err -> {
          return new EngineResult.Err<>(err.error().at(path));
        }
      }
    }
    return new EngineResult.Ok<>(new Loaded.Single(descriptor, parts.get(1)));
  }

  EngineResult<Entity> readDataVersion(final EntryUri uri, final ObjectData descriptor, final long serial) {
    final Strategy strategy;
    switch (descriptor.strategy()) {
      case EngineResult.Ok<Strategy> ok -> strategy = ok.value();
      case EngineResult.Err<Strategy> err -> {
        return new EngineResult.Err<>(err.error());
      }
    }
    final Integer indexSize;
    switch (descriptor.indexSize()) {
      case EngineResult.Ok<Optional<Integer>> ok -> indexSize = ok.value().orElse(null);
      case EngineResult.Err<Optional<Integer>> err -> {
        return new EngineResult.Err<>(err.error());
      }
    }
    final VersionLocation location = root.locateVersion(uri, serial, strategy, indexSize);
    final Path file;
    final Optional<List<byte[]>> parts;
    if (location instanceof VersionLocation.Classic classic) {
      file = classic.file();
      switch (Multipart.readFile(file)) {
        case EngineResult.Ok<Optional<List<byte[]>>> ok -> parts = ok.value();
        case EngineResult.Err<Optional<List<byte[]>>> err -> {
          return new EngineResult.Err<>(err.error());
        }
      }
    } else {
      final VersionLocation.Indexed indexed = (VersionLocation.Indexed) location;
      file = indexed.file();
      final Optional<IndexedFile> opened;
      switch (IndexedFile.open(file, indexed.slots())) {
        case EngineResult.Ok<Optional<IndexedFile>> ok -> opened = ok.value();
        case EngineResult.Err<Optional<IndexedFile>> err -> {
          return new EngineResult.Err<>(err.error());
        }
      }
      if (opened.isEmpty()) {
        parts = Optional.empty();
      } else {
        try (IndexedFile indexedFile = opened.get()) {
          switch (indexedFile.readSlot(indexed.slot())) {
            case EngineResult.Ok<Optional<List<byte[]>>> ok -> parts = ok.value();
            case EngineResult.Err<Optional<List<byte[]>>> err -> {
              return new EngineResult.Err<>(err.error());
            }
          }
        } catch (final IOException e) {
          return new EngineResult.Err<>(new StorageEngineError.Io(file, e));
        }
      }
    }
    if (parts.isEmpty()) {
      return new EngineResult.Err<>(new StorageEngineError.NotFound(uri.withVersion(serial).toString()));
    }
    final List<byte[]> p = parts.get();
    if (p.size() < 2) {
      return new EngineResult.Err<>(tooFewParts(file, p.size()));
    }
    final ObjectVersion version;
    switch (ObjectVersion.parse(p.get(0))) {
      case EngineResult.Ok<ObjectVersion> ok -> version = ok.value();
      case EngineResult.Err<ObjectVersion> err -> {
        return new EngineResult.Err<>(err.error().at(file));
      }
    }
    final String encryptionRule;
    switch (version.encryptionRule()) {
      case EngineResult.Ok<String> ok -> encryptionRule = ok.value();
      case EngineResult.Err<String> err -> {
        return new EngineResult.Err<>(err.error());
      }
    }
    return new EngineResult.Ok<>(new Entity(OptionalLong.of(serial), Optional.of(version), p.get(1), encryptionRule));
  }

  /** The serial of the data version the head points to. */
  private static EngineResult<Long> headDataSerial(final ObjectData descriptor, final EntryUri uri) {
    final Optional<ObjectVersion> currentVersion;
    switch (descriptor.currentVersion()) {
      case EngineResult.Ok<Optional<ObjectVersion>> ok -> currentVersion = ok.value();
      case EngineResult.Err<Optional<ObjectVersion>> err -> {
        return new EngineResult.Err<>(err.error());
      }
    }
    if (currentVersion.isEmpty()) {
      return new EngineResult.Err<>(new StorageEngineError.NoData(uri.toString()));
    }
    final Optional<Long> serialNoData;
    switch (currentVersion.get().serialNoData()) {
      case EngineResult.Ok<Optional<Long>> ok -> serialNoData = ok.value();
      case EngineResult.Err<Optional<Long>> err -> {
        return new EngineResult.Err<>(err.error());
      }
    }
    if (serialNoData.isEmpty()) {
      return new EngineResult.Err<>(new StorageEngineError.NoData(uri.toString()));
    }
    return new EngineResult.Ok<>(serialNoData.get());
  }

  private static StorageEngineError tooFewParts(final Path path, final int found) {
    return new StorageEngineError.Format(path, "expected 2 parts, found " + found);
  }

  // --- write-path helpers -------------------------------------------------------------------

  private EngineResult<ObjectLock> lock(final EntryUri uri) {
    final LockOwner owner = new LockOwner(writeOptions.runtimeId(), "");
    return ObjectLock.acquire(root.lockFile(uri), owner, writeOptions.lock());
  }

  /** A cheap check before locking, so that a typo does not litter the storage with lock files.
   *  The real check is made again under the lock. */
  private EngineResult<Void> requireExisting(final EntryUri uri) {
    if (Files.isRegularFile(root.objectFile(uri))) {
      return new EngineResult.Ok<>(null);
    }
    return new EngineResult.Err<>(new StorageEngineError.NotFound(uri.toString()));
  }

  private EngineResult<Void> refusePending(final EntryUri uri) {
    final Path pending = root.transactionFile(uri);
    if (Files.exists(pending)) {
      return new EngineResult.Err<>(new StorageEngineError.InDoubt(pending));
    }
    return new EngineResult.Ok<>(null);
  }

  /** A version record for the new version. Aspects are carried over from the previous version. */
  private ObjectVersion stampVersion(final Optional<ObjectVersion> previous, final long dataSerial,
                                      final OptionalLong relationSerial, final String encryptionRule) {
    final String transactionId = UUID.randomUUID().toString();
    final ObjectVersion version = ObjectVersion.of(transactionId, Timestamp.now(), writeOptions.createdBy());
    version.setSerialNoData(dataSerial);
    version.setSerialNoRelation(relationSerial.isPresent() ? relationSerial.getAsLong() : null);
    version.setEncryptionRule(encryptionRule);
    if (previous.isPresent()) {
      final Object aspects = previous.get().raw().get("aspects");
      if (aspects != null) {
        version.raw().put("aspects", aspects);
      }
    }
    return version;
  }

  /** The relation data part of the relation file {@code serial}, to be copied into the next one. */
  private EngineResult<byte[]> relationsPart(final EntryUri uri, final long serial) {
    final Path path = root.relationFile(uri, serial);
    final Optional<List<byte[]>> partsOpt;
    switch (Multipart.readFile(path)) {
      case EngineResult.Ok<Optional<List<byte[]>>> ok -> partsOpt = ok.value();
      case EngineResult.Err<Optional<List<byte[]>>> err -> {
        return new EngineResult.Err<>(err.error());
      }
    }
    if (partsOpt.isEmpty()) {
      return new EngineResult.Err<>(new StorageEngineError.NotFound(uri + " relation version " + serial));
    }
    final List<byte[]> parts = partsOpt.get();
    if (parts.size() < 2) {
      return new EngineResult.Err<>(tooFewParts(path, parts.size()));
    }
    return new EngineResult.Ok<>(parts.get(1));
  }

  private EngineResult<Void> writeDataVersion(final EntryUri uri, final ObjectData descriptor, final long serial,
                                               final ObjectVersion version, final byte[] payload) {
    final Strategy strategy;
    switch (descriptor.strategy()) {
      case EngineResult.Ok<Strategy> ok -> strategy = ok.value();
      case EngineResult.Err<Strategy> err -> {
        return new EngineResult.Err<>(err.error());
      }
    }
    final Integer indexSize;
    switch (descriptor.indexSize()) {
      case EngineResult.Ok<Optional<Integer>> ok -> indexSize = ok.value().orElse(null);
      case EngineResult.Err<Optional<Integer>> err -> {
        return new EngineResult.Err<>(err.error());
      }
    }
    final List<byte[]> parts = List.of(version.toBytes(), payload);
    final VersionLocation location = root.locateVersion(uri, serial, strategy, indexSize);
    if (location instanceof VersionLocation.Classic classic) {
      return Multipart.writeFile(classic.file(), parts);
    }
    final VersionLocation.Indexed indexed = (VersionLocation.Indexed) location;
    final IndexedFile file;
    switch (IndexedFile.openOrCreate(indexed.file(), indexed.slots())) {
      case EngineResult.Ok<IndexedFile> ok -> file = ok.value();
      case EngineResult.Err<IndexedFile> err -> {
        return new EngineResult.Err<>(err.error());
      }
    }
    try (file) {
      return file.writeSlot(indexed.slot(), parts);
    } catch (final IOException e) {
      return new EngineResult.Err<>(new StorageEngineError.Io(indexed.file(), e));
    }
  }

  private EngineResult<Committed> replaceSingle(final EntryUri uri, final byte[] payload) {
    final Path path = root.objectFile(uri);
    final Optional<byte[]> bytesOpt;
    switch (FsUtil.readFile(path)) {
      case EngineResult.Ok<Optional<byte[]>> ok -> bytesOpt = ok.value();
      case EngineResult.Err<Optional<byte[]>> err -> {
        return new EngineResult.Err<>(err.error());
      }
    }
    if (bytesOpt.isEmpty()) {
      return new EngineResult.Err<>(new StorageEngineError.NotFound(uri.toString()));
    }
    final List<byte[]> parts;
    switch (Multipart.parse(bytesOpt.get())) {
      case EngineResult.Ok<List<byte[]>> ok -> parts = new ArrayList<>(ok.value());
      case EngineResult.Err<List<byte[]>> err -> {
        return new EngineResult.Err<>(err.error().at(path));
      }
    }
    if (parts.size() < 2) {
      return new EngineResult.Err<>(tooFewParts(path, parts.size()));
    }
    if (parts.get(0).length > 0) {
      final ObjectData descriptor;
      switch (ObjectData.parse(parts.get(0))) {
        case EngineResult.Ok<ObjectData> ok -> descriptor = ok.value();
        case EngineResult.Err<ObjectData> err -> {
          return new EngineResult.Err<>(err.error().at(path));
        }
      }
      final String rule;
      switch (descriptor.encryptionRule()) {
        case EngineResult.Ok<String> ok -> rule = ok.value();
        case EngineResult.Err<String> err -> {
          return new EngineResult.Err<>(err.error());
        }
      }
      if (!JsonDocuments.NO_ENCRYPTION.equals(rule)) {
        return new EngineResult.Err<>(new StorageEngineError.MissingKey(rule));
      }
    }
    parts.set(1, payload);
    if (FailPoint.check("before-descriptor") instanceof EngineResult.Err<Void> err) {
      return new EngineResult.Err<>(err.error());
    }
    if (Multipart.writeFile(path, parts) instanceof EngineResult.Err<Void> err) {
      return new EngineResult.Err<>(err.error());
    }
    return new EngineResult.Ok<>(new Committed(OptionalLong.empty(), Optional.empty()));
  }

  /** Replaces part 1 (the payload), keeping every other part. Refuses an encrypted version. */
  private static EngineResult<List<byte[]>> replacePayload(final List<byte[]> parts, final byte[] payload,
                                                             final Path file) {
    if (parts.size() < 2) {
      return new EngineResult.Err<>(tooFewParts(file, parts.size()));
    }
    final String rule;
    switch (ObjectVersion.parse(parts.get(0))) {
      case EngineResult.Ok<ObjectVersion> ok -> {
        final EngineResult<String> ruleResult = ok.value().encryptionRule();
        if (ruleResult instanceof EngineResult.Err<String> err) {
          return new EngineResult.Err<>(err.error());
        }
        rule = ((EngineResult.Ok<String>) ruleResult).value();
      }
      case EngineResult.Err<ObjectVersion> err -> {
        return new EngineResult.Err<>(err.error().at(file));
      }
    }
    if (!JsonDocuments.NO_ENCRYPTION.equals(rule)) {
      return new EngineResult.Err<>(new StorageEngineError.MissingKey(rule));
    }
    final List<byte[]> replaced = new ArrayList<>(parts);
    replaced.set(1, payload);
    return new EngineResult.Ok<>(replaced);
  }

  /** The data versions of an object, see {@link FsStorageEngine#history}. Reads lazily: each
   *  item is loaded when the iterator reaches it, not all up front. */
  public static final class History implements Iterator<EngineResult<Entity>> {

    private final FsStorageEngine engine;
    private final EntryUri uri;
    private final ObjectData descriptor;
    private long next;
    private final long end;

    private History(final FsStorageEngine engine, final EntryUri uri, final ObjectData descriptor,
                     final long next, final long end) {
      this.engine = engine;
      this.uri = uri;
      this.descriptor = descriptor;
      this.next = next;
      this.end = end;
    }

    @Override
    public boolean hasNext() {
      return next < end;
    }

    @Override
    public EngineResult<Entity> next() {
      if (!hasNext()) {
        throw new NoSuchElementException();
      }
      final long serial = next;
      next += 1;
      return engine.readDataVersion(uri, descriptor, serial);
    }

    /** How many versions are left to read. */
    public long remaining() {
      return end - next;
    }
  }
}

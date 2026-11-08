package com.aestallon.storageexplorer.core.model.instance;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;
import com.aestallon.storageexplorer.core.model.EntryUri;
import com.aestallon.storageexplorer.core.model.entry.GodObjectEntry;
import com.aestallon.storageexplorer.core.model.entry.ObjectEntry;
import com.aestallon.storageexplorer.core.model.entry.ScopedEntry;
import com.aestallon.storageexplorer.core.model.entry.StorageEntry;
import com.aestallon.storageexplorer.core.model.entry.StorageEntryFactory;
import com.aestallon.storageexplorer.core.model.instance.dto.Availability;
import com.aestallon.storageexplorer.core.model.instance.dto.AvailabilityClassifier;
import com.aestallon.storageexplorer.core.model.instance.dto.FsStorageLocation;
import com.aestallon.storageexplorer.core.model.instance.dto.IndexingStrategyType;
import com.aestallon.storageexplorer.core.model.instance.dto.SqlStorageLocation;
import com.aestallon.storageexplorer.core.model.instance.dto.StorageId;
import com.aestallon.storageexplorer.core.model.instance.dto.StorageInstanceDto;
import com.aestallon.storageexplorer.core.model.instance.dto.StorageInstanceType;
import com.aestallon.storageexplorer.core.model.instance.dto.StorageLocation;
import com.aestallon.storageexplorer.core.model.loading.IndexingTarget;
import com.aestallon.storageexplorer.core.model.type.EntityType;
import com.aestallon.storageexplorer.core.model.type.StructuredType;
import com.aestallon.storageexplorer.core.service.engine.ContentHandle;
import com.aestallon.storageexplorer.core.service.engine.EngineResult;
import com.aestallon.storageexplorer.core.service.engine.LoadedContent;
import com.aestallon.storageexplorer.core.service.engine.StorageEngine;
import com.aestallon.storageexplorer.core.service.engine.VersionedContent;
import com.aestallon.storageexplorer.core.service.index.AdjacencyAware;
import com.aestallon.storageexplorer.core.service.index.MetadataAware;
import com.aestallon.storageexplorer.core.service.index.QueryableIndex;
import com.aestallon.storageexplorer.core.service.index.impl.CompositeQueryableIndex;
import com.aestallon.storageexplorer.core.service.index.impl.HeaderMetadataIndex;
import com.aestallon.storageexplorer.core.service.index.impl.StreamingAdjacencyIndex;
import com.aestallon.storageexplorer.core.service.index.impl.TreeBookmarkIndex;
import com.aestallon.storageexplorer.core.service.index.result.ContentLoadResult;
import com.aestallon.storageexplorer.core.service.index.result.EntryResolution;
import com.aestallon.storageexplorer.core.util.ObjectMaps;

/**
 * A storage this tool knows about: its own identity/configuration (name, {@link Availability},
 * {@link StorageLocation}), the one long-lived {@link TreeBookmarkIndex} it keeps forever (per
 * the decisions log's §3 decision 2 / §9), and a thin, domain-shaped public API on top of the
 * held {@link StorageEngine} — command objects and {@link StorageEntry}/result-typed returns,
 * never {@code StorageEngine}'s own raw {@link EntryUri}/serial/{@code byte[]} signatures
 * directly. {@code client}/{@code swing}/{@code app-cli}/{@code spring} (Milestones 5–7) call
 * only this class; {@code engine} itself stays {@code core}-internal (T4.3).
 *
 * <p>Per the settled access-path decision (Milestone 1's overview), the operation-scoped factory
 * methods ({@link #newGraphIndex()}, {@link #newStatsIndex()}) hand the held {@code engine} —
 * not {@code this}/a reference back to this instance — to the Milestone 3 implementation they
 * construct: those implementations are {@code core}-internal code that talks to the low-level
 * interface directly, skipping this class's command-object translation layer entirely.
 *
 * <p><strong>Construction note</strong>: this class no longer builds its own {@code
 * StorageEngine} from a {@link StorageLocation} — that decision (which concrete engine to
 * construct, and what {@link Availability} to report if that fails) belongs to whoever is
 * wiring instances up ({@code StorageInstanceProvider}, T4.5), the same division of labor the
 * pre-T4.3 shape already had between this class and its old {@code setIndex(...)}. {@link
 * #setEngine} is that seam.
 */
public final class StorageInstance {

  private static final Logger log = LoggerFactory.getLogger(StorageInstance.class);

  public static StorageInstance fromDto(final StorageInstanceDto dto) {
    Assert.notNull(dto, "StorageInstanceDto must not be null!");
    return new StorageInstance(new StorageId(dto.getId())).applyDto(dto);
  }

  private final StorageId id;
  private String name;
  private Availability availability;
  private StorageLocation location;
  private IndexingStrategyType indexingStrategy;

  private StorageEngine engine;
  private StorageEntryFactory entryFactory;
  private TreeBookmarkIndex treeIndex;

  // structural type-info accumulation (moved from the old StorageIndex): fed opportunistically
  // by every ObjectEntry content load that happens through read(), not an eager whole-storage
  // scan - see the type-info listener/amendType notes below.
  private final ConcurrentHashMap<String, StructuredType> typesByName = new ConcurrentHashMap<>();
  private final Lock typeLock = new ReentrantLock();
  private final List<Consumer<StorageId>> typeInfoListeners = new CopyOnWriteArrayList<>();

  private StorageInstance(final StorageId id) {
    this.id = Objects.requireNonNull(id, "Storage Instance ID cannot be null!");
    availability = Availability.UNAVAILABLE;
  }

  public StorageId id() {
    return id;
  }

  public String name() {
    return name;
  }

  public void setName(final String name) {
    this.name = (name == null) ? "" : name;
  }

  public Availability availability() {
    return availability;
  }

  public void setAvailability(final Availability availability) {
    this.availability = (availability == null) ? Availability.UNAVAILABLE : availability;
  }

  public StorageLocation location() {
    return location;
  }

  public void setLocation(final StorageLocation location) {
    Assert.notNull(location, "StorageLocation must not be null!");
    this.location = location;
  }

  public StorageInstanceType type() {
    return location instanceof FsStorageLocation ? StorageInstanceType.FS : StorageInstanceType.DB;
  }

  /** Whether {@code location} is locally known to be invalid, before any connection is even
   *  attempted — see {@link AvailabilityClassifier}'s own javadoc (T3.6). */
  public boolean misconfigured() {
    return AvailabilityClassifier.isMisconfigured(location);
  }

  /** The configured indexing strategy — a stored configuration value only; the old FULL/SURFACE
   *  eager whole-storage indexing it used to select has been removed outright (decisions log §3
   *  decision 1). {@code TreeBookmarkIndex#rebuildSurface} is the explicit action that replaces
   *  it, available via {@link #treeIndex()}. */
  public IndexingStrategyType indexingStrategy() {
    return indexingStrategy;
  }

  public void setIndexingStrategy(final IndexingStrategyType type) {
    this.indexingStrategy = type;
  }

  /** Wires this instance's {@link StorageEngine}, by whoever constructs or reconfigures it
   *  ({@code StorageInstanceProvider}, T4.5/reimport). Also (re)builds the {@link #treeIndex()}.
   *  Despite the name suggesting a one-time wiring, {@code reimport} calls this again on an
   *  already-connected instance (e.g. after its connection settings changed) - whatever the
   *  previous engine was holding (an RDB engine's connection pool; nothing, for FS) is closed
   *  first, so reconfiguring a storage can never leak the engine it's replacing. */
  public void setEngine(final StorageEngine engine) {
    Objects.requireNonNull(engine, "engine cannot be null!");
    if (this.engine != null) {
      this.engine.close();
    }
    this.engine = engine;
    this.entryFactory = new StorageEntryFactory(id);
    this.treeIndex = new TreeBookmarkIndex(id, engine, entryFactory);
  }

  /** The one long-lived index backing the StorageTree's bookmark/tracked-entry set. */
  public TreeBookmarkIndex treeIndex() {
    return treeIndex;
  }

  /** Releases everything this instance holds open: the {@link #treeIndex()} and the engine
   *  itself (a connection pool, for an RDB-backed instance). Called when the storage is discarded
   *  outright, not just reconfigured - {@link #setEngine} already handles the reconfigure case on
   *  its own. Safe to call on an instance that never connected (nothing was ever set). */
  public void close() {
    if (treeIndex != null) {
      treeIndex.close();
    }
    if (engine != null) {
      engine.close();
    }
  }

  /** Resolves a bare address against this instance, without tracking it in {@link #treeIndex()}
   *  the way {@code TreeBookmarkIndex#track} does — for callers (ArcScript's {@code
   *  QuerySourceResolver}, T5.1) that need a one-off lookup and must not silently turn every
   *  address they ever look at into a permanent StorageTree bookmark. Same resolution logic as
   *  every Milestone 3 index's own {@code resolve}, just exposed here for a caller that only has
   *  this class to go through. */
  public EntryResolution resolve(final EntryUri uri) {
    if (!engine.exists(uri)) {
      return new EntryResolution.Unknown(uri, "no entry at this address");
    }
    return entryFactory.create(uri)
        .<EntryResolution>map(EntryResolution.Known::new)
        .orElseGet(() -> new EntryResolution.Unknown(uri, "exists, but could not be classified"));
  }

  /** A fresh, lazy, discovery-only listing of every entry matching {@code target} — the direct
   *  replacement for the old eager {@code StorageIndex#find(IndexingTarget)}: entries are
   *  constructed (via the factory, kind-hinted straight from the walk) as the underlying {@link
   *  StorageEngine#list} stream is consumed, never cached here. Callers must close the returned
   *  stream, exactly as {@link StorageEngine#list} itself requires. */
  public Stream<StorageEntry> query(final IndexingTarget target) {
    return engine.list(target)
        .flatMap(listed -> entryFactory.create(listed.uri(), listed.kind()).stream());
  }

  /** {@code owner}'s scoped children (stored list/map/ref/sequence addresses living under its
   *  own path) — the structural-nesting counterpart to {@link #newGraphIndex()}'s adjacency
   *  scan, needed independently by every UI that renders an {@code ObjectEntry}'s own nested
   *  children as such (the Swing tree, the REST DTO tree), not as graph edges. Replaces the
   *  removed {@code ObjectEntry#scopedEntries()} (T4.1, the eager bulk-indexing fold is gone)
   *  with the same narrowed {@code engine.list}-backed technique {@code
   *  StreamingAdjacencyIndex#scopedChildReferences} already uses internally for adjacency
   *  discovery — pulled out here (T7.1) once a second, independent caller needed the exact same
   *  algorithm. */
  public List<ScopedEntry> scopedChildrenOf(final ObjectEntry owner) {
    if (owner instanceof GodObjectEntry) {
      return List.of();
    }
    final List<String> segments = owner.uri().segments();
    if (segments.isEmpty()) {
      return List.of();
    }
    final String ownerPath = owner.uri().toString();
    final IndexingTarget target = new IndexingTarget(
        Set.of(owner.uri().scheme()),
        Set.of(segments.get(0)));
    try (Stream<StorageEntry> candidates = query(target)) {
      return candidates
          .filter(it -> !it.uri().equals(owner.uri()))
          .filter(it -> SCOPE_MARKERS.stream().anyMatch(marker -> it.uri().toString()
              .startsWith(ownerPath + marker)))
          .filter(ScopedEntry.class::isInstance)
          .map(ScopedEntry.class::cast)
          .toList();
    }
  }

  private static final List<String> SCOPE_MARKERS = List.of(
      StorageEntryFactory.STORED_LIST_MARKER,
      StorageEntryFactory.STORED_MAP_MARKER,
      StorageEntryFactory.STORED_REF_MARKER,
      StorageEntryFactory.STORED_SEQ_MARKER);

  /** Reads an entry's content straight through — no caching beyond whatever {@link
   *  #treeIndex()} itself does (none, today). An {@code ObjectEntry} load that succeeds also
   *  feeds the structural type-info registry (see {@link #getOrDescribeTypeOf}) — type info
   *  accumulates as an ambient side-effect of ordinary loads through this one chokepoint,
   *  rather than from a dedicated whole-storage scan. Loads made through a fresh, operation-
   *  scoped index ({@link #newGraphIndex()}, {@link #newStatsIndex()}, or the examiner's own
   *  loader) deliberately bypass this instance entirely (per this class's own construction-seam
   *  note) and so do not feed it — a known, accepted narrowing of the old behaviour. */
  public ContentLoadResult read(final StorageEntry entry) {
    final ContentLoadResult result = treeIndex.load(entry);
    if (result instanceof ContentLoadResult.Ok ok && entry instanceof ObjectEntry objectEntry) {
      noteObjectContent(objectEntry, ok.content());
    }
    return result;
  }

  /** The entry's path on disk, if this backend has one — empty for a SQL-backed instance. See
   *  the implementation plan's note on {@code StorageEntry.path()}'s removal (T4.1). */
  public Optional<Path> pathOf(final StorageEntry entry) {
    return engine.pathOf(entry.uri());
  }

  /** Every data version of {@code entry}, oldest first — a thin passthrough to {@link
   *  StorageEngine#history}, grown onto the engine in T7.1 once the Swing inspector's
   *  multi-version tab browsing needed it. {@code entry.uri().isSingleVersion()} must be
   *  {@code false}. */
  public EngineResult<List<VersionedContent>> history(final StorageEntry entry) {
    return engine.history(entry.uri());
  }

  /** Adds a new version, redirecting the head to it. */
  public StorageWriteResult newVersion(final StorageEntry entry, final StorageEntryPutCommand command) {
    return switch (engine.newVersion(entry.uri(), command.payload())) {
      case com.aestallon.storageexplorer.core.service.engine.EngineResult.Ok<StorageEngine.Committed> ok ->
          new StorageWriteResult.Ok(ok.value().serial());
      case com.aestallon.storageexplorer.core.service.engine.EngineResult.Err<StorageEngine.Committed> err ->
          new StorageWriteResult.Err(err.error().toString());
    };
  }

  /** Amends an existing version's payload, in place. */
  public StorageWriteResult amend(final StorageEntry entry, final StorageEntryAmendCommand command) {
    return switch (engine.amend(entry.uri(), command.serial(), command.payload(), command.touchHead())) {
      case com.aestallon.storageexplorer.core.service.engine.EngineResult.Ok<Void> ok ->
          new StorageWriteResult.Ok(java.util.OptionalLong.empty());
      case com.aestallon.storageexplorer.core.service.engine.EngineResult.Err<Void> err ->
          new StorageWriteResult.Err(err.error().toString());
    };
  }

  /** A fresh, operation-scoped adjacency index — one per graph render, discarded when it ends. */
  public AdjacencyAware newGraphIndex() {
    return new StreamingAdjacencyIndex(id, engine, entryFactory);
  }

  /** A fresh, operation-scoped metadata index — one per stats run, discarded when it ends. */
  public MetadataAware newStatsIndex() {
    return new HeaderMetadataIndex(id, engine, entryFactory);
  }

  /** A fresh, operation-scoped combined content/adjacency index — one per ArcScript query
   *  execution (T5.2), discarded when it ends; what {@code StorageInstanceExaminer
   *  .discoverProperty}'s recursion needs at every step. */
  public QueryableIndex newQueryIndex() {
    return new CompositeQueryableIndex(id, engine, entryFactory);
  }

  /** Every currently-tracked (bookmarked) entry. */
  public Collection<StorageEntry> entries() {
    return treeIndex == null ? java.util.List.of() : treeIndex.entries();
  }

  /** Registers a listener notified (with this instance's {@link #id()}) whenever a load through
   *  {@link #read(StorageEntry)} amends the structural type-info registry — the hook
   *  {@code StorageInstanceProvider} uses to re-publish {@code TypeInfoUpdated} for the
   *  debounced-save listener, without {@code core} itself depending on the Spring event
   *  machinery the old {@code StorageIndex} used directly. */
  public void addTypeInfoListener(final Consumer<StorageId> listener) {
    typeInfoListeners.add(Objects.requireNonNull(listener, "listener cannot be null!"));
  }

  /** The best structural description known so far of {@code entry}'s type — {@code Unknown}
   *  until at least one load of that type has gone through {@link #read(StorageEntry)}. */
  public StructuredType getOrDescribeTypeOf(final ObjectEntry entry) {
    return getOrDescribeTypeOf(entry.typeName());
  }

  public StructuredType getOrDescribeTypeOf(final String typeName) {
    return typesByName.computeIfAbsent(typeName, StructuredType.Unknown::new);
  }

  /** Seeds the registry from persisted type info (loaded at startup) — merged in, never
   *  overwriting what's already been discovered this session. */
  public void addTypeInfo(final Collection<EntityType> types) {
    typeLock.lock();
    try {
      for (final EntityType type : types) {
        final String typeName = type.name();
        final StructuredType existing = typesByName.computeIfAbsent(
            typeName,
            StructuredType.Unknown::new);
        typesByName.put(typeName, existing.amend(type));
      }
    } finally {
      typeLock.unlock();
    }
  }

  /** A snapshot of every structurally-described (i.e. not still {@code Unknown}) type
   *  discovered so far — what gets persisted by the debounced type-info save. */
  public Set<EntityType> getStructuredTypeInfo() {
    return typesByName.values().stream()
        .filter(EntityType.class::isInstance)
        .map(EntityType.class::cast)
        .collect(Collectors.toSet());
  }

  private void noteObjectContent(final ObjectEntry entry, final ContentHandle content) {
    if (content.materialize() instanceof LoadedContent.JsonObject jsonObject) {
      amendType(entry.typeName(), jsonObject.data());
    }
  }

  private void amendType(final String typeName, final Map<String, Object> oam) {
    typeLock.lock();
    try {
      final StructuredType type = typesByName.computeIfAbsent(typeName, StructuredType.Unknown::new);
      typesByName.put(typeName, type.amend(ObjectMaps.entityTypeOf(typeName, oam)));
    } finally {
      typeLock.unlock();
    }
    typeInfoListeners.forEach(listener -> listener.accept(id));
  }

  public StorageInstance applyDto(final StorageInstanceDto dto) {
    Assert.notNull(dto, "StorageInstanceDto must not be null!");

    setName(dto.getName());
    setAvailability(dto.getAvailability());
    switch (dto.getType()) {
      case FS -> setLocation(dto.getFs());
      case DB -> setLocation(dto.getDb());
      default -> throw new IllegalArgumentException("Unsupported storage type: " + dto.getType());
    }
    setIndexingStrategy(dto.getIndexingStrategy());

    return this;
  }

  public StorageInstanceDto toDto() {
    return new StorageInstanceDto()
        .id(id.uuid())
        .name(name)
        .availability(availability)
        .indexingStrategy(indexingStrategy)
        .type(location instanceof FsStorageLocation
            ? StorageInstanceType.FS
            : StorageInstanceType.DB)
        .fs(location instanceof FsStorageLocation fs ? fs : null)
        .db(location instanceof SqlStorageLocation sql ? sql : null);
  }

  @Override
  public boolean equals(final Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final StorageInstance that = (StorageInstance) o;
    return Objects.equals(id, that.id);
  }

  @Override
  public int hashCode() {
    return id.hashCode();
  }

  @Override
  public String toString() {
    return toDto().toString();
  }

}

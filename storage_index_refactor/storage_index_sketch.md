# `StorageIndex` interface sketch

Status: **sketch, not a plan**. This is a first cut at what the new `StorageIndex` contract could
look like given everything settled in `../STORAGE_INDEX_REFACTOR.md`. Package names, exact method
names, and generic signatures are all provisional — none of this has been checked against the
actual `core` module's existing types (`ObjectApi`, `StorageId`, `UriProperty`, etc.) for
compilability. Treat it as a shape to argue with, not code to paste in.

The companion piece — a review/plan for where `StorageEntry` and the rest of `core` actually need
to change to support this — is a separate file; this one is scoped to the `StorageIndex` side only.

## Design goals this sketch tries to satisfy

Pulled straight from the decisions log, so the sketch can be checked against them:

- `StorageEntry` is a pure immutable value (`StorageId` + `EntryUri` only) — it carries **no**
  behavior, so every operation here takes the entry *and* the index, never just the entry.
  **Updated since this sketch was first written**: `java.net.URI` is gone from this whole
  abstraction, replaced everywhere by `EntryUri` (a richer addressing type ported from the Rust
  `sb4a-fs` library — see `implementation_plan.md` Milestone 1). Every `URI` below should be read
  as `EntryUri`.
- "Is this URI known" and "did loading succeed" are both **values, not flags** — no `valid()`
  anywhere; sealed `Result`-shaped returns everywhere a failure is a real, expected outcome
  (matching the repo's existing `ObjectEntryLoadResult`/`EntryAcquisitionResult` convention).
- Every `StorageIndex` implementation must be **safe for concurrent use within one operation**
  (driven by `BatchLoaderExecutor`'s existing concurrent `tryLoad` pattern) — this is a contract
  requirement on every implementation, not something the interface can enforce structurally, so
  it's called out in the type's javadoc, loudly.
- One `StorageInstance` keeps exactly **one** small, long-lived, in-memory index backing the
  StorageTree. Every other use (graph render, ArcScript execution, stats run) gets its **own**
  disposable index, hence `StorageIndex extends AutoCloseable` — closing it is how an
  operation-scoped index gets torn down, no event-fan-out needed for those.
- The three use cases want genuinely different shapes of auxiliary data (full content / outgoing
  adjacency / tabular projection with provenance) — rather than one fat interface with
  methods some implementations can't sensibly support, the sketch splits those into **capability
  interfaces** layered on a minimal base. An implementation only implements the capabilities it
  actually supports; callers program against the capability they need (`AdjacencyAware`,
  `TabularProjectable`, etc.), not against `StorageIndex` itself, for anything beyond identity and
  raw loading.
- The persistence-engine choice is explicitly **not** visible anywhere in this sketch — every
  disk-backed implementation hides its substrate behind its own internal, package-private
  abstraction. (Resolved since this sketch was first written: MapDB and Xodus were both evaluated
  and rejected as too stale/unmaintained a dependency. The graph adjacency store — built once per
  operation, read-only after that — is now planned as a hand-rolled flat-file format rather than a
  library at all; ArcScript's spill structure, which genuinely needs mutation, is evaluating H2's
  MVStore — its standalone persistent map/B-tree layer, not the H2 SQL engine. Neither changes
  anything in this sketch; the abstraction boundary was the point.)
- **New since this sketch was first written**: the actual low-level storage I/O (reading/writing
  bytes off disk or a DB row) does **not** happen inside these `StorageIndex` implementations
  directly. A separate, lower interface, `StorageEngine` — Rust-`Storage`-shaped, core-internal
  only, implemented by `FsStorageEngine` and (later) `RdbStorageEngine` — sits underneath all of
  them. `StorageInstance` holds one `StorageEngine` and hands it to whichever `StorageIndex`
  implementation it constructs; none of the capability interfaces below change shape because of
  this, they just end up calling `StorageEngine.read(...)`/etc. internally instead of whatever
  placeholder loading mechanism was implied before. See `implementation_plan.md` Milestone 1 for
  the full design; not re-sketched here since it doesn't change this file's actual interfaces.

## The base contract

```java
package com.aestallon.storageexplorer.core.service.index;

/**
 * A transient, operation-scoped view over a subset of a StorageInstance's persisted entries.
 *
 * <p>Implementations MUST be safe for concurrent use by multiple threads within the lifetime of
 * a single operation (see BatchLoaderExecutor) — callers never serialize access themselves.
 *
 * <p>An instance is always owned by exactly one operation (an ArcScript execution, a graph
 * render, a stats run, or — the one long-lived exception — the StorageInstance's own
 * tree-bookmark index) and MUST be closed when that operation ends. Closing releases whatever
 * the implementation is holding (an open MapDB/Xodus environment, a thread pool, nothing at all
 * for a pure pass-through). Closing twice is a no-op.
 */
public interface StorageIndex extends AutoCloseable {

  StorageId storageId();

  /**
   * Pure presence check — does this index consider {@code uri} to denote a real, resolvable
   * entry? This never triggers content loading; it answers "do you know about this" only.
   */
  EntryResolution resolve(EntryUri uri);

  @Override
  void close(); // no "throws Exception" — implementations don't get to leak checked exceptions here
}
```

```java
/** Replaces the old mutable {@code StorageEntry.valid()} flag — presence is a value now. */
public sealed interface EntryResolution {

  record Known(StorageEntry entry) implements EntryResolution {}

  /** {@code reason} is diagnostic only (e.g. "no such file", "no row for this uri") — not a type
   *  tag; callers branch on the sealed type, not on the string. */
  record Unknown(EntryUri uri, String reason) implements EntryResolution {}
}
```

## Loading content — explicit index, `Result`-shaped outcome

This directly replaces `ObjectEntry.tryLoad()`. The entry becomes the *key*, not the actor.

```java
public interface ContentLoadable extends StorageIndex {

  /**
   * Loads {@code entry}'s current content. Thread-safe per the type's contract; concurrent
   * callers loading the same entry may or may not be coalesced — that's an implementation detail
   * of the particular index (e.g. an in-memory index can just re-read, a disk-backed one might
   * memoize).
   */
  ContentLoadResult load(StorageEntry entry);
}

public sealed interface ContentLoadResult {

  record Ok(StorageEntry entry, ContentHandle content) implements ContentLoadResult {}

  record Err(StorageEntry entry, String msg) implements ContentLoadResult {}
}

/**
 * Renamed from the earlier placeholder {@code JsonContent} — not just a rename, the shape
 * changed. A handle, not pre-materialized data: deliberately NOT the platform's
 * ObjectNode/Map<String,Object> tree. One type serves every entry kind, including the newer
 * ObjectStreamEntry/TableDataEntry (no separate StreamLoadable/TableLoadable capability needed —
 * that was an open question in an earlier draft, now resolved this way).
 */
public interface ContentHandle {

  /** Raw bytes — for leaf-URI scanning (the graph index) or ArcScript projection evaluation,
   *  neither of which needs full materialization. */
  InputStream openStream();

  /** Full load — for inspector views and anything else that wants the whole thing. */
  LoadedContent materialize();
}

/** Kind-aware on purpose: a plain StorageEntry's full load is a LinkedHashMap (not a generic
 *  tree type — same "extract the deliberate subset/shape we need" reasoning as EntryMetadata
 *  below), an object stream's is a sequence of documents, a table's is tabular. */
public sealed interface LoadedContent {

  record JsonObject(LinkedHashMap<String, Object> data) implements LoadedContent {}

  record ObjectStream(List<LinkedHashMap<String, Object>> entries) implements LoadedContent {}

  record TableData(/* tabular shape — TBD */) implements LoadedContent {}
}
```

The **inspector-view** `StorageIndex` is the trivial case this whole split exists to make trivial:
it implements `ContentLoadable` and nothing else — a thin pass-through to the owning
`StorageInstance`'s `StorageEngine`, no cache, no persistence, because a handful of open
inspectors is cheap and each one just calls `contentHandle.materialize()` once and holds the
result in the GUI widget.

## Outgoing adjacency — the graph-rendering (and ArcScript-traversal) capability

```java
public interface AdjacencyAware extends StorageIndex {

  /**
   * Outgoing references from {@code entry} — (edge label / property path, target URI) pairs.
   * Implementations are expected to have derived this via a single-pass streaming parse of the
   * entry's raw JSON (see notes below), NOT by materializing a full generic tree first.
   */
  AdjacencyResult outgoingReferences(StorageEntry entry);
}

public sealed interface AdjacencyResult {

  record Ok(StorageEntry entry, Set<UriProperty> references) implements AdjacencyResult {}

  record Err(StorageEntry entry, String msg) implements AdjacencyResult {}
}
```

Notes against the main doc:
- The graph-rendering index implements `StorageIndex` + `AdjacencyAware` (it does **not** need
  `ContentLoadable` at all — it only ever needs edges, never the full document). Internally it
  reads raw JSON straight off disk/DB and drives it through Jackson's streaming `JsonParser`
  (`JsonToken`-by-token), recording a `UriProperty` whenever a string leaf matches the
  smartbit4all URI shape — the platform's `ObjectNode`/`Map<String,Object>` tree is never built
  for this path at all.
- Forward-only. There is deliberately no `incomingReferences(...)` capability interface at all
  right now — inbound-reference discovery stays out of scope for this rework (it's the thing
  that's slow today specifically *because* it needs a full index; making it fast is a separate,
  later, differently-shaped feature, likely tied to a full-storage export operation rather than
  this interactive index).
- Persistence (when the graph index needs to spill/persist adjacency for a large render) sits
  entirely *behind* this interface — a package-private `AdjacencyStore` abstraction inside the
  graph-index implementation, backed by a hand-rolled flat-file format (sorted keys + offsets,
  built once per operation and never mutated afterward — no third-party KV engine needed for a
  shape this narrow), is never exposed here.

## Header metadata — the lightweight third shape

```java
public interface MetadataAware extends StorageIndex {

  MetadataResult metadataOf(StorageEntry entry);
}

public sealed interface MetadataResult {

  record Ok(StorageEntry entry, EntryMetadata metadata) implements MetadataResult {}

  record Err(StorageEntry entry, String msg) implements MetadataResult {}
}

/** Everything glimpsable from an entry's header without reading the body. Deliberately scoped,
 *  not a full mirror of either backend's internal model — same pattern as the existing
 *  core.model.loading.ObjectEntryMeta (a curated subset, not everything the richer source type
 *  exposes). May end up literally being the same record StorageEngine.head(...) returns. */
public record EntryMetadata(
    int versionCount,
    Instant lastVersionCreatedAt,
    long currentVersionNumber) {}
```

Per the main doc, this probably doesn't earn its own persistent store — it likely rides along as
extra fixed columns on whichever store (adjacency or projection) already needs to visit the entry,
rather than existing as a fourth standalone index. Shown here as its own capability interface
anyway so a caller that *only* wants metadata (e.g. a stats run) isn't forced to depend on
`AdjacencyAware`/`Tabular` to get it.

## The ArcScript projection — the big unresolved shape

This is the one piece of this sketch I'm least confident in, because "a malleable tabular
projection with droppable rows/columns and provenance-tracked cells, spillable to disk" is a
genuinely different kind of thing from "look up one entry's data" — it's stateful, phase-aware
query-execution machinery, not a per-entry lookup. Sketching it as a second-level type the index
hands out, rather than as methods directly on the index:

```java
public interface Tabular extends StorageIndex {

  /** One projection per query execution (or per sub-phase, if CTEs end up wanting their own). */
  Projection newProjection();
}

/**
 * Rows = StorageEntry candidates. Columns = property paths ("arcs") referenced by where / order /
 * yield at the current phase. Every cell remembers the medial StorageEntry URI it was reached
 * through, because ArcScript needs to continue a property path from THAT entry, not just read a
 * terminal scalar off the seed row's entry.
 */
public interface Projection extends AutoCloseable {

  /** Seeds a new row for {@code entry}; returns a row handle. */
  RowId addRow(StorageEntry entry);

  void dropRow(RowId row);

  /** Get-or-create a column for a property path; columns are shared across all rows. */
  ColumnId column(PropertyPath path);

  void dropColumn(ColumnId column);

  /** The value at (row, column) — may trigger the index to resolve/load the next hop the first
   *  time a given (row, column) pair is touched. */
  CellValue get(RowId row, ColumnId column);

  /** The medial entry this cell's value was reached through — what the recursive arc-walk
   *  continues from, replacing today's StorageInstanceExaminer-held discoverer chain. */
  StorageEntry provenanceOf(RowId row, ColumnId column);

  /** Hint, not a command — implementation decides whether/when to actually spill. Called by the
   *  query engine between phases once it has a size estimate. */
  void considerSpill();

  @Override
  void close();
}
```

Known-shaky parts of this shape, flagged rather than resolved:
- `RowId`/`ColumnId` as opaque handles assumes a fixed row/column identity scheme that survives a
  spill-to-disk round-trip — this needs to be true for `dropRow`/`dropColumn` to keep working
  after data has moved to disk, and nothing here has actually verified that's an easy invariant to
  hold once MapDB/Xodus enters the picture underneath.
- `CellValue` is undefined on purpose — it needs to cover scalars (for `where`/`order`/`yield`)
  *and* "this cell is itself a URI, keep walking" in a way `StorageInstanceExaminer`'s recursive
  `discoverProperty` can consume without each recursive call re-deriving provenance by hand.
  Whether that's a sealed type (`Scalar`/`Reference`) or `CellValue` + a separate
  `provenanceOf` call (as sketched above) is genuinely open.
- No attempt yet to map `StorageInstanceExaminer.discoverProperty`'s actual recursion (four
  overloads, per §5 of the main doc) onto `Projection`'s method shape — that mapping is the real
  test of whether this interface is right, and hasn't been done.
- CTEs and built-in computed columns (creation date etc.) are name-checked in the method set
  (`column(PropertyPath)` could presumably also accept some "built-in" path variant, and a CTE is
  presumably "another `Projection`, joined somehow") but neither has an actual shape here — pure
  placeholder.

## `StorageInstance`'s side of this

```java
public final class StorageInstance {

  /** Rust-Storage-shaped, core-internal only — see implementation_plan.md Milestone 1. Never
   *  referenced outside core; StorageInstance's own public methods translate to/from it. */
  private StorageEngine engine;

  /** The one long-lived index. Present once availability() == AVAILABLE; backs the StorageTree
   *  exclusively. Never bulk-refreshed — grows only via explicit track/acquire calls. */
  private StorageIndex treeIndex;

  public StorageIndex treeIndex() { return treeIndex; }

  // --- public, command-object-shaped API that client/swing/app-cli/spring actually call ---

  public ContentLoadResult read(StorageEntry entry) { /* ... */ }

  public Optional<Path> pathOf(StorageEntry entry) { /* ... */ } // empty for RDB-backed instances;
                                                                   // backs the inspector's "reveal
                                                                   // in system file explorer" button

  // amend/newVersion with command objects omitted here — shapes TBD, see implementation_plan.md

  // --- operation-scoped factories; caller owns the returned index and MUST close it ---
  // each passes `engine` (not `this`) to the implementation it constructs — StorageIndex
  // implementations are core-internal code too, they skip the command-object translation layer.

  public AdjacencyAware newGraphIndex() { /* ... */ }

  public Tabular newArcScriptIndex(ArcScriptIndexSettings settings) { /* ... */ }

  public MetadataAware newStatsIndex() { /* ... */ }
}
```

`ArcScriptIndexSettings` is a stand-in for whatever lets the caller express "in-memory is fine for
this one" vs. "this execution needs the disk-backed variant" — not designed here; per the main
doc, the disk-backed ArcScript index is in scope for phase 1 but its trigger condition (explicit
user setting? engine-estimated row count? both?) hasn't been discussed yet.

## Things deliberately left out of this sketch

- `IndexingStrategy`/`IndexingTarget` — the tree-bookmark index still needs *something* shaped
  like today's `refresh(IndexingStrategy, IndexingTarget)` for its SURFACE-mode rebuild (§9/§10 of
  the main doc), but that's a detail of the one long-lived implementation, not the shared contract
  every operation-scoped index needs — left out here to keep the base contract minimal.
  (`StorageEntryFactory` is no longer in this "left out" list — it now has a concrete task in
  `implementation_plan.md` T4.1: drop its `StorageIndex<?>` dependency, recognize `.os`/`.td`.)
- The `discard()`/`reimport` machinery on `StorageInstanceProvider` — orthogonal to the
  `StorageIndex` shape itself; see §10 of the main doc.
- Any actual MVStore code or hand-rolled flat-file format code, or even an internal `KvStore`-
  shaped abstraction for either — intentionally not sketched yet; MVStore specifically still needs
  a hands-on evaluation before anything is built against it.

# Deferred validation & testing

Items flagged along the way in `implementation_plan.md` that are **not covered by any single
task** — things this port assumes, documents, or works around, but hasn't actually proven against
a real, live, concurrent, cross-process smartbit4all platform node. Collected here so they aren't
lost in the middle of a task's prose. Nothing here blocks landing Milestone 1; it blocks trusting
the write path against a real shared storage "in anger."

## 1. OS lock semantics may not actually interoperate with the platform's

**Where it came from**: T1.1 (general flag), T1.5 (`ObjectLock`'s own javadoc and plan notes).

The platform's own lock is a *logical* lock (an owner record in the `.l` file, with an OS lock
held only for the brief moment of claiming it). This port is deliberately stricter: it holds the
OS lock (`java.nio.channels.FileChannel#lock`) for the **entire** write.

- **Windows**: both the platform and this port should go through the same `LockFileEx`-family
  primitive, so they should exclude each other correctly. Not verified against a real platform
  node — only inferred from both sides targeting the same OS API.
- **Unix**: the platform takes POSIX `fcntl` locks; `FileChannel#lock` goes through `flock`.
  These two lock families are documented as **not mutually exclusive** on at least some Unix
  kernels — a `flock` held by this port would not necessarily be seen by a platform node's
  `fcntl` lock, or vice versa. On Unix, the `.l` owner record (not the OS lock) may be the *only*
  real protection against a live platform node, and even that depends on the platform node
  reliably writing/clearing that record, which this port doesn't control.
- This whole codebase has so far only run on Windows (this session's dev OS) — the two-thread
  lock-contention tests (T1.5) and the write-path tests (T1.7) all passed there, which is *one*
  data point in favor of the Windows case, but says nothing about Unix.

**What would retire this item**: a real cross-process test against a live platform node — ideally
on both Windows and Linux — mirroring the Rust project's own `interop/concurrency.py`: one side
(a running platform instance) holds the object lock across a save, the other side (this port)
attempts a concurrent write, and the test asserts the second side is correctly refused/blocked,
not racing the first.

## 2. `FsUtil.isTransient`'s Windows-sharing-violation heuristic is unvalidated

**Where it came from**: `FsUtil`'s own javadoc; carried forward in T1.4 and T1.5's plan notes.

Java has no exception type as specific as Rust's `io::ErrorKind::ResourceBusy` for "someone else
has this file open right now, try again shortly." `FsUtil.isTransient` guesses based on
`AccessDeniedException`/a generic `FileSystemException` (excluding `NoSuchFileException`), which
is a reasonable approximation but has never actually been exercised against a real Windows sharing
violation — only against the fixture corpus's static files and this port's own single-process
tests, none of which produce that specific I/O condition.

**What would retire this item**: a test (or real-world run) that opens a file this port is about
to read/write from a second process (or a second handle with an incompatible share mode) and
confirms `isTransient` correctly classifies the resulting exception, and that the bounded-retry
behavior built on top of it actually recovers once the other handle closes.

## 3. `amend`'s `touchHead` behavior under concurrent readers is assumed, not proven

**Where it came from**: surfaced while implementing T1.7; not explicitly flagged in the plan
before now.

`amend` with `touchHead = true` opens the `.o` file and calls `Files.setLastModifiedTime(...)` to
bump its mtime, the same way the Rust port does (`OpenOptions::write(true).open(...)` then
`set_modified`), so that platform caches keyed on the descriptor's mtime notice the amended
content underneath an unchanged descriptor body. This is exercised single-process
(`amendTouchesTheHeadUnlessToldNotTo`), but never against a concurrent reader that has the `.o`
file open (or cached) at the moment the mtime changes — i.e., whether a live platform node's cache
invalidation actually reacts the way this assumes.

**What would retire this item**: a test against a running platform node that amends a version
through this port and confirms the platform's own object cache picks up the new content without
a restart.

## 4. Cross-platform lock-file byte format leniency is only lightly exercised

**Where it came from**: `ObjectLock.parseOwner`'s own javadoc (T1.5).

The lock file's owner record is `[i32 BE length][UTF-8 runtime id][i32 BE length][UTF-8
transaction id]`. The platform itself writes the *string's UTF-16 length* as that length prefix,
which only equals the UTF-8 byte length for pure-ASCII ids — `parseOwner` is deliberately lenient
(clamps and reads lossily) to tolerate that mismatch, but this has only been tested with ASCII
runtime/transaction ids (UUIDs and the hardcoded test literals like `"node"`/`"other"`). A real
platform-written lock record with non-ASCII content in either id has never been exercised.

**What would retire this item**: a fixture lock file written by an actual platform node whose
runtime or transaction id contains non-ASCII characters, read back through `parseOwner`, and
checked for a sane (even if lossy) result rather than silent corruption or an exception.

## 5. No real multi-writer throughput/contention test yet

**Where it came from**: implicit in T1.5/T1.7's lock tests all being two-thread, short-lived, and
single-process.

Every lock-contention test so far (T1.5's two real-thread cases, T1.7's held-lock test) exercises
exactly two contenders for a few milliseconds. There's no test yet with realistic contention
levels (many threads/processes hammering the same object) to shake out starvation, the
exponential-backoff pause-growth logic (`ObjectLock.acquire`'s `growBy`) under sustained load, or
whether the `.iv` file's "no protection against concurrent writers, callers hold the object lock"
assumption (`IndexedFile`'s own javadoc) actually holds up when the lock discipline is stressed
rather than assumed.

**What would retire this item**: a stress test — many threads/processes, same object, enough
iterations to make starvation or a missed-lock race visible if either exists.

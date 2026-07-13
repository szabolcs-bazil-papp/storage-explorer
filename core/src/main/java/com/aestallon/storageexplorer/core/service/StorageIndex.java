/*
 * Copyright (C) 2025 Szabolcs Bazil Papp
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

package com.aestallon.storageexplorer.core.service;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toSet;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.smartbit4all.api.collection.CollectionApi;
import org.smartbit4all.core.object.ObjectApi;
import org.springframework.context.ApplicationEventPublisher;
import com.aestallon.storageexplorer.core.event.TypeInfoUpdated;
import com.aestallon.storageexplorer.core.model.entry.ObjectEntry;
import com.aestallon.storageexplorer.core.model.entry.ScopedEntry;
import com.aestallon.storageexplorer.core.model.entry.StorageEntry;
import com.aestallon.storageexplorer.core.model.entry.StorageEntryFactory;
import com.aestallon.storageexplorer.core.model.instance.dto.StorageId;
import com.aestallon.storageexplorer.core.model.loading.IndexingTarget;
import com.aestallon.storageexplorer.core.model.loading.ObjectEntryLoadRequest;
import com.aestallon.storageexplorer.core.model.type.EntityType;
import com.aestallon.storageexplorer.core.model.type.StructuredType;
import com.aestallon.storageexplorer.core.service.cache.StorageIndexCache;
import com.aestallon.storageexplorer.core.util.ObjectMaps;
import com.google.common.base.Strings;

public abstract sealed class StorageIndex<T extends StorageIndex<T>>
    permits FileSystemStorageIndex, RelationalDatabaseStorageIndex {

  private static final Logger log = LoggerFactory.getLogger(StorageIndex.class);

  protected final StorageId storageId;
  protected final ObjectApi objectApi;
  protected final CollectionApi collectionApi;

  protected StorageIndexCache cache;
  protected StorageEntryFactory storageEntryFactory;
  protected ApplicationEventPublisher eventPublisher;

  protected final Lock typeLock = new ReentrantLock(true);
  protected final ConcurrentHashMap<String, StructuredType> typesByName = new ConcurrentHashMap<>();

  protected StorageIndex(StorageId storageId,
                         ObjectApi objectApi,
                         CollectionApi collectionApi) {
    this.storageId = storageId;
    this.objectApi = objectApi;
    this.collectionApi = collectionApi;
  }

  public final StorageId id() {
    return storageId;
  }

  public final Set<URI> uris() {
    return cache.knownUris();
  }

  /**
   * Indexes the entire underlying smartbit4all storage with the provided {@link IndexingStrategy}.
   *
   * <p>
   * All discovered {@link StorageEntry} instances are submitted to the underlying cache of this
   * index.
   *
   * @param strategy the {@link IndexingStrategy} to use for refreshing the index, not null
   *
   * @return the number of {@link StorageEntry} instances discovered by the indexing operation
   */
  public int refresh(IndexingStrategy strategy) {
    clear();
    if (!strategy.fetchEntries()) {
      return 0;
    }
    try (final var uris = fetchEntries()) {
      final var res = strategy.processEntries(uris, storageEntryFactory::create);
      cache.putAll(res);
      return res.size();
    }
  }

  /**
   * Indexes a subset of the underlying smartbit4all storage with the provided
   * {@link IndexingStrategy}.
   *
   * <p>
   * All discovered {@link StorageEntry} instances are submitted to the underlying cache of this
   * index.
   *
   * @param strategy the {@link IndexingStrategy} to use for refreshing the index, not null
   * @param target the {@link IndexingTarget} defining the storage schemas and types to visit
   *     and index, not null
   *
   * @return the number of {@link StorageEntry} instances discovered by the indexing operation
   */
  public int refresh(final IndexingStrategy strategy, final IndexingTarget target) {
    if (!strategy.fetchEntries()) {
      return 0;
    }

    try (final var uris = fetchEntries(target)) {
      final var res = strategy.processEntries(uris, storageEntryFactory::create);
      res.forEach(cache::merge);
      return res.size();
    }
  }

  /**
   * Clears the underlying {@link StorageIndexCache} of this index.
   *
   * <p>
   * This method should be invoked with extreme caution, as the {@link StorageEntry} instances
   * indexed by this index will be left dangling after this method returns.
   *
   */
  public void clear() {
    cache.clear();
  }

  @Deprecated(forRemoval = true, since = "0.3.0")
  public void revalidate(final Collection<? extends StorageEntry> entries) {
    // TODO: This entire thing NEEDS TO GO! Only used by the Graph, which should be configured to 
    //  use this revalidation strategy or not, and if needed it must be handled appropriately!
    Set<StorageEntry> needsRevalidation = entries.stream()
        .filter(ObjectEntry.class::isInstance)
        .map(ObjectEntry.class::cast)
        .filter(it -> !it.valid())
        .collect(toSet());
    new IndexingStrategy.EntryProcessor.Builder(null, needsRevalidation)
        .build()
        .execute()
        .forEach(ObjectEntryLoadRequest::get);
  }

  /**
   * Sets the Spring event publisher to use for publishing events.
   *
   * @param eventPublisher the {@link ApplicationEventPublisher} to use, nullable
   */
  public void setEventPublisher(final ApplicationEventPublisher eventPublisher) {
    this.eventPublisher = eventPublisher;
  }

  /**
   * Publishes a Spring application event, if the publisher is set.
   *
   * @param e the event object to publish, not null
   * @param <EVENT> the type of the event object, it should be a final type (such as a
   *     {@code record})
   */
  protected <EVENT> void publishEvent(final EVENT e) {
    if (eventPublisher != null) {
      eventPublisher.publishEvent(e);
    }
  }

  protected abstract Stream<URI> fetchEntries();

  protected abstract Stream<URI> fetchEntries(IndexingTarget target);

  public final void notifyRefresh(StorageEntry storageEntry) {
    cache.put(storageEntry.uri(), storageEntry);
  }

  public abstract ObjectEntryLoadingService<T> loader();

  // TODO: Make abstract and implement!
  public StorageEntryModificationService<T> modifier() {
    return (entry, content, mode) ->
        new StorageEntryModificationService.StorageEntryModificationResult.Err(
            entry,
            "Feature not yet implemented");
  }

  public Stream<StorageEntry> entities() {
    return cache.stream();
  }

  public Optional<StorageEntry> get(final URI uri) {
    return cache.get(uri);
  }

  public Set<StorageEntry> get(final IndexingTarget target) {
    return cache.stream().filter(matching(target)).collect(toSet());
  }

  /**
   * The exact entry-level predicate an {@link IndexingTarget} denotes.
   *
   * <p>
   * The physical fetch layers over-approximate the target (the file-system walker matches type
   * directories by suffix, the relational query uses {@code CLASSNAME LIKE '%Type'}), so results
   * drawn directly from {@link #fetchEntries(IndexingTarget)} must be re-filtered with this
   * predicate to match what {@link #get(IndexingTarget)} would return.
   */
  private static Predicate<StorageEntry> matching(final IndexingTarget target) {
    final Predicate<StorageEntry> schema = target.schemas().isEmpty()
        ? e -> true
        : e -> target.schemas().contains(e.uri().getScheme());
    final Predicate<StorageEntry> type = target.types().isEmpty()
        ? e -> true
        : e -> e instanceof ObjectEntry o && target.types().contains(o.typeName());
    return schema.and(type);
  }

  /**
   * Lazily discovers the {@link StorageEntry}s matching the provided {@link IndexingTarget},
   * populating the stream as the underlying storage yields them.
   *
   * <p>
   * Every discovered entry is included in this index as a side effect, exactly as a
   * {@link #refresh(IndexingStrategy, IndexingTarget)} pass would include it - but callers observe
   * entries one by one, without waiting for the full discovery to complete. Entries already known
   * to the index are returned as their canonical, cached instances.
   *
   * <p>
   * The returned stream <strong>must be closed</strong> (it is {@code onClose}-chained to the
   * underlying storage resources: file-system walker threads or an open database cursor - for
   * relational storages the connection is held until the stream is closed, not merely until
   * discovery finishes). Closing the stream before exhaustion cancels the underlying discovery.
   * The stream is single-consumer and not thread-safe.
   *
   * @param target the {@link IndexingTarget} defining the storage schemas and types to discover,
   *     not null
   *
   * @return a lazily populated {@link Stream} of matching {@link StorageEntry} instances
   */
  public Stream<StorageEntry> find(final IndexingTarget target) {
    final Stream<URI> uris = fetchEntries(target);
    final Queue<StorageEntry> newEntries = new ConcurrentLinkedQueue<>();
    return uris
        .map(uri -> acquire(uri, newEntries))
        .flatMap(Optional::stream)
        .filter(matching(target))
        .onClose(() -> {
          try {
            uris.close();
          } finally {
            // batch-local scoped-entry association, identical to what a refresh pass performs -
            // deliberately deferred to completion: doing it per entry would scan the entire cache
            // for every new entry (see prepareNewEntry), turning a streaming walk quadratic:
            IndexingStrategy.associateScopedEntries(newEntries);
          }
        });
  }

  /**
   * Acquires the canonical entry for the given URI, creating and indexing it if absent - the
   * per-element "include in the index" side effect of {@link #find(IndexingTarget)}.
   */
  private Optional<StorageEntry> acquire(final URI uri,
                                         final Collection<StorageEntry> newEntries) {
    final var present = cache.get(uri);
    if (present.isPresent()) {
      return present;
    }

    return storageEntryFactory.create(uri)
        .map(created -> {
          final StorageEntry canonical = cache.compute(uri, (k, v) -> {
            if (v == null) {
              return created;
            }

            v.accept(created);
            return v;
          });
          if (canonical == created) {
            newEntries.add(canonical);
          }
          return canonical;
        });
  }

  public EntryAcquisitionResult getOrCreate(final URI uri) {
    return cache
        .get(uri)
        .map(EntryAcquisitionResult::ofPresent)
        .orElseGet(() -> {
          log.debug("Cache miss for {}", uri);
          return storageEntryFactory.create(uri)
              .map(EntryAcquisitionResult::ofNew)
              .orElseGet(EntryAcquisitionResult::ofFail);
        });
  }

  public void accept(final URI uri, StorageEntry entry) {
    cache.compute(uri, (k, v) -> {
      if (v == null) {
        prepareNewEntry(entry);
        return entry;
      }

      v.accept(entry);
      return v;
    });
  }

  private void prepareNewEntry(final StorageEntry entry) {
    if (entry instanceof ObjectEntry objectEntry && !(entry instanceof ScopedEntry)) {
      // here becomes obvious that a more sophisticated cache is in dire need -> this is not
      // only a repetition but woefully slow...
      final Map<String, List<ScopedEntry>> knownScopedEntries = cache
          .scopedEntries()
          .collect(groupingBy(e -> e.scope().getPath()));
      knownScopedEntries
          .getOrDefault(objectEntry.uri().getPath(), new ArrayList<>())
          .forEach(objectEntry::addScopedEntry);
    }

    if (entry instanceof ScopedEntry scopedEntry) {
      cache.objectEntries()
          .filter(it -> it.uri().getPath().equals(scopedEntry.scope().getPath()))
          .forEach(it -> it.addScopedEntry(scopedEntry));
    }
  }

  public StructuredType getOrDescribeTypeOf(final ObjectEntry objectEntry) {
    return getOrDescribeTypeOf(objectEntry.typeName());
  }

  public StructuredType getOrDescribeTypeOf(final String typeName) {
    return typesByName.computeIfAbsent(
        typeName,
        k -> new StructuredType.Unknown(typeName));
  }

  public StructuredType amendType(final String typeName, final Map<String, Object> oam) {
    typeLock.lock();
    try {

      final StructuredType type = typesByName.computeIfAbsent(
          typeName,
          k -> new StructuredType.Unknown(typeName));
      final var amendedType = type.amend(ObjectMaps.entityTypeOf(typeName, oam));
      typesByName.put(typeName, amendedType);
      return amendedType;
    } catch (final Exception e) {
      log.error(e.getMessage(), e);
      return new StructuredType.Unknown(typeName);
    } finally {
      typeLock.unlock();
      publishEvent(new TypeInfoUpdated(storageId));
    }
  }

  public void addTypeInfo(Collection<EntityType> types) {
    typeLock.lock();
    try {

      for (final EntityType type : types) {
        final var typeName = type.name();
        final StructuredType existingType = typesByName.computeIfAbsent(
            typeName,
            k -> new StructuredType.Unknown(typeName));
        final var amendedType = existingType.amend(type);
        typesByName.put(typeName, amendedType);
      }

    } catch (final Exception e) {
      log.error(e.getMessage(), e);
    } finally {
      typeLock.unlock();
    }
  }

  public Set<EntityType> getStructuredTypeInfo() {
    return typesByName.values().stream()
        .filter(EntityType.class::isInstance)
        .map(EntityType.class::cast)
        .collect(toSet());
  }

  public Stream<StorageEntry> searchForUri(final String queryString) {
    if (Strings.isNullOrEmpty(queryString)) {
      return Stream.empty();
    }
    final var p = constructPattern(queryString);
    return cache.stream().filter(it -> p.matcher(it.uri().toString()).find());
  }


  private static Pattern constructPattern(final String queryString) {
    final String q = queryString.replaceAll("[\\.\\+\\*\\-\\(\\)\\[\\]]", "");
    return Arrays.stream(splitAtForwardSlash(q))
        .map(StorageIndex::examineSubsection)
        .collect(Collectors.collectingAndThen(Collectors.joining(), Pattern::compile));
  }

  private static String[] splitAtForwardSlash(final String q) {
    final String[] arr = q.split("/");
    final String[] temp = new String[arr.length];
    int ptr = 0;
    for (final String s : arr) {
      if (Strings.isNullOrEmpty(s)) {
        continue;
      }
      temp[ptr] = ptr == 0 ? s : "\\/" + s;
      ptr++;
    }
    final String[] ret = new String[ptr];
    System.arraycopy(temp, 0, ret, 0, ret.length);
    return ret;
  }

  private static String examineSubsection(final String s) {
    final StringBuilder sb = new StringBuilder();
    for (char c : s.toCharArray()) {
      if (!sb.isEmpty() && Character.isUpperCase(c)) {
        sb.append(".*");
      }
      sb.append(c);
    }
    sb.append(".*");
    return sb.toString();
  }

  public enum AcquisitionKind { NEW, PRESENT, FAIL }


  public sealed interface EntryAcquisitionResult {

    private static EntryAcquisitionResult ofNew(final StorageEntry entry) {
      return new New(entry);
    }

    private static EntryAcquisitionResult ofPresent(final StorageEntry entry) {
      return new Present(entry);
    }

    private static EntryAcquisitionResult ofFail() {
      return new Fail();
    }

    record Present(StorageEntry entry) implements EntryAcquisitionResult {}


    record New(StorageEntry entry) implements EntryAcquisitionResult {}


    record Fail() implements EntryAcquisitionResult {}

  }

}

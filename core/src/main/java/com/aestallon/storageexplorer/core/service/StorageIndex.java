/*
 * Copyright (C) 2024 it4all Hungary Kft.
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

import java.io.Console;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.smartbit4all.api.collection.CollectionApi;
import org.smartbit4all.core.object.ObjectApi;
import org.smartbit4all.core.object.ObjectNode;
import com.aestallon.storageexplorer.core.model.entry.ObjectEntry;
import com.aestallon.storageexplorer.core.model.entry.ScopedEntry;
import com.aestallon.storageexplorer.core.model.entry.StorageEntry;
import com.aestallon.storageexplorer.core.model.entry.StorageEntryFactory;
import com.aestallon.storageexplorer.core.model.instance.dto.StorageId;
import com.aestallon.storageexplorer.core.model.loading.IndexingTarget;
import com.google.common.base.Strings;
import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

public abstract sealed class StorageIndex
    permits FileSystemStorageIndex, RelationalDatabaseStorageIndex {

  private static final Logger log = LoggerFactory.getLogger(StorageIndex.class);

  protected final StorageId storageId;
  protected final ObjectApi objectApi;
  protected final CollectionApi collectionApi;
  protected final Map<URI, StorageEntry> cache;
  protected StorageEntryFactory storageEntryFactory;

  protected StorageIndex(StorageId storageId,
                         ObjectApi objectApi,
                         CollectionApi collectionApi) {
    this.storageId = storageId;
    this.objectApi = objectApi;
    this.collectionApi = collectionApi;
    this.cache = new ConcurrentHashMap<>();
  }
  
  public final StorageId id() {
    return storageId;
  }

  public int refresh(IndexingStrategy strategy) {
    clear();
    if (!strategy.fetchEntries()) {
      return 0;
    }
    ensureStorageEntryFactory();
    final var res = strategy.processEntries(fetchEntries(), storageEntryFactory::create);
    cache.putAll(res);
    return res.size();
  }

  public int refresh(final IndexingStrategy strategy, final IndexingTarget target) {
    if (!strategy.fetchEntries()) {
      return 0;
    }

    ensureStorageEntryFactory();
    final var res = strategy.processEntries(fetchEntries(target), storageEntryFactory::create);
    // we need a bit more sophisticated work here:
    // If entry was absent from cache -> put it in.
    // If entry was present in the cache, and the indexing strategy was FULL -> refresh the entry 
    // with the UriProperties found in the newly indexed one.
    // We should emit an event for each (!) refreshed entry, so UIs may update (inspectors, graph)
//    res.forEach((uri, entry) -> cache.compute(uri, (k, v) -> {
//      if (v == null) {
//        return entry;
//      }
//
//      v.accept(entry);
//      return v;
//    }));
    res.forEach(cache::putIfAbsent);
    return res.size();
  }

  void clear() {
    cache.clear();
  }

  @Deprecated
  public void revalidate(final Collection<? extends StorageEntry> entries) {
    entries.stream()
        .filter(ObjectEntry.class::isInstance)
        .map(ObjectEntry.class::cast)
        .filter(it -> !it.valid())
        .forEach(ObjectEntry::refresh);
  }

  protected abstract Stream<URI> fetchEntries();

  protected abstract Stream<URI> fetchEntries(IndexingTarget target);

  protected abstract StorageEntryFactory storageEntryFactory();
  
  public abstract ObjectEntryLoadingService loader();

  protected final void ensureStorageEntryFactory() {
    if (storageEntryFactory == null) {
      this.storageEntryFactory = storageEntryFactory();
    }
  }

  public Stream<StorageEntry> entities() {
    return cache.values().stream();
  }

  public Optional<StorageEntry> get(final URI uri) {
    return Optional.ofNullable(cache.get(uri));
  }

  public Set<StorageEntry> get(final IndexingTarget target) {
    final Predicate<StorageEntry> schema = target.schemas().isEmpty()
        ? e -> true
        : e -> target.schemas().contains(e.uri().getScheme());
    final Predicate<StorageEntry> type = target.types().isEmpty()
        ? e -> true
        : e -> e instanceof ObjectEntry o && target.types().contains(o.typeName());
    final var p = schema.and(type);
    return cache.values().stream().filter(p).collect(toSet());
  }

  public EntryAcquisitionResult getOrCreate(final URI uri) {
    StorageEntry storageEntry = cache.get(uri);
    if (storageEntry == null) {
      log.debug("Cache miss for {}", uri);
      ensureStorageEntryFactory();
      return storageEntryFactory.create(uri)
          .map(EntryAcquisitionResult::ofNew)
          .orElseGet(EntryAcquisitionResult::ofFail);
    }

    return EntryAcquisitionResult.ofPresent(storageEntry);
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
      final Map<String, List<ScopedEntry>> knownScopedEntries = cache.values().stream()
          .filter(ScopedEntry.class::isInstance)
          .map(ScopedEntry.class::cast)
          .collect(groupingBy(e -> e.scope().getPath()));
      knownScopedEntries
          .getOrDefault(objectEntry.uri().getPath(), new ArrayList<>())
          .forEach(objectEntry::addScopedEntry);
    }

    if (entry instanceof ScopedEntry scopedEntry) {
      cache.values().stream()
          .filter(ObjectEntry.class::isInstance)
          .map(ObjectEntry.class::cast)
          .filter(it -> it.uri().getPath().equals(scopedEntry.scope().getPath()))
          .forEach(it -> it.addScopedEntry(scopedEntry));
    }
  }

  public Stream<StorageEntry> searchForUri(final String queryString) {
    if (Strings.isNullOrEmpty(queryString)) {
      return Stream.empty();
    }
    final var p = constructPattern(queryString);
    return cache.values().stream().filter(it -> p.matcher(it.uri().toString()).find());
  }

  private static Pattern constructPattern(final String queryString) {
    final String q = queryString.replaceAll("\\.\\+\\*-\\(\\)\\[]", "");
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

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

import com.aestallon.storageexplorer.core.model.entry.ScopedEntry;
import com.aestallon.storageexplorer.core.model.entry.StorageEntryFactory;
import com.aestallon.storageexplorer.core.model.instance.dto.StorageId;
import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.groupingBy;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
import com.aestallon.storageexplorer.core.model.loading.IndexingTarget;
import com.google.common.base.Strings;
import com.aestallon.storageexplorer.core.model.entry.ObjectEntry;
import com.aestallon.storageexplorer.core.model.entry.StorageEntry;
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
    this.cache = new HashMap<>();
  }

  public void refresh(IndexingStrategy strategy) {
    clear();
    if (!strategy.fetchEntries()) {
      return;
    }
    ensureStorageEntryFactory();
    cache.putAll(strategy.processEntries(fetchEntries(), storageEntryFactory::create));
  }
  
  public void refresh(final IndexingStrategy strategy, final IndexingTarget target) {
    if (!strategy.fetchEntries()) {
      return;
    }
    
    ensureStorageEntryFactory();
    cache.putAll(strategy.processEntries(fetchEntries(target), storageEntryFactory::create));
  }

  void clear() {
    cache.clear();
  }

  public void revalidate(final Collection<? extends StorageEntry> entries) {
    final List<ObjectEntry> needRevalidation = entries.stream()
        .filter(ObjectEntry.class::isInstance)
        .map(ObjectEntry.class::cast)
        .filter(it -> !it.valid())
        .distinct()
        .toList();
    final List<ObjectNode> nodes = needRevalidation.stream()
        .map(StorageEntry::uri)
        .map(objectApi::getLatestUri)
        .collect(collectingAndThen(toList(), objectApi::loadBatch));
    if (entries.size() != nodes.size()) {
      log.debug("Batch loading resulted in {} entries and {} nodes", entries.size(), nodes.size());
      if (log.isTraceEnabled()) {
        log.trace("Batch loading:\nEntries were: {}\nNodes became: {}", entries, nodes);
      }
      return;
    }

    IntStream.range(0, entries.size()).forEach(i -> needRevalidation.get(i).refresh(nodes.get(i)));
  }

  protected abstract Stream<URI> fetchEntries();

  protected abstract Stream<URI> fetchEntries(IndexingTarget target);

  protected abstract StorageEntryFactory storageEntryFactory();

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
      ensureStorageEntryFactory();
      return storageEntryFactory.create(uri)
          .map(EntryAcquisitionResult::ofNew)
          .orElseGet(EntryAcquisitionResult::ofFail);
    }

    return EntryAcquisitionResult.ofPresent(storageEntry);
  }

  public void accept(final URI uri, StorageEntry entry) {
    cache.put(uri, entry);
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
  
  public void printCount() {
    System.out.println(cache.size());
  }

  public Stream<StorageEntry> searchForUri(final String queryString) {
    if (Strings.isNullOrEmpty(queryString)) {
      return Stream.empty();
    }
    final var p = constructPattern(queryString);
    return cache.values().stream().filter(it -> p.matcher(it.uri().toString()).find());
  }

  private static Pattern constructPattern(final String queryString) {
    final String q = queryString.replaceAll("\\.\\+\\*\\-\\(\\)\\[\\]", "");
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


  public static final class EntryAcquisitionResult {

    private static EntryAcquisitionResult ofNew(final StorageEntry entry) {
      return new EntryAcquisitionResult(entry, AcquisitionKind.NEW);
    }

    private static EntryAcquisitionResult ofPresent(final StorageEntry entry) {
      return new EntryAcquisitionResult(entry, AcquisitionKind.PRESENT);
    }

    private static EntryAcquisitionResult ofFail() {
      return new EntryAcquisitionResult(null, AcquisitionKind.FAIL);
    }

    private final StorageEntry entry;
    private final AcquisitionKind kind;

    private EntryAcquisitionResult(final StorageEntry entry, final AcquisitionKind kind) {
      this.entry = entry;
      this.kind = kind;
    }

    public StorageEntry entry() {
      return entry;
    }

    public AcquisitionKind kind() {
      return kind;
    }

  }

}

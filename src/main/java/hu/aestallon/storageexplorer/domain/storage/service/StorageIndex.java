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

package hu.aestallon.storageexplorer.domain.storage.service;

import hu.aestallon.storageexplorer.domain.storage.model.instance.dto.StorageId;
import hu.aestallon.storageexplorer.util.Pair;
import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.groupingBy;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.smartbit4all.api.collection.CollectionApi;
import org.smartbit4all.core.object.ObjectApi;
import org.smartbit4all.core.object.ObjectNode;
import com.google.common.base.Strings;
import hu.aestallon.storageexplorer.domain.storage.model.entry.ObjectEntry;
import hu.aestallon.storageexplorer.domain.storage.model.entry.ScopedEntry;
import hu.aestallon.storageexplorer.domain.storage.model.entry.StorageEntry;
import hu.aestallon.storageexplorer.util.IO;
import static java.util.stream.Collectors.toList;

public abstract class StorageIndex {

  private static final Logger log = LoggerFactory.getLogger(StorageIndex.class);

  protected final StorageId storageId;
  protected final ObjectApi objectApi;
  protected final CollectionApi collectionApi;
  protected final Map<URI, StorageEntry> cache;

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
    cache.putAll(strategy.processEntries(
        fetchEntries(),
        uri -> StorageEntry.create(storageId, uri, objectApi, collectionApi)));
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
        .collect(toList());
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

  public Stream<StorageEntry> entities() {
    return cache.values().stream();
  }

  public Optional<StorageEntry> get(final URI uri) {
    return Optional.ofNullable(cache.get(uri));
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
      if (sb.length() != 0 && Character.isUpperCase(c)) {
        sb.append(".*");
      }
      sb.append(c);
    }
    sb.append(".*");
    return sb.toString();
  }

}

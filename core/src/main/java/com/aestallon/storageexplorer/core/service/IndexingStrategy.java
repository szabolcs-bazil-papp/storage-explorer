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
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import static java.util.stream.Collectors.groupingBy;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.aestallon.storageexplorer.common.util.Pair;
import com.aestallon.storageexplorer.core.model.entry.ObjectEntry;
import com.aestallon.storageexplorer.core.model.entry.ScopedEntry;
import com.aestallon.storageexplorer.core.model.entry.StorageEntry;
import com.aestallon.storageexplorer.core.model.instance.dto.IndexingStrategyType;
import com.aestallon.storageexplorer.core.model.loading.ObjectEntryLoadRequest;
import com.aestallon.storageexplorer.core.model.loading.ObjectEntryLoadResult;
import com.aestallon.storageexplorer.core.util.AbstractEntryEvaluationExecutor;

public interface IndexingStrategy {

  Logger log = LoggerFactory.getLogger(IndexingStrategy.class);


  @FunctionalInterface
  interface StorageEntryCreator extends Function<URI, Optional<? extends StorageEntry>> {}


  IndexingStrategy STRATEGY_ON_DEMAND = new NoOpIndexingStrategy();
  IndexingStrategy STRATEGY_INITIAL = new InitialIndexingStrategy();
  IndexingStrategy STRATEGY_FULL = new FullIndexingStrategy();

  static IndexingStrategy of(IndexingStrategyType type) {
    return switch (type) {
      case null -> STRATEGY_INITIAL;
      case ON_DEMAND -> STRATEGY_ON_DEMAND;
      case INITIAL -> STRATEGY_INITIAL;
      case FULL -> STRATEGY_FULL;
    };
  }

  IndexingStrategyType type();

  boolean fetchEntries();

  Map<URI, StorageEntry> processEntries(Stream<URI> uris, StorageEntryCreator creator);

  final class NoOpIndexingStrategy implements IndexingStrategy {

    @Override
    public IndexingStrategyType type() {
      return IndexingStrategyType.ON_DEMAND;
    }

    @Override
    public boolean fetchEntries() {
      return false;
    }

    @Override
    public Map<URI, StorageEntry> processEntries(Stream<URI> uris, StorageEntryCreator creator) {
      return Collections.emptyMap();
    }
  }


  class InitialIndexingStrategy implements IndexingStrategy {
    @Override
    public IndexingStrategyType type() {
      return IndexingStrategyType.INITIAL;
    }

    @Override
    public boolean fetchEntries() {
      return true;
    }

    @Override
    public Map<URI, StorageEntry> processEntries(Stream<URI> uris, StorageEntryCreator creator) {
      final var map = uris/* .parallel() */
          .map(uri -> Pair.of(uri, creator.apply(uri)))
          .flatMap(Pair.streamOnB())
          .map(Pair.onB(StorageEntry.class::cast))
          .collect(Pair.toMap());

      Map<String, List<ScopedEntry>> scopedEntries = map.values().stream()
          .filter(ScopedEntry.class::isInstance)
          .map(ScopedEntry.class::cast)
          .collect(groupingBy(it -> it.scope().getPath()));

      map.values().stream()
          .filter(ObjectEntry.class::isInstance)
          .map(ObjectEntry.class::cast)
          .forEach(it -> {
            final var scopedChildren = scopedEntries.get(it.uri().getPath());
            if (scopedChildren == null) {
              return;
            }

            scopedChildren.forEach(it::addScopedEntry);
          });
      return map;
    }
  }


  final class FullIndexingStrategy
      extends InitialIndexingStrategy
      implements IndexingStrategy {

    @Override
    public IndexingStrategyType type() {
      return IndexingStrategyType.FULL;
    }

    @Override
    public Map<URI, StorageEntry> processEntries(Stream<URI> uris, StorageEntryCreator creator) {
      final var map = super.processEntries(uris, creator);
      log.info("Indexing strategy FULL: {} entries indexed", map.size());
      log.info("Refreshing {} entries...", map.size());
      new EntryProcessor.Builder(null, new HashSet<>(map.values()))
          .build()
          .execute()
          .forEach(ObjectEntryLoadRequest::get);
      return map;
    }

  }


  final class EntryProcessor
      extends AbstractEntryEvaluationExecutor<ObjectEntryLoadRequest, EntryProcessor> {

    static final class Builder
        extends AbstractEntryEvaluationExecutor.Builder<EntryProcessor, Builder> {

      Builder(StorageInstanceExaminer examiner,
              Set<StorageEntry> entries) {
        super(examiner, entries);
      }

      @Override
      protected Builder self() {
        return this;
      }

      @Override
      public EntryProcessor build() {
        return new EntryProcessor(this);
      }
    }


    private EntryProcessor(Builder builder) {
      super(builder);
    }

    @Override
    protected boolean shortCircuit() {
      return false;
    }

    @Override
    protected boolean doNotExecute() {
      return false;
    }

    @Override
    protected void work(StorageEntry entry) {
      if (entry instanceof ObjectEntry o) {
        results.offer(o.tryLoad());
      } else {
        // FIXME: this is not cool.
        results.offer(
            new ObjectEntryLoadRequest.FileSystemObjectEntryLoadRequest(
                ObjectEntryLoadResult.err("not an object entry")));
      }
    }
  }

}

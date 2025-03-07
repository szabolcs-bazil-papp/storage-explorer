package com.aestallon.storageexplorer.core.service;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;
import com.aestallon.storageexplorer.core.model.entry.ObjectEntry;
import com.aestallon.storageexplorer.core.model.entry.ScopedEntry;
import com.aestallon.storageexplorer.core.model.entry.StorageEntry;
import com.aestallon.storageexplorer.core.model.instance.dto.IndexingStrategyType;
import com.aestallon.storageexplorer.common.util.Pair;
import static java.util.stream.Collectors.groupingBy;

public interface IndexingStrategy {

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
      final var map = uris.parallel()
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
      map.values().stream()
          .parallel()
          .forEach(StorageEntry::refresh);
      return map;
    }

  }

}

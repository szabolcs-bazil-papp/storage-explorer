package hu.aestallon.storageexplorer.spring.service;

import java.util.stream.Stream;
import hu.aestallon.storageexplorer.spring.rest.model.EntryAcquisitionRequest;
import hu.aestallon.storageexplorer.spring.rest.model.EntryAcquisitionResult;
import hu.aestallon.storageexplorer.spring.rest.model.EntryLoadRequest;
import hu.aestallon.storageexplorer.spring.rest.model.EntryLoadResult;
import hu.aestallon.storageexplorer.spring.rest.model.StorageEntryDto;
import hu.aestallon.storageexplorer.spring.rest.model.StorageEntryType;
import hu.aestallon.storageexplorer.spring.rest.model.StorageIndexDto;
import hu.aestallon.storageexplorer.spring.util.IndexingMethod;
import hu.aestallon.storageexplorer.storage.model.entry.ListEntry;
import hu.aestallon.storageexplorer.storage.model.entry.MapEntry;
import hu.aestallon.storageexplorer.storage.model.entry.ObjectEntry;
import hu.aestallon.storageexplorer.storage.model.entry.ScopedEntry;
import hu.aestallon.storageexplorer.storage.model.entry.SequenceEntry;
import hu.aestallon.storageexplorer.storage.model.entry.StorageEntry;
import hu.aestallon.storageexplorer.storage.service.IndexingStrategy;
import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.toList;

public class StorageIndexService {

  private final StorageIndexProvider indexProvider;

  public StorageIndexService(StorageIndexProvider indexProvider) {
    this.indexProvider = indexProvider;
  }

  public StorageIndexDto index(final IndexingMethod indexingMethod) {
    final IndexingStrategy indexingStrategy = switch (indexingMethod) {
      case NONE -> IndexingStrategy.STRATEGY_ON_DEMAND;
      case SURFACE -> IndexingStrategy.STRATEGY_INITIAL;
      case FULL -> IndexingStrategy.STRATEGY_FULL;
    };

    final var index = indexProvider.provide();
    index.refresh(indexingStrategy);
    return index.entities()
        .flatMap(this::entryToDto)
        .collect(collectingAndThen(toList(), new StorageIndexDto()::entries));
  }

  private Stream<StorageEntryDto> entryToDto(final StorageEntry entry) {
    return switch (entry) {
      case ScopedEntry scoped -> Stream.empty();
      case ObjectEntry o -> Stream.of();
      case ListEntry l -> Stream.of();
      case MapEntry m -> Stream.of(new StorageEntryDto()
          .type(StorageEntryType.MAP)
          .uri(m.uri())
          .schema(m.schema())
          .name(m.name()));
      // TODO: ^ References
      case SequenceEntry s -> Stream.of(new StorageEntryDto()
          .type(StorageEntryType.SEQUENCE)
          .uri(s.uri())
          .schema(s.schema())
          .name(s.name())
          .seqVal(s.current()));
    };
  }

  public EntryAcquisitionResult acquire(final EntryAcquisitionRequest acquisitionRequest) {
    
    return new EntryAcquisitionResult();
  }

  public EntryLoadResult load(final EntryLoadRequest loadRequest) {

    return new EntryLoadResult();
  }

}

package com.aestallon.storageexplorer.spring.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import com.aestallon.storageexplorer.spring.rest.model.EntryAcquisitionRequest;
import com.aestallon.storageexplorer.spring.rest.model.EntryAcquisitionResult;
import com.aestallon.storageexplorer.spring.rest.model.EntryLoadRequest;
import com.aestallon.storageexplorer.spring.rest.model.EntryLoadResult;
import com.aestallon.storageexplorer.spring.rest.model.EntryLoadResultType;
import com.aestallon.storageexplorer.spring.rest.model.EntryMeta;
import com.aestallon.storageexplorer.spring.rest.model.EntryVersionDto;
import com.aestallon.storageexplorer.spring.rest.model.Reference;
import com.aestallon.storageexplorer.spring.rest.model.StorageEntryDto;
import com.aestallon.storageexplorer.spring.rest.model.StorageEntryType;
import com.aestallon.storageexplorer.spring.rest.model.StorageIndexDto;
import com.aestallon.storageexplorer.spring.util.IndexingMethod;
import com.aestallon.storageexplorer.core.model.entry.ListEntry;
import com.aestallon.storageexplorer.core.model.entry.MapEntry;
import com.aestallon.storageexplorer.core.model.entry.ObjectEntry;
import com.aestallon.storageexplorer.core.model.entry.ScopedEntry;
import com.aestallon.storageexplorer.core.model.entry.ScopedListEntry;
import com.aestallon.storageexplorer.core.model.entry.ScopedMapEntry;
import com.aestallon.storageexplorer.core.model.entry.ScopedObjectEntry;
import com.aestallon.storageexplorer.core.model.entry.SequenceEntry;
import com.aestallon.storageexplorer.core.model.entry.StorageEntry;
import com.aestallon.storageexplorer.core.model.entry.UriProperty;
import com.aestallon.storageexplorer.core.model.loading.ObjectEntryLoadResult;
import com.aestallon.storageexplorer.core.service.IndexingStrategy;
import com.aestallon.storageexplorer.core.service.StorageIndex;
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
        .flatMap(it -> entryToDto(it, IndexingMethod.FULL == indexingMethod))
        .collect(collectingAndThen(toList(), new StorageIndexDto()::entries));
  }

  private Stream<StorageEntryDto> entryToDto(final StorageEntry entry, boolean needRefs) {
    return switch (entry) {
      case ScopedEntry scoped -> Stream.empty();
      case ObjectEntry o -> Stream.concat(
          Stream.of(convertObjectEntry(needRefs, o)),
          o.scopedEntries().stream()
              .map(it -> switch (it) {
                case ScopedObjectEntry soe -> convertObjectEntry(needRefs, soe);
                case ScopedListEntry sle -> convertListEntry(needRefs, sle);
                case ScopedMapEntry sme -> convertMapEntry(needRefs, sme);
              })
              .map(it -> it.scopeHost(o.uri())));
      case ListEntry l -> Stream.of(convertListEntry(needRefs, l));
      case MapEntry m -> Stream.of(convertMapEntry(needRefs, m));
      case SequenceEntry s -> Stream.of(convertSequenceEntry(s));
    };
  }

  private static StorageEntryDto convertSequenceEntry(SequenceEntry s) {
    return new StorageEntryDto()
        .type(StorageEntryType.SEQUENCE)
        .uri(s.uri())
        .schema(s.schema())
        .name(s.name())
        .seqVal(s.current());
  }

  private StorageEntryDto convertMapEntry(boolean needRefs, MapEntry m) {
    return new StorageEntryDto()
        .type(StorageEntryType.MAP)
        .uri(m.uri())
        .schema(m.schema())
        .name(m.name())
        .references(needRefs ? uriPropertiesToReferences(m.uriProperties()) : new ArrayList<>());
  }

  private StorageEntryDto convertListEntry(boolean needRefs, ListEntry l) {
    return new StorageEntryDto()
        .type(StorageEntryType.LIST)
        .uri(l.uri())
        .schema(l.schema())
        .name(l.name())
        .references(needRefs ? uriPropertiesToReferences(l.uriProperties()) : new ArrayList<>());
  }

  private StorageEntryDto convertObjectEntry(boolean needRefs, ObjectEntry o) {
    return new StorageEntryDto()
        .type(StorageEntryType.OBJECT)
        .uri(o.uri())
        .schema(o.uri().getScheme())
        .name(o.uuid())
        .typeName(o.typeName())
        .references(needRefs ? uriPropertiesToReferences(o.uriProperties()) : new ArrayList<>());
  }

  private List<Reference> uriPropertiesToReferences(final Collection<UriProperty> uriProperties) {
    return uriProperties.stream()
        .map(it -> new Reference()
            .propName(it.propertyName())
            .uri(it.uri())
            .pos(it.position < 0 ? null : it.position))
        .toList();
  }

  public EntryAcquisitionResult acquire(final EntryAcquisitionRequest acquisitionRequest) {
    final var uris = acquisitionRequest.getUris();
    if (uris.isEmpty()) {
      return new EntryAcquisitionResult();
    }

    final var index = indexProvider.provide();
    index.refresh(IndexingStrategy.STRATEGY_ON_DEMAND);
    final var entries = uris.stream().map(index::getOrCreate)
        .filter(it -> StorageIndex.AcquisitionKind.FAIL != it.kind())
        .map(StorageIndex.EntryAcquisitionResult::entry)
        .toList();
    index.revalidate(entries);
    return entries.stream()
        .flatMap(it -> entryToDto(it, true))
        .collect(collectingAndThen(toList(), new EntryAcquisitionResult()::entries));

  }

  public EntryLoadResult load(final EntryLoadRequest loadRequest) {
    final var uri = loadRequest.getUri();
    final var index = indexProvider.provide();
    final var result = index.getOrCreate(uri);
    if (StorageIndex.AcquisitionKind.FAIL == result.kind()) {
      return new EntryLoadResult().type(EntryLoadResultType.FAILED);
    }

    final StorageEntry entry = result.entry();
    final StorageEntryDto entryDto;
    final List<EntryVersionDto> versions;
    final EntryLoadResultType loadResultType;
    switch (entry) {
      case ListEntry l -> {
        entryDto = convertListEntry(true, l);
        versions = Collections.emptyList();
        loadResultType = EntryLoadResultType.SINGLE;
      }
      case MapEntry m -> {
        entryDto = convertMapEntry(true, m);
        versions = Collections.emptyList();
        loadResultType = EntryLoadResultType.SINGLE;
      }
      case SequenceEntry s -> {
        entryDto = convertSequenceEntry(s);
        versions = Collections.emptyList();
        loadResultType = EntryLoadResultType.SINGLE;
      }
      case ObjectEntry o -> {
        entryDto = convertObjectEntry(true, o);
        final var loadResult = o.tryLoad();
        switch (loadResult) {
          case ObjectEntryLoadResult.Err err -> {
            return new EntryLoadResult().type(EntryLoadResultType.FAILED);
          }
          case ObjectEntryLoadResult.SingleVersion sv -> {
            versions = Collections.singletonList(convertVersionToDto(sv));
            loadResultType = EntryLoadResultType.SINGLE;
          }
          case ObjectEntryLoadResult.MultiVersion mv -> {
            versions = mv.versions().stream().map(this::convertVersionToDto).toList();
            loadResultType = EntryLoadResultType.MULTI;
          }
        }
      }
    }

    return new EntryLoadResult()
        .type(loadResultType)
        .entry(entryDto)
        .versions(versions);
  }

  private EntryVersionDto convertVersionToDto(final ObjectEntryLoadResult.SingleVersion sv) {
    final var meta = sv.meta();
    return new EntryVersionDto()
        .meta(new EntryMeta()
            .uri(meta.uri())
            .storageSchema(meta.storageSchema())
            .qualifiedName(meta.qualifiedName())
            .versionNr(meta.versionNr())
            .createdAt(meta.createdAt())
            .lastModifiedAt(meta.lastModified()))
        .objectAsMap(sv.objectAsMap());
  }

}

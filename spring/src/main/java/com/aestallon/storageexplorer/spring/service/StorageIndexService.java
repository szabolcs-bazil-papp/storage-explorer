package com.aestallon.storageexplorer.spring.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.toList;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import com.aestallon.storageexplorer.arcscript.api.Arc;
import com.aestallon.storageexplorer.arcscript.engine.ArcScriptResult;
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
import com.aestallon.storageexplorer.core.model.instance.StorageInstance;
import com.aestallon.storageexplorer.core.model.instance.dto.Availability;
import com.aestallon.storageexplorer.core.model.instance.dto.FsStorageLocation;
import com.aestallon.storageexplorer.core.model.instance.dto.IndexingStrategyType;
import com.aestallon.storageexplorer.core.model.instance.dto.SqlStorageLocation;
import com.aestallon.storageexplorer.core.model.instance.dto.StorageInstanceDto;
import com.aestallon.storageexplorer.core.model.instance.dto.StorageInstanceType;
import com.aestallon.storageexplorer.core.model.loading.ObjectEntryLoadResult;
import com.aestallon.storageexplorer.core.service.FileSystemStorageIndex;
import com.aestallon.storageexplorer.core.service.IndexingStrategy;
import com.aestallon.storageexplorer.core.service.RelationalDatabaseStorageIndex;
import com.aestallon.storageexplorer.core.service.StorageIndex;
import com.aestallon.storageexplorer.spring.rest.model.ArcScriptColumnDescriptor;
import com.aestallon.storageexplorer.spring.rest.model.ArcScriptEvalError;
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
            .propName(it.toString())
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
        .flatMap(it -> switch (it) {
          case StorageIndex.EntryAcquisitionResult.New(var e) -> Stream.of(e);
          case StorageIndex.EntryAcquisitionResult.Present(var e) -> Stream.of(e);
          case StorageIndex.EntryAcquisitionResult.Fail f -> Stream.empty();
        })
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

    final StorageEntry entry = switch (result) {
      case StorageIndex.EntryAcquisitionResult.New(var e) -> e;
      case StorageIndex.EntryAcquisitionResult.Present(var e) -> e;
      case StorageIndex.EntryAcquisitionResult.Fail f -> null;
    };
    if (entry == null) {
      return new EntryLoadResult().type(EntryLoadResultType.FAILED);
    }

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
        final var loadResult = o.tryLoad().get();
        switch (loadResult) {
          case ObjectEntryLoadResult.Err err -> {
            return new EntryLoadResult().type(EntryLoadResultType.FAILED);
          }
          case ObjectEntryLoadResult.SingleVersion sv -> {
            versions = Collections.singletonList(convertVersionToDto(sv));
            loadResultType = EntryLoadResultType.SINGLE;
          }
          case ObjectEntryLoadResult.MultiVersion(var vs) -> {
            versions = vs.stream().map(this::convertVersionToDto).toList();
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

  public sealed interface ArcScriptQueryEvalResult {

    record Ok(List<ArcScriptColumnDescriptor> columns, List<Object> resultSet)
        implements ArcScriptQueryEvalResult {}


    record Err(ArcScriptEvalError err) implements ArcScriptQueryEvalResult {}

  }

  public ArcScriptQueryEvalResult evalArcScript(final String script) {
    final StorageIndex<?> index = indexProvider.provide();

    final StorageInstanceDto temp = new StorageInstanceDto();
    switch (index) {
      case RelationalDatabaseStorageIndex rdsi -> temp
          .db(new SqlStorageLocation())
          .type(StorageInstanceType.DB);
      case FileSystemStorageIndex fssi -> temp
          .fs(new FsStorageLocation())
          .type(StorageInstanceType.FS);
    }

    final StorageInstance storageInstance = StorageInstance.fromDto(temp
        .indexingStrategy(IndexingStrategyType.ON_DEMAND)
        .availability(Availability.AVAILABLE));
    storageInstance.setIndex(index);

    return switch (Arc.evaluate(script, storageInstance)) {
      case ArcScriptResult.CompilationError cErr ->
          new ArcScriptQueryEvalResult.Err(new ArcScriptEvalError()
              .msg(cErr.msg())
              .line(cErr.line())
              .col(cErr.col()));
      case ArcScriptResult.ImpermissibleInstruction iErr ->
          new ArcScriptQueryEvalResult.Err(new ArcScriptEvalError().msg(iErr.msg()));
      case ArcScriptResult.UnknownError uErr ->
          new ArcScriptQueryEvalResult.Err(new ArcScriptEvalError().msg(uErr.msg()));
      case ArcScriptResult.Ok(List<ArcScriptResult.InstructionResult> results) -> results.stream()
          .filter(it -> it instanceof ArcScriptResult.QueryPerformed)
          .map(ArcScriptResult.QueryPerformed.class::cast)
          .findFirst()
          .map(ArcScriptResult.QueryPerformed::resultSet)
          .map(this::unwrapResultSet)
          .orElseGet(() -> new ArcScriptQueryEvalResult.Ok(
              Collections.emptyList(),
              Collections.emptyList()));
    };
  }

  private ArcScriptQueryEvalResult.Ok unwrapResultSet(final ArcScriptResult.ResultSet resultSet) {
    final var rowCreator = RowCreator.newInstance(resultSet.meta());
    final var rows = new ArrayList<>();
    for (final var row : resultSet.rows()) {
      rows.add(rowCreator.getRow(row));
    }
    return new ArcScriptQueryEvalResult.Ok(rowCreator.getColumnDescriptors(), rows);
  }

  private sealed interface RowCreator {

    static RowCreator newInstance(ArcScriptResult.ResultSetMeta meta) {
      return (meta.columns().isEmpty())
          ? new Default()
          : new Custom(meta);
    }

    Map<String, Object> getRow(ArcScriptResult.QueryResultRow row);

    List<ArcScriptColumnDescriptor> getColumnDescriptors();


    final class Custom implements RowCreator {

      private final String[] headers;
      private final String[] props;

      private Custom(ArcScriptResult.ResultSetMeta meta) {
        final var columns = meta.columns();
        headers = new String[columns.size()];
        props = new String[columns.size()];
        for (int i = 0; i < columns.size(); i++) {
          final var col = columns.get(i);
          headers[i] = col.title();
          props[i] = col.prop();
        }
      }

      @Override
      public Map<String, Object> getRow(ArcScriptResult.QueryResultRow row) {
        final Map<String, Object> ret = new LinkedHashMap<>();
        final var cells = row.cells();
        for (int i = 0; i < props.length; i++) {
          final var prop = props[i];
          final var header = headers[i];

          final var cell = cells.get(prop);
          if (cell != null) {
            ret.put(header, cell.value());
          }
        }
        return ret;
      }

      @Override
      public List<ArcScriptColumnDescriptor> getColumnDescriptors() {
        return IntStream.range(0, headers.length)
            .mapToObj(i -> new ArcScriptColumnDescriptor().column(props[i]).alias(headers[i]))
            .toList();
      }

    }


    final class Default implements RowCreator {

      @Override
      public Map<String, Object> getRow(ArcScriptResult.QueryResultRow row) {
        return Map.of("uri", row.entry().uri().toString());
      }

      @Override
      public List<ArcScriptColumnDescriptor> getColumnDescriptors() {
        return Collections.singletonList(new ArcScriptColumnDescriptor().column("uri"));
      }

    }
  }

}

package com.aestallon.storageexplorer.spring.service;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.toList;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import com.aestallon.storageexplorer.arcscript.api.Arc;
import com.aestallon.storageexplorer.arcscript.engine.ArcScriptResult;
import com.aestallon.storageexplorer.common.util.Pair;
import com.aestallon.storageexplorer.core.model.EntryUri;
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
import com.aestallon.storageexplorer.core.model.instance.dto.StorageInstanceDto;
import com.aestallon.storageexplorer.core.model.instance.dto.StorageInstanceType;
import com.aestallon.storageexplorer.core.model.loading.IndexingTarget;
import com.aestallon.storageexplorer.core.model.loading.ObjectEntryLoadResult;
import com.aestallon.storageexplorer.core.service.engine.LoadedContent;
import com.aestallon.storageexplorer.core.service.engine.StorageEngine;
import com.aestallon.storageexplorer.core.service.index.result.AdjacencyResult;
import com.aestallon.storageexplorer.core.service.index.result.ContentLoadResult;
import com.aestallon.storageexplorer.core.service.index.result.EntryResolution;
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

/**
 * Rewritten for Milestone 7 (T7.5): a fresh {@link StorageInstance} (backed by a fresh {@link
 * StorageEngine} the {@link StorageIndexProvider} opens) is built per request, mirroring the old
 * per-call {@code StorageIndexProvider#provide()} index construction exactly — nothing here was
 * ever cached across requests, so the new architecture's transient, operation-scoped philosophy
 * is a close match, not a behavior change. The RDB side (the old {@code
 * RelationalDatabaseStorageIndex}-backed branch) is gone with no replacement, per the same
 * explicit decision Milestone 4 (T4.2) made for {@code core}/{@code client} — {@code
 * RdbStorageEngine} doesn't exist yet.
 */
public class StorageIndexService {

  private final StorageIndexProvider indexProvider;

  public StorageIndexService(StorageIndexProvider indexProvider) {
    this.indexProvider = indexProvider;
  }

  private StorageInstance newStorageInstance() {
    final StorageInstance storageInstance = StorageInstance.fromDto(new StorageInstanceDto()
        .type(StorageInstanceType.FS)
        .fs(new FsStorageLocation())
        .indexingStrategy(IndexingStrategyType.ON_DEMAND)
        .availability(Availability.AVAILABLE));
    storageInstance.setEngine(indexProvider.provide());
    return storageInstance;
  }

  public StorageIndexDto index(final IndexingMethod indexingMethod) {
    final StorageInstance storageInstance = newStorageInstance();
    if (IndexingMethod.NONE == indexingMethod) {
      return new StorageIndexDto().entries(List.of());
    }

    storageInstance.treeIndex().rebuildSurface(IndexingTarget.any());
    final boolean needRefs = IndexingMethod.FULL == indexingMethod;
    return storageInstance.treeIndex().entries().stream()
        .flatMap(it -> entryToDto(storageInstance, it, needRefs))
        .collect(collectingAndThen(toList(), new StorageIndexDto()::entries));
  }

  private Stream<StorageEntryDto> entryToDto(final StorageInstance storageInstance,
                                             final StorageEntry entry, boolean needRefs) {
    return switch (entry) {
      case ScopedEntry scoped -> Stream.empty();
      case ObjectEntry o -> Stream.concat(
          Stream.of(convertObjectEntry(storageInstance, needRefs, o)),
          storageInstance.scopedChildrenOf(o).stream()
              .map(it -> switch (it) {
                case ScopedObjectEntry soe -> convertObjectEntry(storageInstance, needRefs, soe);
                case ScopedListEntry sle -> convertListEntry(storageInstance, needRefs, sle);
                case ScopedMapEntry sme -> convertMapEntry(storageInstance, needRefs, sme);
              })
              .map(it -> it.scopeHost(URI.create(o.uri().toString()))));
      case ListEntry l -> Stream.of(convertListEntry(storageInstance, needRefs, l));
      case MapEntry m -> Stream.of(convertMapEntry(storageInstance, needRefs, m));
      case SequenceEntry s -> Stream.of(convertSequenceEntry(storageInstance, s));
      // ObjectStreamEntry/TableDataEntry: not yet representable by this DTO (T6.7/T7.6, confirmed
      // wanted, not designed):
      default -> Stream.empty();
    };
  }

  private StorageEntryDto convertSequenceEntry(final StorageInstance storageInstance,
                                               final SequenceEntry s) {
    return new StorageEntryDto()
        .type(StorageEntryType.SEQUENCE)
        .uri(URI.create(s.uri().toString()))
        .schema(s.schema())
        .name(s.name())
        .seqVal(currentValueOf(storageInstance, s));
  }

  /** The sequence's current value, read fresh off its content (the {@code "current"} field) —
   *  replaces the removed {@code SequenceEntry#current()} (T4.1). */
  private long currentValueOf(final StorageInstance storageInstance, final SequenceEntry s) {
    return switch (storageInstance.read(s)) {
      case ContentLoadResult.Ok ok -> switch (ok.content().materialize()) {
        case LoadedContent.JsonObject(var data) -> {
          try {
            yield Long.parseLong(String.valueOf(data.get("current")));
          } catch (final Exception e) {
            yield -1L;
          }
        }
        default -> -1L;
      };
      case ContentLoadResult.Err err -> -1L;
    };
  }

  private StorageEntryDto convertMapEntry(final StorageInstance storageInstance,
                                          boolean needRefs, MapEntry m) {
    return new StorageEntryDto()
        .type(StorageEntryType.MAP)
        .uri(URI.create(m.uri().toString()))
        .schema(m.schema())
        .name(m.name())
        .references(needRefs
            ? uriPropertiesToReferences(outgoingReferences(storageInstance, m))
            : new ArrayList<>());
  }

  private StorageEntryDto convertListEntry(final StorageInstance storageInstance,
                                           boolean needRefs, ListEntry l) {
    return new StorageEntryDto()
        .type(StorageEntryType.LIST)
        .uri(URI.create(l.uri().toString()))
        .schema(l.schema())
        .name(l.name())
        .references(needRefs
            ? uriPropertiesToReferences(outgoingReferences(storageInstance, l))
            : new ArrayList<>());
  }

  private StorageEntryDto convertObjectEntry(final StorageInstance storageInstance,
                                             boolean needRefs, ObjectEntry o) {
    return new StorageEntryDto()
        .type(StorageEntryType.OBJECT)
        .uri(URI.create(o.uri().toString()))
        .schema(o.uri().scheme())
        .name(o.uuid())
        .typeName(o.typeName())
        .references(needRefs
            ? uriPropertiesToReferences(outgoingReferences(storageInstance, o))
            : new ArrayList<>());
  }

  /** {@code entry}'s outgoing references — replaces the removed {@code
   *  StorageEntry#uriProperties()} (T4.1) with the same adjacency scan every other Milestone 3
   *  index already uses for graph traversal. */
  private Set<UriProperty> outgoingReferences(final StorageInstance storageInstance,
                                              final StorageEntry entry) {
    try (var index = storageInstance.newGraphIndex()) {
      return switch (index.outgoingReferences(entry)) {
        case AdjacencyResult.Ok(var e, var refs) -> refs;
        case AdjacencyResult.Err err -> Set.of();
      };
    }
  }

  private List<Reference> uriPropertiesToReferences(final Collection<UriProperty> uriProperties) {
    return uriProperties.stream()
        .map(it -> new Reference()
            .propName(it.toString())
            .uri(it.uri())
            .pos(it.position < 0 ? null : it.position))
        .toList();
  }

  private static Optional<StorageEntry> resolve(final StorageInstance storageInstance, final URI uri) {
    return EntryUri.parse(uri.toString())
        .map(storageInstance::resolve)
        .flatMap(res -> switch (res) {
          case EntryResolution.Known(var entry) -> Optional.of(entry);
          case EntryResolution.Unknown u -> Optional.<StorageEntry>empty();
        });
  }

  public EntryAcquisitionResult acquire(final EntryAcquisitionRequest acquisitionRequest) {
    final var uris = acquisitionRequest.getUris();
    if (uris.isEmpty()) {
      return new EntryAcquisitionResult();
    }

    final StorageInstance storageInstance = newStorageInstance();
    return uris.stream()
        .flatMap(uri -> resolve(storageInstance, uri).stream())
        // an ObjectEntry that fails to load is dropped, same as the old "verify validity" pass:
        .filter(it -> !(it instanceof ObjectEntry o)
            || storageInstance.read(o) instanceof ContentLoadResult.Ok)
        .flatMap(it -> entryToDto(storageInstance, it, true))
        .collect(collectingAndThen(toList(), new EntryAcquisitionResult()::entries));
  }

  public EntryLoadResult load(final EntryLoadRequest loadRequest) {
    final StorageInstance storageInstance = newStorageInstance();
    final StorageEntry entry = resolve(storageInstance, loadRequest.getUri()).orElse(null);
    if (entry == null) {
      return new EntryLoadResult().type(EntryLoadResultType.FAILED);
    }

    final StorageEntryDto entryDto;
    final List<EntryVersionDto> versions;
    final EntryLoadResultType loadResultType;
    switch (entry) {
      case ListEntry l -> {
        entryDto = convertListEntry(storageInstance, true, l);
        versions = Collections.emptyList();
        loadResultType = EntryLoadResultType.SINGLE;
      }
      case MapEntry m -> {
        entryDto = convertMapEntry(storageInstance, true, m);
        versions = Collections.emptyList();
        loadResultType = EntryLoadResultType.SINGLE;
      }
      case SequenceEntry s -> {
        entryDto = convertSequenceEntry(storageInstance, s);
        versions = Collections.emptyList();
        loadResultType = EntryLoadResultType.SINGLE;
      }
      case ObjectEntry o -> {
        entryDto = convertObjectEntry(storageInstance, true, o);
        final var loadResult = ObjectEntryLoadResult.load(storageInstance, o);
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
      default -> {
        // ObjectStreamEntry/TableDataEntry: not yet representable (T6.7/T7.6):
        return new EntryLoadResult().type(EntryLoadResultType.FAILED);
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

    record Ok(List<ArcScriptColumnDescriptor> columns, String entryUriKey, List<Object> resultSet)
        implements ArcScriptQueryEvalResult {}


    record Err(ArcScriptEvalError err) implements ArcScriptQueryEvalResult {}

  }

  public ArcScriptQueryEvalResult evalArcScript(final String script) {
    final StorageInstance storageInstance = newStorageInstance();

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
      case ArcScriptResult.Ok(var results, var verbose) -> results.stream()
          .filter(it -> it instanceof ArcScriptResult.QueryPerformed)
          .map(ArcScriptResult.QueryPerformed.class::cast)
          .findFirst()
          .map(ArcScriptResult.QueryPerformed::resultSet)
          .map(this::unwrapResultSet)
          .orElseGet(() -> new ArcScriptQueryEvalResult.Ok(
              Collections.emptyList(),
              "uri",
              Collections.emptyList()));
    };
  }

  private ArcScriptQueryEvalResult.Ok unwrapResultSet(final ArcScriptResult.ResultSet resultSet) {
    final var rowCreator = RowCreator.newInstance(resultSet.meta());
    final var rows = new ArrayList<>();
    for (final var row : resultSet.rows()) {
      rows.add(rowCreator.getRow(row));
    }

    return new ArcScriptQueryEvalResult.Ok(
        rowCreator.getColumnDescriptors(),
        rowCreator.entryUriKey(),
        rows);
  }

  private sealed interface RowCreator {

    static RowCreator newInstance(ArcScriptResult.ResultSetMeta meta) {
      return (meta.columns().isEmpty())
          ? new Default()
          : new Custom(meta);
    }

    Map<String, Object> getRow(ArcScriptResult.QueryResultRow row);

    List<ArcScriptColumnDescriptor> getColumnDescriptors();

    String entryUriKey();


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
        ret.put(entryUriKey(), row.entry().uri().toString());
        return ret;
      }

      @Override
      public List<ArcScriptColumnDescriptor> getColumnDescriptors() {
        return IntStream.range(0, headers.length)
            .mapToObj(i -> new ArcScriptColumnDescriptor().column(props[i]).alias(headers[i]))
            .toList();
      }

      @Override
      public String entryUriKey() {
        return "__uri";
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

      @Override
      public String entryUriKey() {
        return "uri";
      }
    }
  }

}

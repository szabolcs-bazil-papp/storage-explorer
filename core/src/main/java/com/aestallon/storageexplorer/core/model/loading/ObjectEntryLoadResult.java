package com.aestallon.storageexplorer.core.model.loading;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import com.aestallon.storageexplorer.core.model.entry.StorageEntry;
import com.aestallon.storageexplorer.core.model.instance.StorageInstance;
import com.aestallon.storageexplorer.core.service.engine.ContentHandle;
import com.aestallon.storageexplorer.core.service.engine.EngineResult;
import com.aestallon.storageexplorer.core.service.engine.EntryMetadata;
import com.aestallon.storageexplorer.core.service.engine.LoadedContent;
import com.aestallon.storageexplorer.core.service.engine.VersionedContent;
import com.aestallon.storageexplorer.core.service.index.MetadataAware;
import com.aestallon.storageexplorer.core.service.index.result.ContentLoadResult;
import com.aestallon.storageexplorer.core.service.index.result.MetadataResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Still-live display model for {@code swing}/{@code app-cli}'s entry inspectors — nobody built
 * its instances anymore after {@code ObjectEntryLoadingService} was deleted (Milestone 4) until
 * {@link #load}/{@link #asSingleVersion} (T7.1) took over that job against the new {@link
 * StorageInstance#read}/{@link StorageInstance#history} primitives, replacing the old {@code
 * ObjectNode}-based factories (removed here, dead since Milestone 4 — their only caller was the
 * deleted loading service) entirely.
 */
public sealed interface ObjectEntryLoadResult permits
    ObjectEntryLoadResult.Err,
    ObjectEntryLoadResult.SingleVersion,
    ObjectEntryLoadResult.MultiVersion {

  ObjectMapper OBJECT_MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

  static ObjectEntryLoadResult.Err err(String msg) {
    return new ObjectEntryLoadResult.Err(msg);
  }

  /** Loads {@code entry}'s full version history (single-version entries report one "version":
   *  their one content) — the display-level replacement for the removed {@code
   *  StorageEntry#tryLoad()} (T4.1). */
  static ObjectEntryLoadResult load(final StorageInstance storageInstance, final StorageEntry entry) {
    if (entry.uri().isSingleVersion()) {
      return switch (storageInstance.read(entry)) {
        case ContentLoadResult.Ok ok -> singleVersionOf(entry, metadataOf(storageInstance, entry), ok.content());
        case ContentLoadResult.Err err -> new ObjectEntryLoadResult.Err(err.reason());
      };
    }

    return switch (storageInstance.history(entry)) {
      case EngineResult.Ok<List<VersionedContent>> ok -> new ObjectEntryLoadResult.MultiVersion(
          ok.value().stream()
              .map(vc -> singleVersionOf(entry, vc.metadata(), vc.content()))
              .toList());
      case EngineResult.Err<List<VersionedContent>> err -> new ObjectEntryLoadResult.Err(err.error().toString());
    };
  }

  /** For a stored {@code ListEntry}/{@code MapEntry}'s own content (always single-version) —
   *  the replacement for the removed {@code ListEntry}/{@code MapEntry#asSingleVersion()}
   *  (T4.1). */
  static Optional<ObjectEntryLoadResult.SingleVersion> asSingleVersion(
      final StorageInstance storageInstance, final StorageEntry entry) {
    return switch (load(storageInstance, entry)) {
      case ObjectEntryLoadResult.SingleVersion sv -> Optional.of(sv);
      case ObjectEntryLoadResult.MultiVersion mv -> Optional.of(mv.head());
      case ObjectEntryLoadResult.Err err -> Optional.empty();
    };
  }

  private static EntryMetadata metadataOf(final StorageInstance storageInstance, final StorageEntry entry) {
    try (MetadataAware stats = storageInstance.newStatsIndex()) {
      return switch (stats.metadataOf(entry)) {
        case MetadataResult.Ok(var e, var meta) -> meta;
        case MetadataResult.Err err -> new EntryMetadata(entry.uri(), null, null, null, null, null);
      };
    }
  }

  @SuppressWarnings("unchecked")
  private static ObjectEntryLoadResult.SingleVersion singleVersionOf(final StorageEntry entry,
                                                                      final EntryMetadata meta,
                                                                      final ContentHandle content) {
    final Map<String, Object> objectAsMap = switch (content.materialize()) {
      case LoadedContent.JsonObject(var data) -> (Map<String, Object>) data;
      default -> new LinkedHashMap<>();
    };
    final ObjectEntryMeta m = new ObjectEntryMeta(
        URI.create(entry.uri().toString()),
        meta.qualifiedName(),
        entry.uri().scheme(),
        meta.versionNr(),
        meta.createdAt(),
        meta.lastModified(),
        meta.entryId());
    return new ObjectEntryLoadResult.SingleVersion.Eager(m, objectAsMap, OBJECT_MAPPER);
  }


  boolean isOk();

  default boolean isErr() { return !isOk(); }

  record Err(String msg) implements ObjectEntryLoadResult {

    @Override
    public boolean isOk() {
      return false;
    }

  }


  /*
   * The previous implementation was a disgrace: we eagerly loaded every object version, AND we
   * serialised it back to have a nice oamStr ready to go. This was horribly inefficient
   * (flame-graph revealed we spend most of the time deserialising and re-serialising entry versions
   * which we probably will never need...). Thus, we load every non-head object version lazily,
   * on-demand - at least if we are running on FS storage...
   */
  sealed interface SingleVersion extends ObjectEntryLoadResult {

    ObjectEntryMeta meta();

    Map<String, Object> objectAsMap();

    String oamStr();

    final class Eager implements SingleVersion {

      private final ObjectEntryMeta meta;
      private final Map<String, Object> objectAsMap;
      private final Supplier<String> oamSupplier;

      public Eager(final ObjectEntryMeta meta,
                   final Map<String, Object> objectAsMap,
                   final ObjectMapper objectMapper) {
        this.meta = meta;
        this.objectAsMap = objectAsMap;
        oamSupplier = () -> {
          try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(objectAsMap);
          } catch (JsonProcessingException e) {
            return "ERROR SERIALIZING OBJECT-AS-MAP!";
          }
        };
      }

      @Override
      public ObjectEntryMeta meta() {
        return meta;
      }

      @Override
      public Map<String, Object> objectAsMap() {
        return objectAsMap;
      }

      @Override
      public String oamStr() {
        return oamSupplier.get();
      }

      @Override
      public boolean isOk() {
        return true;
      }
    }


    final class Lazy implements SingleVersion {

      private final Supplier<Eager> supplier;
      private Eager inner;

      Lazy(final Supplier<Eager> supplier) {
        this.supplier = Objects.requireNonNull(supplier, "Eager supplier must not be null!");
      }

      private void ensure() {
        if (inner == null) {
          inner = supplier.get();
        }
      }

      @Override
      public ObjectEntryMeta meta() {
        ensure();
        return inner.meta();
      }

      @Override
      public Map<String, Object> objectAsMap() {
        ensure();
        return inner.objectAsMap();
      }

      @Override
      public String oamStr() {
        ensure();
        return inner.oamStr();
      }

      @Override
      public boolean isOk() {
        return true;
      }
    }

  }


  record MultiVersion(List<SingleVersion> versions) implements ObjectEntryLoadResult {

    @Override
    public boolean isOk() {
      return true;
    }

    public SingleVersion head() {
      return versions.getLast();
    }

  }
}

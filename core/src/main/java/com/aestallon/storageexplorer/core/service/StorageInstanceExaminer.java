package com.aestallon.storageexplorer.core.service;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import com.aestallon.storageexplorer.core.model.entry.ObjectEntry;
import com.aestallon.storageexplorer.core.model.entry.StorageEntry;
import com.aestallon.storageexplorer.core.model.loading.ObjectEntryLoadResult;

public class StorageInstanceExaminer {

  private final Function<URI, Optional<StorageEntry>> discoverer;

  public StorageInstanceExaminer(final Function<URI, Optional<StorageEntry>> discoverer) {
    this.discoverer = discoverer;
  }

  public PropertyDiscoveryResult discoverProperty(final StorageEntry entry,
                                                  final String propQuery) {
    return switch (entry) {
      case ObjectEntry o -> {
        final var loadResult = o.tryLoad();
        yield switch (loadResult) {
          case ObjectEntryLoadResult.Err err -> new NotFound();
          case ObjectEntryLoadResult.SingleVersion sv -> inVersion(sv, entry, propQuery);
          case ObjectEntryLoadResult.MultiVersion mv -> mv.versions().stream()
              .map(sv -> inVersion(sv, entry, propQuery))
              .filter(it -> !(it instanceof NotFound))
              .findFirst()
              .orElseGet(NotFound::new);
        };
      }
      case null, default -> new NotFound();
    };
  }

  private PropertyDiscoveryResult inVersion(final ObjectEntryLoadResult.SingleVersion sv,
                                            final StorageEntry host,
                                            final String propQuery) {
    final Map<String, Object> oam = sv.objectAsMap();
    final Object value = oam.get(propQuery);
    return switch (value) {
      case null -> new NotFound();
      case List<?> list -> new ListFound<>((List<Object>) list, host);
      case Map<?, ?> map -> new ComplexFound((Map<String, Object>) map, host);
      default -> new PrimitiveFound<>(value, host);
    };
  }



  public sealed interface PropertyDiscoveryResult {

  }


  public record NotFound() implements PropertyDiscoveryResult {}


  public record PrimitiveFound<T>(T value, StorageEntry host) implements PropertyDiscoveryResult {}


  public record ComplexFound(Map<String, Object> value, StorageEntry host)
      implements PropertyDiscoveryResult {}


  public record ListFound<E>(List<E> value, StorageEntry host) implements PropertyDiscoveryResult {}
}

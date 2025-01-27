package com.aestallon.storageexplorer.core.service;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import com.aestallon.storageexplorer.core.model.entry.ObjectEntry;
import com.aestallon.storageexplorer.core.model.entry.StorageEntry;
import com.aestallon.storageexplorer.core.model.loading.ObjectEntryLoadResult;
import static com.aestallon.storageexplorer.common.util.Streams.reverse;

public class StorageInstanceExaminer {

  public static final class ObjectEntryLookupTable {

    public static ObjectEntryLookupTable newInstance() {
      return new ObjectEntryLookupTable();
    }

    private final ConcurrentHashMap<ObjectEntry, ObjectEntryLoadResult> inner;

    private ObjectEntryLookupTable() {
      inner = new ConcurrentHashMap<>();
    }

    private ObjectEntryLoadResult computeIfAbsent(final ObjectEntry objectEntry,
                                                  final Function<? super ObjectEntry, ? extends ObjectEntryLoadResult> f) {
      return inner.computeIfAbsent(objectEntry, f);
    }
  }


  private final Function<URI, Optional<StorageEntry>> discoverer;

  public StorageInstanceExaminer(final Function<URI, Optional<StorageEntry>> discoverer) {
    this.discoverer = discoverer;
  }

  public PropertyDiscoveryResult discoverProperty(final StorageEntry entry,
                                                  final String propQuery) {
    final var cache = ObjectEntryLookupTable.newInstance();
    return discoverProperty(entry, propQuery, cache);
  }

  public PropertyDiscoveryResult discoverProperty(final StorageEntry entry,
                                                  final String propQuery,
                                                  final ObjectEntryLookupTable cache) {
    return switch (entry) {
      case ObjectEntry o -> {
        final var loadResult = cache.computeIfAbsent(o, ObjectEntry::tryLoad);
        yield switch (loadResult) {
          case ObjectEntryLoadResult.Err err -> new NotFound(err.msg());
          case ObjectEntryLoadResult.SingleVersion sv -> inVersion(sv, entry, propQuery, cache);
          case ObjectEntryLoadResult.MultiVersion mv -> mv.versions().stream()
              .collect(reverse())
              .map(sv -> inVersion(sv, entry, propQuery, cache))
              .filter(it -> !(it instanceof NotFound))
              .findFirst()
              .orElseGet(NoValue::new);
        };
      }
      case null, default -> new NotFound("Entry is not an object entry.");
    };
  }

  private PropertyDiscoveryResult inVersion(final ObjectEntryLoadResult.SingleVersion sv,
                                            final StorageEntry host,
                                            final String propQuery,
                                            final ObjectEntryLookupTable cache) {
    return host.uriProperties().stream()
        .filter(it -> propQuery.startsWith(it.propertyName()))
        .findFirst()
        .map(it -> discoverer.apply(it.uri())
            .map(e -> {
              final int propertyNameLength = it.propertyName().length();
              final int queryLength = propQuery.length();
              if (queryLength == propertyNameLength) {
                // we are looking for the referenced entry:
                return discoverProperty(e, "", cache);
              }

              // exclude the trailing dot:
              return discoverProperty(e, propQuery.substring(propertyNameLength + 1), cache);
            })
            .orElseGet(() -> new NotFound(it.uri() + " is unreachable!")))
        .orElseGet(() -> inObject(sv.objectAsMap(), host, propQuery));
  }

  private PropertyDiscoveryResult inObject(final Map<String, Object> oam,
                                           final StorageEntry host,
                                           final String propQuery) {
    final String[] es = propQuery.split("\\.");
    return inObject(oam, host, es);
  }

  private PropertyDiscoveryResult inObjectMap(final Map<String, Object> map,
                                              final StorageEntry host,
                                              final String[] es) {
    if (es == null || es.length == 0) {
      return new NoValue();
    }

    return inObject(map.get(es[0]), host, restOf(es));
  }

  private PropertyDiscoveryResult inObjectList(final List<Object> list,
                                               final StorageEntry host,
                                               final String[] es) {
    try {
      final var idx = Integer.parseInt(es[0]);
      if (idx < 0) {
        return new NotFound("Index value [ %d ] is not a valid index number.".formatted(idx));
      }

      if (idx >= list.size()) {
        return new NoValue();
      }

      return inObject(list.get(idx), host, restOf(es));
    } catch (NumberFormatException e) {
      return new NotFound(
          "Property query points to a list [ %s ], but first element is not a number!".formatted(
              String.join(".", es)));
    }
  }

  @SuppressWarnings({ "unchecked" })
  private PropertyDiscoveryResult inObject(final Object o,
                                           final StorageEntry host,
                                           final String[] es) {
    final boolean terminal = es.length == 0;
    return switch (o) {
      case null -> new NoValue();
      case List<?> l -> terminal
          ? new ListFound<>((List<Object>) l, host)
          : inObjectList((List<Object>) l, host, es);
      case Map<?, ?> m -> terminal
          ? new ComplexFound((Map<String, Object>) m, host)
          : inObjectMap((Map<String, Object>) m, host, es);
      case Boolean b -> terminal ? new BooleanFound(b, host) : earlyTermination(es, b);
      case Number n -> terminal ? new NumberFound(n, host) : earlyTermination(es, n);
      case String s -> terminal ? new StringFound(s, host) : earlyTermination(es, s);
      // FIXME: maybe we need more sophisticated type recognition?
      default -> terminal ? new StringFound(String.valueOf(o), host) : earlyTermination(es, o);
    };
  }

  private static NotFound earlyTermination(final String[] es, final Object val) {
    return new NotFound(
        "Property query specified had remaining elements [ %s ] but terminated early on [ key: %s ][ value: %s ]".formatted(
            String.join(".", es),
            es[0],
            val));
  }

  private static <E> E[] restOf(final E[] es) {
    if (es == null || es.length == 0) {
      throw new IllegalArgumentException("Elements cannot be null or empty!");
    }

    return Arrays.copyOfRange(es, 1, es.length);
  }


  public sealed interface PropertyDiscoveryResult {}


  public sealed interface None extends PropertyDiscoveryResult {}


  public record NoValue() implements None {}


  public record NotFound(String errMsg) implements None {}


  public sealed interface Some extends PropertyDiscoveryResult {}


  public sealed interface PrimitiveDiscoveryResult extends Some {}


  public record StringFound(String string, StorageEntry host) implements PrimitiveDiscoveryResult {}


  public record NumberFound(Number number, StorageEntry host) implements PrimitiveDiscoveryResult {}


  public record BooleanFound(boolean bool, StorageEntry host) implements PrimitiveDiscoveryResult {}


  public record ComplexFound(Map<String, Object> value, StorageEntry host) implements Some {}


  public record ListFound<E>(List<E> value, StorageEntry host) implements Some {}


}

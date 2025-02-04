package com.aestallon.storageexplorer.core.service;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Stream;
import com.aestallon.storageexplorer.common.util.Pair;
import com.aestallon.storageexplorer.core.model.entry.ObjectEntry;
import com.aestallon.storageexplorer.core.model.entry.StorageEntry;
import com.aestallon.storageexplorer.core.model.entry.UriProperty;
import com.aestallon.storageexplorer.core.model.loading.ObjectEntryLoadResult;
import static com.aestallon.storageexplorer.common.util.Streams.reverse;
import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

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

  public PropertyDiscoveryResult discoverProperty(final PropertyDiscoveryResult medial,
                                                  final String propQuery,
                                                  final ObjectEntryLookupTable cache) {
    return switch (medial) {
      case None none -> switch (none) {
        case NoValue noValue when propQuery.isEmpty() -> new NoValue();
        default -> new NotFound("No value present to continue on " + propQuery);
      };
      case Some some -> (propQuery.isEmpty())
          ? medial
          : discoverProperty(some.host(), UriProperty.join(some.path(), propQuery), cache);
    };
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
    /*
     * How propQueries work and how to interpret them
     *
     * PropQueries consist of arbitrary strings joined by a `.` (dot) character, like `a`, `a.b.c`.
     * It may contain indexing segments, such as `a.5.b` which means "the property `b` on the sixth
     * element of the root's list-like property `a`".
     *
     * First we try to match a prefix of the query against any known UriProperties of the host: if
     * we match, we can drop the prefix and restart the process on the referenced entry (the new
     * host). Thus:
     * a.b.c matches -> there is a new entry on the prefix
     * a.5.c matches -> there is a match on either a.5 or a.5.c
     *
     * Then, we try to match entire list references. Suppose a.bList denotes a list-like set of
     * UriProperties on root. Then a.bList.0.c would potentially match a.bList.0 with the above
     * method, but a.bList.c does not. The query a.bList.c is valid: it means to flatten all
     * elements of a.bList.* then pick property c on each of them. In this case we are looking for
     * a.bList.*, then b. Another possibility is that bList is not a reference list, but a.bList.*.c
     * is a reference. Thus, we try to match UriProperties from longest to shortest with omitting
     * index elements:
     * a.b.c matches -> a.*.b.*.c
     * a.b.5 matches -> a.*.b.5
     * a.5.c matches -> a.5.c.*
     *
     */
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
        .or(() -> {
          final PropQuery q = new PropQuery(propQuery);
          final List<UriProperty> matchedListProps = host.uriProperties().stream()
              .filter(it -> !it.isStandalone())
              .filter(q::matches)
              .toList();
          if (matchedListProps.isEmpty()) {
            return Optional.empty();
          }

          return matchedListProps.stream()
              .map(it -> Pair.of(it, discoverer.apply(it.uri())))
              .flatMap(Pair.streamOnB())
              .map(p -> discoverProperty(p.b(), q.drop(p.a()), cache))
              .collect(collectingAndThen(toList(), rs -> Optional.of(ListFound.of(rs, ""))));
        })
        .orElseGet(() -> new InlinePropertyDiscoverer(host, propQuery).discover(sv.objectAsMap()));
  }

  private static final class InlinePropertyDiscoverer {

    private final StorageEntry host;
    private final String[] pathElements;

    private InlinePropertyDiscoverer(final StorageEntry host, final String propQuery) {
      this.host = host;
      this.pathElements = propQuery.isEmpty() ? new String[0] : propQuery.split("\\.");
    }

    private PropertyDiscoveryResult discover(final Object root) {
      final List<UriProperty.Segment> segments = new ArrayList<>();
      return inObject(root, pathElements, segments);
    }

    @SuppressWarnings({ "unchecked" })
    private PropertyDiscoveryResult inObject(final Object o,
                                             final String[] es,
                                             final List<UriProperty.Segment> segments) {
      final boolean terminal = es.length == 0;
      return switch (o) {
        case null -> new NoValue();
        case List<?> l -> terminal
            ? ListFound.of(l, host, UriProperty.Segment.asString(segments))
            : inObjectList((List<Object>) l, es, segments);
        case Map<?, ?> m -> terminal
            ? new ComplexFound((Map<String, Object>) m, host,
            UriProperty.Segment.asString(segments))
            : inObjectMap((Map<String, Object>) m, es, segments);
        case Boolean b -> terminal
            ? new BooleanFound(b, host, UriProperty.Segment.asString(segments))
            : earlyTermination(es, b);
        case Number n -> terminal
            ? new NumberFound(n, host, UriProperty.Segment.asString(segments))
            : earlyTermination(es, n);
        case String s -> terminal
            ? new StringFound(s, host, UriProperty.Segment.asString(segments))
            : earlyTermination(es, s);
        // FIXME: maybe we need more sophisticated type recognition?
        default -> terminal
            ? new StringFound(String.valueOf(o), host, UriProperty.Segment.asString(segments))
            : earlyTermination(es, o);
      };
    }

    private PropertyDiscoveryResult inObjectMap(final Map<String, Object> map,
                                                final String[] es,
                                                final List<UriProperty.Segment> segments) {
      if (es == null || es.length == 0) {
        return new NoValue();
      }

      segments.add(new UriProperty.Segment.Key(es[0]));
      return inObject(map.get(es[0]), restOf(es), segments);
    }

    private PropertyDiscoveryResult inObjectList(final List<Object> list,
                                                 final String[] es,
                                                 final List<UriProperty.Segment> segments) {
      try {
        final var idx = Integer.parseInt(es[0]);
        if (idx < 0) {
          return new NotFound("Index value [ %d ] is not a valid index number.".formatted(idx));
        }

        if (idx >= list.size()) {
          return new NoValue();
        }

        segments.add(new UriProperty.Segment.Idx(idx));
        return inObject(list.get(idx), restOf(es), segments);
      } catch (NumberFormatException e) {
        // not a numeric, we multicast:
        final List<PropertyDiscoveryResult> ret = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
          final Object o = list.get(i);
          final var segmentsI = new ArrayList<>(segments);
          segmentsI.add(new UriProperty.Segment.Idx(i));
          final PropertyDiscoveryResult res = inObject(o, es /* !!! */, segmentsI);
          ret.add(res);
        }
        return ListFound.of(ret, UriProperty.Segment.asString(segments));
      }
    }

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


  public record NoValue() implements None {

    @Override
    public String toString() {
      return "<<<NO VALUE>>>";
    }

  }


  public record NotFound(String errMsg) implements None {

    @Override
    public String toString() {
      return "<<<Error retrieving: %s>>>".formatted(errMsg);
    }

  }


  public sealed interface Some extends PropertyDiscoveryResult {

    StorageEntry host();
    
    Object val();

    String path();

  }


  public sealed interface PrimitiveDiscoveryResult extends Some {}


  public record StringFound(String string, StorageEntry host, String path)
      implements PrimitiveDiscoveryResult {

    @Override
    public String toString() {
      return "\"" + string + "\"";
    }

    @Override
    public Object val() {
      return string;
    }
  }


  public record NumberFound(Number number, StorageEntry host, String path)
      implements PrimitiveDiscoveryResult {

    @Override
    public Object val() {
      return number;
    }

    @Override
    public String toString() {
      return String.valueOf(number);
    }

  }


  public record BooleanFound(boolean bool, StorageEntry host, String path)
      implements PrimitiveDiscoveryResult {

    @Override
    public String toString() {
      return String.valueOf(bool);
    }

    @Override
    public Object val() {
      return bool;
    }
  }


  public record ComplexFound(Map<String, Object> value, StorageEntry host, String path)
      implements Some {

    @Override
    public String toString() {
      return String.valueOf(value);
    }

    @Override
    public Object val() {
      return value;
    }
  }


  public record ListFound(List<PropertyDiscoveryResult> value, StorageEntry host, boolean terminal,
      String path)
      implements Some {

    static ListFound of(final List<?> list, final StorageEntry host, String path) {
      final List<PropertyDiscoveryResult> ret = new ArrayList<>(list.size());
      for (int i = 0; i < list.size(); i++) {
        Object o = list.get(i);
        final String ePath = path + "." + i;
        final var e = switch (o) {
          case null -> new NoValue();
          case List<?> l -> ListFound.of(l, host, ePath);
          case Map<?, ?> m -> new ComplexFound((Map<String, Object>) m, host, ePath);
          case Boolean b -> new BooleanFound(b, host, ePath);
          case Number n -> new NumberFound(n, host, ePath);
          case String s -> new StringFound(s, host, ePath);
          default -> new StringFound(String.valueOf(o), host, ePath);
        };
        ret.add(e);
      }

      return new ListFound(ret, host, true, path);
    }

    static ListFound of(final List<PropertyDiscoveryResult> props, String path) {
      return props.stream()
          .flatMap(it -> switch (it) {
            case ListFound list -> list.flatten().stream();
            default -> Stream.of(it);
          })
          .collect(collectingAndThen(toList(), rs -> new ListFound(rs, null, false, path)));
    }

    private List<PropertyDiscoveryResult> flatten() {
      if (terminal) {
        return value();
      }

      return value().stream()
          .flatMap(it -> switch (it) {
            case ListFound list -> list.flatten().stream();
            default -> Stream.of(it);
          })
          .toList();
    }


    @Override
    public String toString() {
      return value().stream().map(String::valueOf).collect(joining(", ", "[", "]"));
    }

    @Override
    public Object val() {
      return value.stream()
          .map(it -> switch (it) {
            case None none -> null;
            case Some some -> some.val();
          })
          .toList();
    }
  }


  private static final class PropQuery {

    private final UriProperty.Segment[] segments;
    private final UriProperty.Segment.Key[] keysRequested;
    private final Map<UriProperty, Integer> matchIndices = new HashMap<>();

    PropQuery(final String str) {
      segments = UriProperty.Segment.parse(str);
      keysRequested = Arrays.stream(segments)
          .filter(UriProperty.Segment.Key.class::isInstance)
          .map(UriProperty.Segment.Key.class::cast)
          .toArray(UriProperty.Segment.Key[]::new);
    }

    String drop(final UriProperty uriProperty) {
      final int matchIdx = matchIndices.computeIfAbsent(uriProperty, k -> 0);
      if (matchIdx < 1) {
        throw new IllegalStateException(
            "Cannot drop prefix for unknown URI property: " + uriProperty);
      }

      return UriProperty.Segment.asString(segments, matchIdx);
    }

    boolean matches(final UriProperty uriProperty) {
      final UriProperty.Segment[] segmentsToMatch = uriProperty.segments();

      final UriProperty.Segment.Key[] keysToMatch = Arrays.stream(segmentsToMatch)
          .filter(UriProperty.Segment.Key.class::isInstance)
          .map(UriProperty.Segment.Key.class::cast)
          .toArray(UriProperty.Segment.Key[]::new);
      if (keysRequested.length < keysToMatch.length) {
        // query does not even reach to URI property:
        return false;
      }

      for (int i = 0; i < keysToMatch.length; i++) {
        if (!keysRequested[i].equals(keysToMatch[i])) {
          // query diverges from the path of the URI property:
          return false;
        }
      }

      int matchIdx = -1;
      UriProperty.Segment curr = segments[++matchIdx];
      LOOP:
      for (int i = 0; i < segmentsToMatch.length; i++) {
        switch (segmentsToMatch[i]) {
          case UriProperty.Segment.Idx(int idx) -> {
            switch (curr) {
              case UriProperty.Segment.Idx(int value) -> {
                if (idx == value) {
                  matchIdx++;
                  if (matchIdx == segments.length) {
                    break LOOP;
                  }
                  curr = segments[matchIdx];
                } else {
                  break LOOP;
                }
              }
              case UriProperty.Segment.Key key -> {
                continue LOOP;
              }
            }
          }
          case UriProperty.Segment.Key(String key) -> {
            switch (curr) {
              case UriProperty.Segment.Idx idx -> {
                break LOOP;
              }
              case UriProperty.Segment.Key(String value) -> {
                if (Objects.equals(key, value)) {
                  matchIdx++;
                  if (matchIdx == segments.length) {
                    break LOOP;
                  }
                  curr = segments[matchIdx];
                } else {
                  break LOOP;
                }
              }
            }
          }
        }
      }
      final boolean match = matchIdx > 0;
      if (match) {
        matchIndices.put(uriProperty, matchIdx);
      }

      return match;
    }
  }


}

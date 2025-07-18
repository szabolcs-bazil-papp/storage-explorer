package com.aestallon.storageexplorer.core.model.loading;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import static java.util.stream.Collectors.toCollection;
import java.util.stream.LongStream;
import org.smartbit4all.core.object.ObjectNode;
import com.aestallon.storageexplorer.core.util.Uris;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public sealed interface ObjectEntryLoadResult permits
    ObjectEntryLoadResult.Err,
    ObjectEntryLoadResult.SingleVersion,
    ObjectEntryLoadResult.MultiVersion {

  static ObjectEntryLoadResult.Err err(String msg) {
    return new ObjectEntryLoadResult.Err(msg);
  }

  static ObjectEntryLoadResult.SingleVersion singleVersion(final ObjectNode node,
                                                           final ObjectMapper objectMapper) {
    return new ObjectEntryLoadResult.SingleVersion.Eager(node, objectMapper);
  }

  static ObjectEntryLoadResult.SingleVersion singleVersion(
      final Supplier<SingleVersion.Eager> supplier) {
    return new ObjectEntryLoadResult.SingleVersion.Lazy(supplier);
  }

  @FunctionalInterface
  interface ExactVersionLoader {

    SingleVersion.Eager load(final URI uri, final long version);

    default Supplier<SingleVersion.Eager> asSupplier(final URI uri, final long version) {
      return () -> load(uri, version);
    }

  }

  static ObjectEntryLoadResult.MultiVersion multiVersion(final ObjectNode node,
                                                         final ExactVersionLoader loader,
                                                         final ObjectMapper objectMapper,
                                                         final long versionLimit) {
    final URI objectUri = node.getObjectUri();
    long vn = Uris.getVersion(objectUri);
    final var head = singleVersion(node, objectMapper);
    return createLazyVersions(loader, versionLimit, objectUri, vn, head);
  }

  static ObjectEntryLoadResult.MultiVersion multiVersion(
      final ObjectEntryLoadResult.SingleVersion singleVersion,
      final ExactVersionLoader loader,
      final long versionLimit) {
    final URI objectUri = singleVersion.meta().uri();
    long vn = singleVersion.meta().versionNr();
    return createLazyVersions(loader, versionLimit, objectUri, vn, singleVersion);
  }

  private static ObjectEntryLoadResult.MultiVersion createLazyVersions(
      final ExactVersionLoader loader,
      final long versionLimit,
      final URI objectUri, long vn,
      final ObjectEntryLoadResult.SingleVersion head) {
    final List<ObjectEntryLoadResult.SingleVersion> versions = (versionLimit < 2)
        ? new ArrayList<>()
        : LongStream
            .range(0, Math.min(versionLimit, vn))
            .mapToObj(i -> loader.asSupplier(objectUri, i))
            .map(SingleVersion.Lazy::new)
            .collect(toCollection(ArrayList::new));
    versions.add(head);
    return new ObjectEntryLoadResult.MultiVersion(versions);
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

      Eager(final ObjectNode node, final ObjectMapper objectMapper) {
        this(ObjectEntryMeta.of(node.getData()), node.getObjectAsMap(), objectMapper);
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

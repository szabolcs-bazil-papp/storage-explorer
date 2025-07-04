package com.aestallon.storageexplorer.core.model.loading;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.smartbit4all.core.object.ObjectApi;
import org.smartbit4all.core.object.ObjectNode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public sealed interface ObjectEntryLoadResult permits
    ObjectEntryLoadResult.Err,
    ObjectEntryLoadResult.SingleVersion,
    ObjectEntryLoadResult.MultiVersion {

  boolean isOk();

  default boolean isErr() {return !isOk();}

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

      private final Supplier<ObjectNode> nodeSupplier;
      private final ObjectMapper objectMapper;
      private ObjectNode node;

      Lazy(final URI versionUri,
           final ObjectApi objectApi,
           final ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        nodeSupplier = () -> objectApi.load(versionUri);
      }

      private void ensureNode() {
        node = nodeSupplier.get();
      }

      @Override
      public ObjectEntryMeta meta() {
        ensureNode();
        return ObjectEntryMeta.of(node.getData());
      }

      @Override
      public Map<String, Object> objectAsMap() {
        ensureNode();
        return node.getObjectAsMap();
      }

      @Override
      public String oamStr() {
        try {
          return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(objectAsMap());
        } catch (JsonProcessingException e) {
          return "ERROR SERIALIZING OBJECT-AS-MAP!";
        }
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

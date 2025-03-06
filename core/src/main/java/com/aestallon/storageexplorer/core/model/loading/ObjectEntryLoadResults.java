package com.aestallon.storageexplorer.core.model.loading;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.LongStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.smartbit4all.core.object.ObjectApi;
import org.smartbit4all.core.object.ObjectNode;
import com.aestallon.storageexplorer.common.util.Uris;
import com.fasterxml.jackson.databind.ObjectMapper;
import static java.util.stream.Collectors.toCollection;

public final class ObjectEntryLoadResults {

  private static final Logger log = LoggerFactory.getLogger(ObjectEntryLoadResults.class);

  private ObjectEntryLoadResults() {}

  public static ObjectEntryLoadResult.Err err(String msg) {
    return new ObjectEntryLoadResult.Err(msg);
  }

  public static ObjectEntryLoadResult.SingleVersion singleVersion(final ObjectNode node,
                                                                  final ObjectMapper objectMapper) {
    return new ObjectEntryLoadResult.SingleVersion.Eager(node, objectMapper);
  }

  public static ObjectEntryLoadResult.SingleVersion singleVersion(final URI versionedUri,
                                                                  final ObjectApi objectApi,
                                                                  final ObjectMapper objectMapper) {
    return new ObjectEntryLoadResult.SingleVersion.Lazy(versionedUri, objectApi, objectMapper);
  }

  public static ObjectEntryLoadResult.MultiVersion multiVersion(final ObjectNode node,
                                                                final ObjectApi objectApi,
                                                                final ObjectMapper objectMapper,
                                                                final long versionLimit) {
    final URI objectUri = node.getObjectUri();
    long vn = Uris.getVersion(objectUri);
    final var head = singleVersion(node, objectMapper);
    final List<ObjectEntryLoadResult.SingleVersion> versions = (versionLimit < 2)
        ? new ArrayList<>()
        : LongStream
            .range(0, Math.min(versionLimit, vn))
            .mapToObj(i -> Uris.atVersion(objectUri, i))
            .map(it -> new ObjectEntryLoadResult.SingleVersion.Lazy(it, objectApi, objectMapper))
            .collect(toCollection(ArrayList::new));
    versions.add(head);
    return new ObjectEntryLoadResult.MultiVersion(versions);
  }

}

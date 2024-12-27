package hu.aestallon.storageexplorer.core.model.loading;

import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.smartbit4all.core.object.ObjectHistoryIterator;
import org.smartbit4all.core.object.ObjectNode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class ObjectEntryLoadResults {

  private static final Logger log = LoggerFactory.getLogger(ObjectEntryLoadResults.class);

  private ObjectEntryLoadResults() {}

  public static ObjectEntryLoadResult.Err err(String msg) {
    return new ObjectEntryLoadResult.Err(msg);
  }

  public static ObjectEntryLoadResult.SingleVersion singleVersion(final ObjectNode node,
                                                                  final ObjectMapper objectMapper) {
    final var objectAsMap = node.getObjectAsMap();
    String oasStr = "Error processing Object-as-Map into text!";
    try {
      oasStr = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(objectAsMap);
    } catch (JsonProcessingException e) {
      log.error(e.getMessage(), e);
    }
    return new ObjectEntryLoadResult.SingleVersion(
        ObjectEntryMeta.of(node.getData()),
        objectAsMap,
        oasStr);
  }

  public static ObjectEntryLoadResult.MultiVersion multiVersion(
      final ObjectHistoryIterator historyIterator,
      final ObjectMapper objectMapper) {
    final List<ObjectEntryLoadResult.SingleVersion> versions = new ArrayList<>();
    while (historyIterator.hasNext()) {
      final var node = historyIterator.next();
      versions.add(singleVersion(node, objectMapper));
    }
    return new ObjectEntryLoadResult.MultiVersion(versions);
  }

}

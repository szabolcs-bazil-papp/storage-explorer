package hu.aestallon.storageexplorer.storage.model.loading;

import java.net.URI;
import java.time.OffsetDateTime;
import org.smartbit4all.api.object.bean.ObjectNodeData;

public record ObjectEntryMeta(
    URI uri,
    String qualifiedName,
    String storageSchema,
    Long versionNr,
    OffsetDateTime createdAt,
    Long lastModified) {

  public static ObjectEntryMeta of(final ObjectNodeData objectNodeData) {
    return new ObjectEntryMeta(
        objectNodeData.getObjectUri(),
        objectNodeData.getQualifiedName(),
        objectNodeData.getStorageSchema(),
        objectNodeData.getVersionNr(),
        objectNodeData.getCreatedAt(),
        objectNodeData.getLastModified());
  }

}

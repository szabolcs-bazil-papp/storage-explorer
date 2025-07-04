package com.aestallon.storageexplorer.core.model.entry;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.smartbit4all.api.collection.CollectionApi;
import org.smartbit4all.core.object.ObjectApi;
import com.aestallon.storageexplorer.common.util.Uris;
import com.aestallon.storageexplorer.core.model.instance.dto.StorageId;
import com.aestallon.storageexplorer.core.service.StorageIndex;
import com.google.common.base.Strings;

public final class StorageEntryFactory {

  private static final Logger log = LoggerFactory.getLogger(StorageEntryFactory.class);

  public static final String STORED_LIST_MARKER = "/storedlist";
  public static final String STORED_MAP_MARKER = "/storedmap";
  public static final String STORED_REF_MARKER = "/storedRef";
  public static final String STORED_SEQ_MARKER = "/storedSeq";


  public static Builder builder(final StorageIndex<?> storageIndex,
                                final ObjectApi objectApi,
                                final CollectionApi collectionApi) {
    return new Builder(storageIndex, objectApi, collectionApi);
  }

  private static Optional<URI> scopeUri(final String uriString, final String probe) {
    if (Strings.isNullOrEmpty(uriString)) {
      return Optional.empty();
    }
    return scopeEndsAt(uriString, probe).stream()
        .mapToObj(i -> uriString.substring(0, i))
        .findFirst()
        .flatMap(Uris::parse);
  }

  private static OptionalInt scopeEndsAt(final String uriString, final String probe) {
    final int idx = uriString.indexOf(probe);
    return (idx > 0) ? OptionalInt.of(idx) : OptionalInt.empty();
  }

  private final StorageIndex<?> storageIndex;
  private final StorageId id;
  private final ObjectApi objectApi;
  private final CollectionApi collectionApi;
  private final Path pathToStorage;

  private StorageEntryFactory(final Builder builder) {
    this.storageIndex = builder.storageIndex;
    id = storageIndex.id();
    this.objectApi = builder.objectApi;
    this.collectionApi = builder.collectionApi;
    this.pathToStorage = builder.pathToStorage;
  }

  public Optional<? extends StorageEntry> create(final URI uri) {
    final URI latestUri = objectApi.getLatestUri(uri);
    final Path relativePath = Paths.get(latestUri.getScheme(), latestUri.getPath() + ".o");
    final Path path = (pathToStorage == null) ? null : pathToStorage.resolve(relativePath);
    final String uriString = latestUri.toString();
    try {
      if (uriString.contains(STORED_LIST_MARKER)) {
        return scopeUri(uriString, STORED_LIST_MARKER)
            .map(
                scope -> (ListEntry) new ScopedListEntry(id, path, latestUri, collectionApi, scope))
            .or(() -> Optional.of(new ListEntry(id, path, latestUri, collectionApi)));

      } else if (uriString.contains(STORED_MAP_MARKER)) {
        return scopeUri(uriString, STORED_MAP_MARKER)
            .map(scope -> (MapEntry) new ScopedMapEntry(id, path, latestUri, collectionApi, scope))
            .or(() -> Optional.of(new MapEntry(id, path, latestUri, collectionApi)));

      } else if (uriString.contains(STORED_REF_MARKER)) {
        return scopeUri(uriString, STORED_REF_MARKER)
            .map(scope -> new ScopedObjectEntry(storageIndex, path, latestUri, scope));

      } else if (uriString.contains(STORED_SEQ_MARKER)) {
        return Optional.of(new SequenceEntry(id, path, latestUri, collectionApi));

      } else {
        return Optional.of(new ObjectEntry(storageIndex, path, latestUri));

      }

    } catch (Exception e) {
      log.error("Cannot initialise StorageEntry [ {} ]: [ {} ]", latestUri, e.getMessage());
      log.debug(e.getMessage(), e);
    }

    log.warn("Cannot yet deal with URI TYPE of [ {} ]", latestUri);
    return Optional.empty();
  }


  public static final class Builder {
    private final StorageIndex<?> storageIndex;
    private final ObjectApi objectApi;
    private final CollectionApi collectionApi;

    private Path pathToStorage;

    private Builder(StorageIndex<?> storageIndex, ObjectApi objectApi, CollectionApi collectionApi) {
      this.storageIndex = Objects.requireNonNull(storageIndex, "StorageId must not be null!");
      this.objectApi = Objects.requireNonNull(objectApi, "ObjectApi must not be null!");
      this.collectionApi = Objects.requireNonNull(collectionApi, "CollectionApi must not be null!");
    }

    public Builder pathToStorage(final Path pathToStorage) {
      this.pathToStorage = pathToStorage;
      return this;
    }

    public StorageEntryFactory build() {
      return new StorageEntryFactory(this);
    }
  }


}

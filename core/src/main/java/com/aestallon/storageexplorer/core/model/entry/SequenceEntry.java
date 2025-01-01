package com.aestallon.storageexplorer.core.model.entry;

import java.net.URI;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.smartbit4all.api.collection.CollectionApi;
import org.smartbit4all.api.collection.StoredSequence;
import org.smartbit4all.core.utility.StringConstant;
import com.aestallon.storageexplorer.core.model.instance.dto.StorageId;

public final class SequenceEntry implements StorageEntry {

  private static final Logger log = LoggerFactory.getLogger(SequenceEntry.class);

  private final StorageId id;
  private final Path path;
  private final URI uri;
  private final CollectionApi collectionApi;
  private final String schema;
  private final String name;

  private boolean valid = false;
  private long current = -1L;

  SequenceEntry(StorageId id, Path path, URI uri, CollectionApi collectionApi) {
    this.id = id;
    this.path = path;
    this.uri = uri;
    this.collectionApi = collectionApi;

    final String fullScheme = uri.getScheme();
    this.schema = fullScheme.substring(0, fullScheme.lastIndexOf('-'));

    final String uriPath = uri.getPath();
    final String marker = "storedSeq/";
    final int markerIdx = uriPath.indexOf(marker);
    if (markerIdx != -1) {
      this.name = uriPath.substring(markerIdx + marker.length(), uriPath.length() - 2);
    } else {
      this.name = StringConstant.EMPTY;
    }
  }

  @Override
  public StorageId storageId() {
    return id;
  }

  @Override
  public URI uri() {
    return uri;
  }

  public String schema() {
    return schema;
  }

  public String name() {
    return name;
  }

  @Override
  public Set<UriProperty> uriProperties() {
    return Collections.emptySet();
  }

  public long current() {
    if (!valid) {
      refresh();
    }

    return current;
  }

  @Override
  public void refresh() {
    if (name.isEmpty()) {
      log.warn("Cannot refresh sequence '{}' (sequence name is not valid)", uri);
      return;
    }

    final StoredSequence sequence = collectionApi.sequence(schema, name);
    final Long currentBoxed = sequence.current();
    current = (currentBoxed != null) ? currentBoxed : -1L;
    valid = true;
  }

  @Override
  public boolean valid() {
    return valid;
  }

  public String displayName() {
    return schema + " / " + name;
  }

  @Override
  public String toString() {
    return "SEQ (" + schema + "/" + name + ")";
  }
}

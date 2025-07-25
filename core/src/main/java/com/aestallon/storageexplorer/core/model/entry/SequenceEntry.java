package com.aestallon.storageexplorer.core.model.entry;

import java.net.URI;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.smartbit4all.api.collection.CollectionApi;
import org.smartbit4all.api.collection.StoredSequence;
import org.smartbit4all.core.utility.StringConstant;
import com.aestallon.storageexplorer.core.model.instance.dto.StorageId;
import com.aestallon.storageexplorer.core.util.Uris;

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

  @Override
  public Path path() {
    return path;
  }

  public String schema() {
    return schema;
  }

  public String name() {
    return name;
  }

  @Override
  public void accept(StorageEntry storageEntry) {
    if (Objects.requireNonNull(storageEntry) instanceof SequenceEntry that && that.valid) {
      current = that.current;
      valid = true;
    }
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
    try {
      final Long currentBoxed = sequence.current();
      current = (currentBoxed != null) ? currentBoxed : -1L;
    } catch (Exception e) {
      log.error(e.getMessage(), e);
      current = -1L;
    }
    
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
  public void setUriProperties(Set<UriProperty> uriProperties) {
    // NO OP
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    SequenceEntry that = (SequenceEntry) o;
    return Uris.equalIgnoringVersion(uri, that.uri);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(uri);
  }

  @Override
  public String toString() {
    return "SEQ (" + schema + "/" + name + ")";
  }
}

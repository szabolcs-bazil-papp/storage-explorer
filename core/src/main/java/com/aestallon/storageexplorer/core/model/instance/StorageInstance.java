package com.aestallon.storageexplorer.core.model.instance;

import java.net.URI;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.smartbit4all.core.utility.StringConstant;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.util.Assert;
import org.w3c.dom.events.EventTarget;
import com.aestallon.storageexplorer.core.event.EntryAcquired;
import com.aestallon.storageexplorer.core.event.EntryAcquisitionFailed;
import com.aestallon.storageexplorer.core.event.EntryDiscovered;
import com.aestallon.storageexplorer.core.model.entry.StorageEntry;
import com.aestallon.storageexplorer.core.model.instance.dto.Availability;
import com.aestallon.storageexplorer.core.model.instance.dto.FsStorageLocation;
import com.aestallon.storageexplorer.core.model.instance.dto.IndexingStrategyType;
import com.aestallon.storageexplorer.core.model.instance.dto.SqlStorageLocation;
import com.aestallon.storageexplorer.core.model.instance.dto.StorageId;
import com.aestallon.storageexplorer.core.model.instance.dto.StorageInstanceDto;
import com.aestallon.storageexplorer.core.model.instance.dto.StorageInstanceType;
import com.aestallon.storageexplorer.core.model.instance.dto.StorageLocation;
import com.aestallon.storageexplorer.core.service.IndexingStrategy;
import com.aestallon.storageexplorer.core.service.StorageIndex;
import com.aestallon.storageexplorer.core.service.StorageInstanceExaminer;

public final class StorageInstance {

  private static final Logger log = LoggerFactory.getLogger(StorageInstance.class);

  public static StorageInstance fromDto(final StorageInstanceDto dto) {
    Assert.notNull(dto, "StorageInstanceDto must not be null!");
    return new StorageInstance(new StorageId(dto.getId())).applyDto(dto);
  }

  private final StorageId id;
  private String name;
  private Availability availability;
  private StorageLocation location;
  private IndexingStrategy indexingStrategy;

  private StorageIndex index;
  private ApplicationEventPublisher eventPublisher;

  private StorageInstance(final StorageId id) {
    this.id = Objects.requireNonNull(id, "Storage Instance ID cannot be null!");
    availability = Availability.UNAVAILABLE;
  }

  private <EVENT> void publishEvent(final EVENT e) {
    if (eventPublisher != null) {
      eventPublisher.publishEvent(e);
    }
  }

  public StorageId id() {
    return id;
  }

  public String name() {
    return name;
  }

  public void setName(String name) {
    this.name = (name == null) ? StringConstant.EMPTY : name;
  }

  public Availability availability() {
    return availability;
  }

  public void setAvailability(
      Availability availability) {
    this.availability = (availability == null) ? Availability.UNAVAILABLE : availability;
  }

  public StorageLocation location() {
    return location;
  }

  public void setLocation(
      StorageLocation location) {
    Assert.notNull(location, "StorageLocation must not be null!");
    this.location = location;
  }

  public StorageInstanceType type() {
    return location instanceof FsStorageLocation ? StorageInstanceType.FS : StorageInstanceType.DB;
  }

  public IndexingStrategyType indexingStrategy() {
    return indexingStrategy.type();
  }

  public void setIndexingStrategy(IndexingStrategyType type) {
    this.indexingStrategy = IndexingStrategy.of(type);
  }

  public StorageIndex index() {
    return index;
  }

  public void setIndex(final StorageIndex index) {
    this.index = index;
  }

  public void refreshIndex() {
    if (index == null || indexingStrategy == null) {
      log.warn("Index or Strategy is not available for instance: {}", this);
      return;
    }

    index.refresh(indexingStrategy);
  }

  public void validate(final Collection<? extends StorageEntry> entries) {
    index.revalidate(entries);
  }

  /**
   * User code should call this.
   *
   * <p>
   * For programmatic acquisition, use {@link #discover(URI)}.
   *
   * @param uri the {@code URI} of the entry to acquire
   *
   * @return an {@link Optional} containing the entry if it was reachable, an empty {@code Optional}
   *     otherwise
   */
  public Optional<StorageEntry> acquire(final URI uri) {
    final StorageIndex.EntryAcquisitionResult result = index.getOrCreate(uri);
    return switch (result.kind()) {
      case FAIL -> {
        publishEvent(new EntryAcquisitionFailed(this, uri));
        yield Optional.empty();
      }
      case PRESENT -> Optional.of(result.entry());
      case NEW -> {
        final var entry = result.entry();
        try {
          // we must refresh the requested entry right away:
          // - the programme requested acquisition for future load anyway
          // - there is literally no other way to validate if a given URI is present in a Storage
          //   apart from attempting to load it...
          entry.refresh();
        } catch (final Exception e) {
          log.error(e.getMessage(), e);
          publishEvent(new EntryAcquisitionFailed(this, uri));
          yield Optional.empty();
        }

        index.accept(uri, entry);
        // >>> IMPORTANT! <<<
        // Sadly we can't do the following, for a ScopedEntry's scope is not actually the host node
        // (the schema belongs to the ScopedEntry itself...). Thus, we may do something else: notify 
        // both the StorageIndex and the broader universe (the UI) that a ScopedEntry has been 
        // discovered/acquired! But, we do not need to actually carry that out: ScopedEntries cannot
        // be discovered automatically, only acquired through user action (because no sane Storage
        // structure would persist a scoped entry's URI in an actual entry (!!)) -> we can only
        // discover scoped entries through acquisition, and that path has been taken care of.
        // 
        // Sadly, as a side effect, the user must supply BOTH the host and scoped URIs for the 
        // scoped entry to actually show up in the application, but that is a rendering issue, our
        // job here is done.
        //        if (entry instanceof ScopedEntry) {
        //          // Scoped entries are useless without their host, we plainly discover that:
        //          final var scopedEntry = (ScopedEntry) entry;
        //          final var host =
        //              discover(scopedEntry.scope()); // --- then this discovery binds to the host, for we added it to the index earlier
        //          if (host.isEmpty()) {
        //            // no point acquiring at this point sadly...
        //            eventPublisher.publishEvent(new ViewController.EntryAcquisitionFailed(this, uri));
        //            return Optional.empty();
        //          }
        //        }
        publishEvent(new EntryAcquired(this, entry));
        yield Optional.of(entry);
      }
    };
  }

  public Optional<StorageEntry> discover(final URI uri) {
    final StorageIndex.EntryAcquisitionResult result = index.getOrCreate(uri);
    return switch (result.kind()) {
      case FAIL -> {
        publishEvent(new EntryAcquisitionFailed(this, uri));
        yield Optional.empty();
      }
      case PRESENT -> Optional.of(result.entry());
      case NEW -> {
        final var entry = result.entry();
        // discovery is expected to be programmatic -> we trust the URI is valid.
        index.accept(uri, entry);
        publishEvent(new EntryDiscovered(this, entry));
        yield Optional.of(entry);
      }
    };
  }

  public Stream<StorageEntry> entities() {
    return index == null ? Stream.empty() : index.entities();
  }

  public StorageInstance applyDto(final StorageInstanceDto dto) {
    Assert.notNull(dto, "StorageInstanceDto must not be null!");

    setName(dto.getName());
    setAvailability(dto.getAvailability());
    switch (dto.getType()) {
      case FS -> setLocation(dto.getFs());
      case DB -> setLocation(dto.getDb());
      default -> throw new IllegalArgumentException("Unsupported storage type: " + dto.getType());
    }
    setIndexingStrategy(dto.getIndexingStrategy());

    return this;
  }

  public StorageInstanceDto toDto() {
    return new StorageInstanceDto()
        .id(id.uuid())
        .name(name)
        .availability(availability)
        .indexingStrategy(indexingStrategy.type())
        .type(location instanceof FsStorageLocation
            ? StorageInstanceType.FS
            : StorageInstanceType.DB)
        .fs(location instanceof FsStorageLocation ? (FsStorageLocation) location : null)
        .db(location instanceof SqlStorageLocation ? (SqlStorageLocation) location : null);
  }

  public void setEventPublisher(final ApplicationEventPublisher eventPublisher) {
    this.eventPublisher = eventPublisher;
  }
  
  public StorageInstanceExaminer examiner() {
    return new StorageInstanceExaminer(this::discover);
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass())
      return false;
    StorageInstance that = (StorageInstance) o;
    return Objects.equals(id, that.id);
  }

  @Override
  public int hashCode() {
    return id.hashCode();
  }

  @Override
  public String toString() {
    return toDto().toString();
  }

}

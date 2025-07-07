/*
 * Copyright (C) 2025 Szabolcs Bazil Papp
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along with this program.
 * If not, see <http://www.gnu.org/licenses/>.
 */

package com.aestallon.storageexplorer.client.userconfig.service;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import com.aestallon.storageexplorer.client.storage.StorageInstanceProvider;
import com.aestallon.storageexplorer.client.userconfig.model.FavouriteStorageEntry;
import com.aestallon.storageexplorer.client.userconfig.model.TrackedInspector;
import com.aestallon.storageexplorer.core.model.entry.StorageEntry;
import com.aestallon.storageexplorer.core.model.instance.dto.StorageId;
import com.fasterxml.jackson.core.type.TypeReference;

@Service
public class StorageEntryTrackingService {

  private static final Logger log = LoggerFactory.getLogger(StorageEntryTrackingService.class);


  public record StorageEntryUserData(String name, String description) {}


  private static final String FAVOURITE_STORAGE_ENTRIES = "favourite.storage.entries";
  private static final String TRACKED_INSPECTORS = "tracked.inspectors";

  private final UserConfigPersistenceService persistenceService;
  private final ApplicationEventPublisher eventPublisher;
  private final StorageInstanceProvider storageInstanceProvider;

  private final AtomicReference<Map<URI, FavouriteStorageEntry>> favouriteStorageEntries;
  private final AtomicReference<List<TrackedInspector>> trackedInspectors;
  private final Lock trackLock = new ReentrantLock(true);

  public StorageEntryTrackingService(UserConfigPersistenceService persistenceService,
                                     ApplicationEventPublisher eventPublisher,
                                     StorageInstanceProvider storageInstanceProvider) {
    this.persistenceService = persistenceService;
    this.eventPublisher = eventPublisher;
    this.storageInstanceProvider = storageInstanceProvider;

    favouriteStorageEntries = new AtomicReference<>(persistenceService.readSettingsAt(
        FAVOURITE_STORAGE_ENTRIES,
        new TypeReference<>() {},
        HashMap::new));
    trackedInspectors = new AtomicReference<>(persistenceService.readSettingsAt(
        TRACKED_INSPECTORS,
        new TypeReference<>() {},
        ArrayList::new));
  }

  public List<TrackedInspector> trackedInspectors() {
    return Collections.unmodifiableList(trackedInspectors.get());
  }

  public List<StorageEntry> entriesOfTrackedInspectors() {
    final var toRemove = new ArrayList<TrackedInspector>();
    final var es = new ArrayList<>(trackedInspectors());
    final var result = es.stream()
        .flatMap(it -> {
          final var storageInstance = storageInstanceProvider.get(new StorageId(it.getStorageId()));
          if (storageInstance == null) {
            log.warn("Could not find storage instance for tracked inspector [ {} ]!", it);
            toRemove.add(it);
            return Stream.empty();
          }

          return storageInstance.discover(it.getUri()).stream();
        })
        .toList();
    if (!toRemove.isEmpty()) {
      es.removeAll(toRemove);
      updateTrackedInspectors(es);
    }

    return result;
  }

  public void addTrackedInspector(final StorageEntry storageEntry) {
    trackLock.lock();
    try {
      final var trackedInspectors = trackedInspectors();
      if (trackedInspectors.stream()
          .anyMatch(it -> Objects.equals(it.getUri(), storageEntry.uri()))) {
        return;
      }

      final var newTrackedInspector = new TrackedInspector();
      newTrackedInspector.setUri(storageEntry.uri());
      newTrackedInspector.setStorageId(storageEntry.storageId().uuid());

      final var updated = new ArrayList<>(trackedInspectors);
      updated.add(newTrackedInspector);
      updateTrackedInspectors(updated);
    } finally {
      trackLock.unlock();
    }
  }

  public void removeTrackedInspector(final StorageEntry storageEntry) {
    trackLock.lock();
    try {
      final var trackedInspectors = new ArrayList<>(trackedInspectors());
      final var toRemove = trackedInspectors.stream()
          .filter(it -> Objects.equals(it.getUri(), storageEntry.uri()))
          .toList();
      trackedInspectors.removeAll(toRemove);
      updateTrackedInspectors(trackedInspectors);
    } finally {
      trackLock.unlock();
    }
  }

  private void updateTrackedInspectors(List<TrackedInspector> trackedInspectors) {
    final var baseline = trackedInspectors();
    if (baseline.equals(trackedInspectors)) {
      return;
    }

    this.trackedInspectors.set(new ArrayList<>(trackedInspectors));
    persistenceService.writeSettingsTo(TRACKED_INSPECTORS, trackedInspectors);
  }

  public Optional<StorageEntryUserData> getUserData(final StorageEntry storageEntry) {
    Objects.requireNonNull(storageEntry, "storageEntry cannot be null!");

    return Optional
        .ofNullable(favouriteStorageEntries.get().get(storageEntry.uri()))
        .map(it -> new StorageEntryUserData(it.getName(), it.getDescription()));
  }

  public void setStorageEntryName(final StorageEntry storageEntry, final String name) {
    setStorageEntryProperty(storageEntry, FavouriteStorageEntry::setName, name);
  }

  public void setStorageEntryDescription(final StorageEntry storageEntry,
                                         final String description) {
    setStorageEntryProperty(storageEntry, FavouriteStorageEntry::setDescription, description);
  }

  private <T> void setStorageEntryProperty(final StorageEntry storageEntry,
                                           final BiConsumer<FavouriteStorageEntry, T> mutator,
                                           final T value) {
    Objects.requireNonNull(storageEntry, "storageEntry cannot be null!");
    Objects.requireNonNull(mutator, "mutator cannot be null!");
    Objects.requireNonNull(value, "value cannot be null!");

    final var map = favouriteStorageEntries.updateAndGet(fse -> {
      final FavouriteStorageEntry it = fse.computeIfAbsent(storageEntry.uri(), k -> {
        final var entry = new FavouriteStorageEntry();
        entry.setUri(k);
        entry.setStorageId(storageEntry.storageId().uuid());
        entry.setName("");
        entry.setDescription("");
        return entry;
      });
      mutator.accept(it, value);
      return fse;
    });
    persistenceService.writeSettingsTo(FAVOURITE_STORAGE_ENTRIES, map);
  }



}

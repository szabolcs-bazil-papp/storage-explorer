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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import com.aestallon.storageexplorer.client.storage.StorageInstanceProvider;
import com.aestallon.storageexplorer.client.userconfig.event.StorageEntryUserDataChanged;
import com.aestallon.storageexplorer.client.userconfig.model.FavouriteStorageEntry;
import com.aestallon.storageexplorer.client.userconfig.model.TrackedEntry;
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
  private final ProblemService problemService;

  private final AtomicReference<Map<URI, FavouriteStorageEntry>> favouriteStorageEntries;
  private final AtomicReference<List<TrackedEntry>> trackedEntries;
  private final Lock trackLock = new ReentrantLock(true);

  public StorageEntryTrackingService(UserConfigPersistenceService persistenceService,
                                     ApplicationEventPublisher eventPublisher,
                                     StorageInstanceProvider storageInstanceProvider,
                                     ProblemService problemService) {
    this.persistenceService = persistenceService;
    this.eventPublisher = eventPublisher;
    this.storageInstanceProvider = storageInstanceProvider;
    this.problemService = problemService;

    favouriteStorageEntries = new AtomicReference<>(persistenceService.readSettingsAt(
        FAVOURITE_STORAGE_ENTRIES,
        new TypeReference<>() {},
        HashMap::new));
    trackedEntries = new AtomicReference<>(persistenceService.readSettingsAt(
        TRACKED_INSPECTORS,
        new TypeReference<>() {},
        ArrayList::new));
  }

  private List<TrackedEntry> trackedEntries() {
    return Collections.unmodifiableList(trackedEntries.get());
  }

  private record ShowableEntry(
      TrackedEntry trackedEntry,
      boolean showNow,
      StorageEntry storageEntry) {}

  public List<StorageEntry> entriesOfTrackedInspectors() {
    final var es = new ArrayList<>(trackedEntries());
    final var showableEntries = es.stream()
        .map(it -> {
          final var storageId = new StorageId(it.getStorageId());
          final var storageInstance = storageInstanceProvider.get(storageId);
          if (storageInstance == null) {
            if (storageInstanceProvider.isKnownStorageInstance(storageId)) {
              // if the storage is not available, but is known, we won't reopen this:
              return new ShowableEntry(it, false, null);
            }
            // if this is a now forgotten storage, we outright want to eliminate it:
            return new ShowableEntry(null, false, null);
          }

          return storageInstance.discover(it.getUri())
              // the storage is available, and the entry is found: we show this inspector, if need be:
              .map(entry -> new ShowableEntry(it, it.isUnderInspection(), entry))
              .orElseGet(() -> {
                // the storage is available, but the entry is not: (this means the URI is malformed
                // as per `discover` -> we shall hide this, and maybe it will be available
                // on restart:
                return new ShowableEntry(null, false, null);
              });
        })
        .toList();
    final List<ShowableEntry> entriesToKeep = showableEntries.stream()
        .filter(it -> it.trackedEntry() != null)
        .toList();
    final List<TrackedEntry> updated = entriesToKeep.stream()
        .map(ShowableEntry::trackedEntry)
        .toList();
    updateTrackedEntries(updated);

    return entriesToKeep.stream()
        .filter(ShowableEntry::showNow)
        .map(ShowableEntry::storageEntry)
        .toList();
  }

  public void addTrackedInspector(final StorageEntry storageEntry) {
    trackLock.lock();
    try {
      final var trackedEntries = new ArrayList<>(trackedEntries());
      final List<TrackedEntry> updated;
      boolean found = false;
      for (final var trackedEntry : trackedEntries) {
        if (Objects.equals(trackedEntry.getStorageId(), storageEntry.storageId().uuid())
            && Objects.equals(trackedEntry.getUri(), storageEntry.uri())) {

          if (trackedEntry.isUnderInspection()) {
            return;
          }
          trackedEntry.setUnderInspection(true);
          found = true;
        }
      }
      if (!found) {
        final var newTrackedEntry = new TrackedEntry();
        newTrackedEntry.setUri(storageEntry.uri());
        newTrackedEntry.setStorageId(storageEntry.storageId().uuid());
        newTrackedEntry.setUnderInspection(true);
        trackedEntries.add(newTrackedEntry);
      }

      updateTrackedEntries(trackedEntries);
    } finally {
      trackLock.unlock();
    }
  }

  public void removeTrackedInspector(final StorageEntry storageEntry, boolean forget) {
    trackLock.lock();
    try {
      final var trackedInspectors = new ArrayList<>(trackedEntries());
      final var toRemove = trackedInspectors.stream()
          .filter(it -> Objects.equals(it.getUri(), storageEntry.uri()))
          .toList();
      if (forget) {
        trackedInspectors.removeAll(toRemove);
      } else {
        toRemove.forEach(it -> it.setUnderInspection(false));
      }
      updateTrackedEntries(trackedInspectors);
    } finally {
      trackLock.unlock();
    }
  }

  private void updateTrackedEntries(List<TrackedEntry> trackedEntries) {
    final var baseline = trackedEntries();
    if (baseline.equals(trackedEntries)) {
      return;
    }

    this.trackedEntries.set(new ArrayList<>(trackedEntries));
    persistenceService.writeSettingsTo(TRACKED_INSPECTORS, trackedEntries);
  }

  public Optional<StorageEntryUserData> getUserData(final StorageEntry storageEntry) {
    Objects.requireNonNull(storageEntry, "storageEntry cannot be null!");

    return Optional
        .ofNullable(favouriteStorageEntries.get().get(storageEntry.uri()))
        .map(it -> new StorageEntryUserData(it.getName(), it.getDescription()));
  }

  public void updateStorageEntryUserData(final StorageEntry storageEntry,
                                         final StorageEntryUserData storageEntryUserData) {
    Objects.requireNonNull(storageEntry, "storageEntry cannot be null!");
    Objects.requireNonNull(storageEntryUserData, "storageEntryUserData cannot be null!");

    final var map = favouriteStorageEntries.updateAndGet(fse -> {
      final FavouriteStorageEntry it = fse.computeIfAbsent(storageEntry.uri(), k -> {
        final var entry = new FavouriteStorageEntry();
        entry.setUri(k);
        entry.setStorageId(storageEntry.storageId().uuid());
        entry.setName("");
        entry.setDescription("");
        return entry;
      });
      it.setName(storageEntryUserData.name);
      it.setDescription(storageEntryUserData.description);
      return fse;
    });
    persistenceService.writeSettingsTo(FAVOURITE_STORAGE_ENTRIES, map);
    eventPublisher.publishEvent(new StorageEntryUserDataChanged(
        storageEntry,
        storageEntryUserData));
  }

}

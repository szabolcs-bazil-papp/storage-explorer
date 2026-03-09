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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import com.aestallon.storageexplorer.client.userconfig.event.ProblemEncountered;
import com.aestallon.storageexplorer.client.userconfig.model.Problem;
import com.aestallon.storageexplorer.core.event.EntryAcquisitionFailed;
import com.fasterxml.jackson.core.type.TypeReference;

@Service
public class ProblemService {

  private static final Logger log = LoggerFactory.getLogger(ProblemService.class);

  private static final String TRACKED_PROBLEMS = "tracked.problems";

  private final UserConfigPersistenceService persistenceService;
  private final ApplicationEventPublisher eventPublisher;
  private final AtomicReference<List<Problem>> problems;
  private final Lock lock = new ReentrantLock(true);

  public ProblemService(UserConfigPersistenceService persistenceService,
                        ApplicationEventPublisher eventPublisher) {
    this.persistenceService = persistenceService;
    this.eventPublisher = eventPublisher;
    this.problems = new AtomicReference<>(persistenceService.readSettingsAt(
        TRACKED_PROBLEMS,
        new TypeReference<>() {},
        ArrayList::new));
  }

  @EventListener(EntryAcquisitionFailed.class)
  public void onEntryAcquisitionFailed(EntryAcquisitionFailed event) {
    add(Problem.ofStorageEntry(
        event.storageInstance().id(),
        event.uri(),
        "Failed to acquire entry in Storage " + event.storageInstance().name() + "."));
  }

  public List<Problem> problems() {
    return Collections.unmodifiableList(problems.get());
  }

  public void add(final Problem problem) {
    lock.lock();
    try {
      final var updated = new ArrayList<>(problems.get());
      updated.add(problem);
      updateProblems(updated);
      eventPublisher.publishEvent(new ProblemEncountered(problem));
    } finally {
      lock.unlock();
    }
  }

  private void updateProblems(List<Problem> ps) {
    problems.set(new ArrayList<>(ps));
    persistenceService.writeSettingsTo(TRACKED_PROBLEMS, ps);
  }

  public void delete(final UUID problemId) {
    if (problemId == null) {
      log.error("NULL problem ID provided for delete operation!");
      return;
    }

    lock.lock();
    try {
      final var updated = new ArrayList<>(problems.get());
      if (updated.removeIf(it -> problemId.equals(it.getId()))) {
        updateProblems(updated);
      }
    } finally {
      lock.unlock();
    }
  }

  public void clear() {
    lock.lock();
    try {
      updateProblems(new ArrayList<>());
    } finally {
      lock.unlock();
    }
  }

}

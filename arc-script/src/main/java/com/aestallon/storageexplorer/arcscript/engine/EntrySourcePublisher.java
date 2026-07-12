/*
 * Copyright (C) 2026 Szabolcs Bazil Papp
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

package com.aestallon.storageexplorer.arcscript.engine;

import java.util.Collection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.aestallon.storageexplorer.core.model.entry.StorageEntry;

/**
 * Head of the query pipeline.
 *
 * <p>
 * By the time the query engine runs, the engine-level (implicit or explicit) {@code index}
 * instruction has already fully resolved the source set, so this publisher receives a complete,
 * in-memory collection. Its sole job is to turn that bulk collection into a {@link Flow.Publisher}
 * boundary: entries are submitted one at a time from a producer thread, so downstream stages start
 * consuming the first entry without waiting for the iteration to finish. Should source acquisition
 * ever become lazy, this is the seam where a streaming supplier would plug in.
 */
final class EntrySourcePublisher {

  private static final Logger log = LoggerFactory.getLogger(EntrySourcePublisher.class);

  private final SubmissionPublisher<StorageEntry> publisher;
  private final Collection<StorageEntry> entries;
  private final ExecutorService executor;
  private final PipelineMetrics.StageMetrics metrics;

  EntrySourcePublisher(final Collection<StorageEntry> entries,
                       final ExecutorService executor,
                       final int queueCapacity,
                       final PipelineMetrics.StageMetrics metrics) {
    this.entries = entries;
    this.executor = executor;
    // route subscriber-thrown failures into the regular onError channel instead of
    // SubmissionPublisher's default silent cancellation (see PipelineStage):
    this.publisher = new SubmissionPublisher<>(
        executor,
        queueCapacity,
        (subscriber, throwable) -> subscriber.onError(throwable));
    this.metrics = metrics;
  }

  void subscribe(final Flow.Subscriber<? super StorageEntry> subscriber) {
    publisher.subscribe(subscriber);
  }

  /**
   * Begins submitting entries downstream on a producer thread. Subscribers must be attached before
   * invoking this method. The producer stops as soon as every subscriber has cancelled (early
   * termination) or the source set is exhausted.
   */
  void start() {
    executor.submit(() -> {
      try {

        for (final StorageEntry entry : entries) {
          if (publisher.isClosed() || !publisher.hasSubscribers()) {
            log.debug("All subscribers are gone - halting source iteration.");
            metrics.earlyTerminated();
            break;
          }
          publisher.submit(entry);
          metrics.out();
        }

      } catch (final Throwable e) {
        log.error(e.getMessage(), e);
        publisher.closeExceptionally(e);
        if (e instanceof Error error) {
          throw error;
        }
        return;
      } finally {
        metrics.completed();
      }
      publisher.close();
    });
  }

}

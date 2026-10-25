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

import java.util.Iterator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.aestallon.storageexplorer.core.model.entry.StorageEntry;

/**
 * Head of the query pipeline.
 *
 * <p>
 * Bridges the lazily populated source stream (see
 * {@code StorageIndex.find} / {@link QuerySourceResolver}) into the {@link Flow.Publisher}
 * boundary: entries are submitted downstream one at a time from a producer thread <em>as
 * discovery yields them</em>, so the first entry enters {@code where} evaluation while the
 * storage walk (or database cursor) is still in progress.
 *
 * <p>
 * This publisher owns the source stream's lifecycle: the stream is closed when iteration
 * finishes, when every subscriber has cancelled (early termination - closing is what cancels the
 * underlying discovery), or when iteration fails.
 */
final class EntrySourcePublisher {

  private static final Logger log = LoggerFactory.getLogger(EntrySourcePublisher.class);

  private final SubmissionPublisher<StorageEntry> publisher;
  private final Stream<StorageEntry> entries;
  private final ExecutorService executor;
  private final PipelineMetrics.StageMetrics metrics;

  EntrySourcePublisher(final Stream<StorageEntry> entries,
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
        Flow.Subscriber::onError);
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
    Thread.ofPlatform().start(() -> {
      try (entries) {

        final Iterator<StorageEntry> it = entries.iterator();
        while (it.hasNext()) {
          if (publisher.isClosed() || !publisher.hasSubscribers()) {
            log.debug("All subscribers are gone - halting source iteration.");
            metrics.earlyTerminated();
            break;
          }
          publisher.submit(it.next());
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

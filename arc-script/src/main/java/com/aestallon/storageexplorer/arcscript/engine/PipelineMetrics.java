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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Per-stage instrumentation accumulator of a single pipelined query execution.
 *
 * <p>
 * Stage counters are always maintained (they are two atomic increments per element - the yield
 * stage duration also drives the rendering time reported on the result set meta); the
 * {@code collectStageTimings} setting only controls whether the accumulated figures are attached to
 * the query result as {@link ArcScriptResult.PipelineStats}.
 */
final class PipelineMetrics {

  // the engine's pipeline code is kept monitor-free: virtual threads blocking on a contended
  // synchronized block pin their carrier on JDK 21 (pre-JEP 491), locks merely park them:
  private final java.util.concurrent.locks.ReentrantLock lock =
      new java.util.concurrent.locks.ReentrantLock();
  private final List<StageMetrics> stages = new ArrayList<>();

  StageMetrics register(final String name) {
    final var stage = new StageMetrics(name);
    lock.lock();
    try {
      stages.add(stage);
    } finally {
      lock.unlock();
    }
    return stage;
  }

  ArcScriptResult.PipelineStats toStats() {
    final List<ArcScriptResult.StageStats> stats;
    lock.lock();
    try {
      stats = stages.stream().map(StageMetrics::toStats).toList();
    } finally {
      lock.unlock();
    }
    return new ArcScriptResult.PipelineStats(stats);
  }

  static final class StageMetrics {

    private final String name;
    private final AtomicLong in = new AtomicLong();
    private final AtomicLong out = new AtomicLong();
    /** Nanotime of the first element this stage touched; 0 while untouched. */
    private final AtomicLong firstElementAt = new AtomicLong();
    private volatile long completedAt = 0L;
    private volatile boolean earlyTerminated = false;

    private StageMetrics(final String name) {
      this.name = name;
    }

    private void touch() {
      // stages overlap, so a from-pipeline-start measurement would make every stage's duration
      // approximately the total wall time; the span from a stage's FIRST element to its
      // completion is what allows meaningful per-stage attribution:
      if (firstElementAt.get() == 0L) {
        firstElementAt.compareAndSet(0L, System.nanoTime());
      }
    }

    void in() {
      touch();
      in.incrementAndGet();
    }

    void out() {
      touch();
      out.incrementAndGet();
    }

    void completed() {
      completedAt = System.nanoTime();
    }

    void earlyTerminated() {
      earlyTerminated = true;
    }

    long timeTaken() {
      final long first = firstElementAt.get();
      return (first == 0L || completedAt == 0L) ? -1L : completedAt - first;
    }

    private ArcScriptResult.StageStats toStats() {
      return new ArcScriptResult.StageStats(
          name,
          in.get(),
          out.get(),
          timeTaken(),
          firstElementAt.get(),
          completedAt,
          earlyTerminated);
    }

  }

}

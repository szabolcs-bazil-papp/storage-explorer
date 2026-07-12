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

import java.time.Duration;

/**
 * Tunable knobs of ArcScript query execution.
 *
 * <p>
 * Every parameter of the pipelined query engine is expressed here, primarily to allow performance
 * measurements, and down the line, user-facing fine-tuning. Acquire a baseline instance with
 * {@link #defaults()} and derive variants with {@link #toBuilder()}.
 *
 * @param engineMode which {@link QueryEngine} implementation executes {@code query}
 *     instructions
 * @param earlyTerminationEnabled when a {@code limit} is present without an {@code order}
 *     clause, cancel the upstream stages as soon as enough entries passed the {@code where}
 *     predicate
 * @param sourcePrefetch how many entries a pipeline stage initially requests from its upstream;
 *     as further demand is only signalled per completed unit of work, this also caps a stage's
 *     effective parallelism - stages therefore request
 *     {@code max(sourcePrefetch, stage concurrency)}
 * @param whereConcurrency maximum concurrent {@code where} predicate evaluations; {@code -1}
 *     means unbounded
 * @param yieldConcurrency maximum concurrent projection computations (sort key discovery and
 *     {@code show} column rendering); {@code -1} means unbounded
 * @param queueCapacity buffer capacity of each pipeline stage; saturated stages block their
 *     upstream (backpressure)
 * @param cacheMaxEntries maximum size of the load cache shared across the {@code where},
 *     {@code order} and {@code yield} phases of a single query; {@code -1} means unbounded
 * @param cacheExpireAfterAccess load cache entries not accessed for this duration become
 *     eligible for eviction; ignored for unbounded caches
 * @param unboundedSortWarnThreshold log a warning when an unbounded sort ({@code order} without
 *     {@code limit}) buffers more than this many entries; {@code -1} disables the warning
 * @param unboundedSortHardCap hard upper bound on entries retained by a sort which is not
 *     already bounded by an explicit query {@code limit}; when exceeded, the worst-sorting entries
 *     are discarded with a warning; never overrides an explicit query {@code limit}; {@code -1}
 *     disables the cap
 * @param collectStageTimings attach per-stage instrumentation to query results (counters are
 *     maintained either way; this only controls attachment)
 * @param queryTimeout last-resort watchdog on a single query execution: if the pipeline fails to
 *     deliver a terminal signal within this duration, the query fails instead of blocking its
 *     caller forever; {@code null} disables the watchdog
 */
public record QueryEngineSettings(
    EngineMode engineMode,
    boolean earlyTerminationEnabled,
    int sourcePrefetch,
    int whereConcurrency,
    int yieldConcurrency,
    int queueCapacity,
    long cacheMaxEntries,
    Duration cacheExpireAfterAccess,
    int unboundedSortWarnThreshold,
    int unboundedSortHardCap,
    boolean collectStageTimings,
    Duration queryTimeout) {

  public enum EngineMode { LEGACY, PIPELINED }

  public QueryEngineSettings {
    if (engineMode == null) {
      throw new IllegalArgumentException("engineMode cannot be null!");
    }
    if (sourcePrefetch < 1) {
      throw new IllegalArgumentException("sourcePrefetch must be positive: " + sourcePrefetch);
    }
    if (whereConcurrency < 1 && whereConcurrency != -1) {
      throw new IllegalArgumentException(
          "whereConcurrency must be positive or -1 (unbounded): " + whereConcurrency);
    }
    if (yieldConcurrency < 1 && yieldConcurrency != -1) {
      throw new IllegalArgumentException(
          "yieldConcurrency must be positive or -1 (unbounded): " + yieldConcurrency);
    }
    if (queueCapacity < 1) {
      throw new IllegalArgumentException("queueCapacity must be positive: " + queueCapacity);
    }
    if (cacheMaxEntries < 1L && cacheMaxEntries != -1L) {
      throw new IllegalArgumentException(
          "cacheMaxEntries must be positive or -1 (unbounded): " + cacheMaxEntries);
    }
    if (cacheMaxEntries > 0L
        && (cacheExpireAfterAccess == null
            || cacheExpireAfterAccess.isZero()
            || cacheExpireAfterAccess.isNegative())) {
      throw new IllegalArgumentException(
          "cacheExpireAfterAccess must be a positive duration for a bounded cache!");
    }
    if (queryTimeout != null && (queryTimeout.isZero() || queryTimeout.isNegative())) {
      throw new IllegalArgumentException(
          "queryTimeout must be a positive duration or null (disabled): " + queryTimeout);
    }
    if (unboundedSortWarnThreshold < 1 && unboundedSortWarnThreshold != -1) {
      throw new IllegalArgumentException(
          "unboundedSortWarnThreshold must be positive or -1 (disabled): "
          + unboundedSortWarnThreshold);
    }
    if (unboundedSortHardCap < 1 && unboundedSortHardCap != -1) {
      throw new IllegalArgumentException(
          "unboundedSortHardCap must be positive or -1 (disabled): " + unboundedSortHardCap);
    }
  }

  public static QueryEngineSettings defaults() {
    return builder().build();
  }

  public static Builder builder() {
    return new Builder();
  }

  public Builder toBuilder() {
    return new Builder()
        .engineMode(engineMode)
        .earlyTerminationEnabled(earlyTerminationEnabled)
        .sourcePrefetch(sourcePrefetch)
        .whereConcurrency(whereConcurrency)
        .yieldConcurrency(yieldConcurrency)
        .queueCapacity(queueCapacity)
        .cacheMaxEntries(cacheMaxEntries)
        .cacheExpireAfterAccess(cacheExpireAfterAccess)
        .unboundedSortWarnThreshold(unboundedSortWarnThreshold)
        .unboundedSortHardCap(unboundedSortHardCap)
        .collectStageTimings(collectStageTimings)
        .queryTimeout(queryTimeout);
  }

  public static final class Builder {

    private EngineMode engineMode = EngineMode.PIPELINED;
    private boolean earlyTerminationEnabled = true;
    private int sourcePrefetch = 256;
    private int whereConcurrency = Runtime.getRuntime().availableProcessors() * 4;
    private int yieldConcurrency = Runtime.getRuntime().availableProcessors() * 4;
    private int queueCapacity = 512;
    private long cacheMaxEntries = 10_000L;
    private Duration cacheExpireAfterAccess = Duration.ofMinutes(2L);
    private int unboundedSortWarnThreshold = 50_000;
    private int unboundedSortHardCap = -1;
    private boolean collectStageTimings = true;
    private Duration queryTimeout = Duration.ofHours(1L);

    private Builder() {}

    public Builder engineMode(EngineMode engineMode) {
      this.engineMode = engineMode;
      return this;
    }

    public Builder earlyTerminationEnabled(boolean earlyTerminationEnabled) {
      this.earlyTerminationEnabled = earlyTerminationEnabled;
      return this;
    }

    public Builder sourcePrefetch(int sourcePrefetch) {
      this.sourcePrefetch = sourcePrefetch;
      return this;
    }

    public Builder whereConcurrency(int whereConcurrency) {
      this.whereConcurrency = whereConcurrency;
      return this;
    }

    public Builder yieldConcurrency(int yieldConcurrency) {
      this.yieldConcurrency = yieldConcurrency;
      return this;
    }

    public Builder queueCapacity(int queueCapacity) {
      this.queueCapacity = queueCapacity;
      return this;
    }

    public Builder cacheMaxEntries(long cacheMaxEntries) {
      this.cacheMaxEntries = cacheMaxEntries;
      return this;
    }

    public Builder cacheExpireAfterAccess(Duration cacheExpireAfterAccess) {
      this.cacheExpireAfterAccess = cacheExpireAfterAccess;
      return this;
    }

    public Builder unboundedSortWarnThreshold(int unboundedSortWarnThreshold) {
      this.unboundedSortWarnThreshold = unboundedSortWarnThreshold;
      return this;
    }

    public Builder unboundedSortHardCap(int unboundedSortHardCap) {
      this.unboundedSortHardCap = unboundedSortHardCap;
      return this;
    }

    public Builder collectStageTimings(boolean collectStageTimings) {
      this.collectStageTimings = collectStageTimings;
      return this;
    }

    public Builder queryTimeout(Duration queryTimeout) {
      this.queryTimeout = queryTimeout;
      return this;
    }

    public QueryEngineSettings build() {
      return new QueryEngineSettings(
          engineMode,
          earlyTerminationEnabled,
          sourcePrefetch,
          whereConcurrency,
          yieldConcurrency,
          queueCapacity,
          cacheMaxEntries,
          cacheExpireAfterAccess,
          unboundedSortWarnThreshold,
          unboundedSortHardCap,
          collectStageTimings,
          queryTimeout);
    }

  }

}

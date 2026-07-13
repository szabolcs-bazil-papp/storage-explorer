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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Flow;
import java.util.concurrent.Semaphore;
import java.util.concurrent.SubmissionPublisher;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base of every intermediate stage of the pipelined query engine.
 *
 * <p>
 * A stage is a {@link Flow.Processor}: it consumes elements from its upstream under backpressure
 * (an initial {@code request(prefetch)}, then one further element requested per completed unit of
 * work), dispatches per-element work onto the shared virtual thread executor (bounded by an
 * optional {@link Semaphore}), and pushes results downstream through a {@link SubmissionPublisher}
 * the moment they are ready - elements never wait for their siblings.
 *
 * <p>
 * Downstream completion is signalled only once the upstream has completed (or has been cancelled)
 * <em>and</em> every dispatched unit of work has finished - the same completion semantics the
 * legacy executor achieved with a {@code CountDownLatch}, without a pipeline-wide barrier.
 *
 * <p>
 * Per-element failures are logged and swallowed (the element is dropped), mirroring the legacy
 * {@code AbstractEntryEvaluationExecutor} behaviour; only systemic failures propagate downstream
 * and fail the query.
 */
abstract class PipelineStage<IN, OUT> implements Flow.Processor<IN, OUT> {

  private static final Logger log = LoggerFactory.getLogger(PipelineStage.class);

  private final ExecutorService executor;
  private final SubmissionPublisher<OUT> downstream;
  private final Semaphore permits;
  private final long prefetch;
  private final AtomicLong inFlight = new AtomicLong();
  private final AtomicBoolean upstreamDone = new AtomicBoolean();
  private final AtomicBoolean cancelled = new AtomicBoolean();
  private final AtomicBoolean finished = new AtomicBoolean();
  private volatile Flow.Subscription subscription;

  protected final PipelineMetrics.StageMetrics metrics;

  protected PipelineStage(final ExecutorService executor,
                          final int queueCapacity,
                          final int concurrency,
                          final long prefetch,
                          final PipelineMetrics.StageMetrics metrics) {
    this.executor = executor;
    // if the downstream subscriber's callback throws (instead of routing failures through the
    // regular onError channel), SubmissionPublisher's default behaviour is to silently cancel the
    // subscription - severing the pipeline without any terminal signal ever reaching the result
    // collector. The handler re-routes such failures into the subscriber's onError so they
    // propagate down the chain and fail the query instead of hanging it:
    this.downstream = new SubmissionPublisher<>(
        executor,
        queueCapacity,
        Flow.Subscriber::onError);
    this.permits = concurrency > 0 ? new Semaphore(concurrency) : null;
    // demand never exceeds the initial request (one further element is requested only per
    // completed unit of work), so a prefetch below the concurrency bound would silently cap the
    // stage's parallelism:
    this.prefetch = Math.max(Math.max(1, prefetch), concurrency);
    this.metrics = metrics;
  }

  @Override
  public final void subscribe(final Flow.Subscriber<? super OUT> subscriber) {
    downstream.subscribe(subscriber);
  }

  @Override
  public final void onSubscribe(final Flow.Subscription subscription) {
    this.subscription = subscription;
    subscription.request(prefetch);
  }

  @Override
  public final void onNext(final IN item) {
    if (cancelled.get()) {
      return;
    }

    metrics.in();
    inFlight.incrementAndGet();
    try {
      dispatch(item);
    } catch (final RuntimeException e) {
      // most plausibly a RejectedExecutionException in an executor shutdown race - the increment
      // above must be undone, and the failure must reach the collector:
      inFlight.decrementAndGet();
      cancelled.set(true);
      downstream.closeExceptionally(e);
    }
  }

  private void dispatch(final IN item) {
    executor.submit(() -> {
      try {

        if (permits != null) {
          permits.acquire();
        }
        try {
          if (!cancelled.get()) {
            process(item);
          }
        } finally {
          if (permits != null) {
            permits.release();
          }
        }

      } catch (final InterruptedException e) {
        log.warn(e.getMessage(), e);
        Thread.currentThread().interrupt();
      } catch (final Exception e) {
        log.error("Error occurred during evaluation of [ {} ]: {}", item, e.getMessage());
        log.debug(e.getMessage(), e);
      } finally {
        if (inFlight.decrementAndGet() == 0L && (upstreamDone.get() || cancelled.get())) {
          finish();
        }
        if (!cancelled.get()) {
          final var s = subscription;
          if (s != null) {
            s.request(1L);
          }
        }
      }
    });
  }

  @Override
  public final void onError(final Throwable throwable) {
    // quiesce the stage: in-flight workers stop processing and stop requesting further demand,
    // instead of noisily emitting into a dead publisher:
    cancelled.set(true);
    downstream.closeExceptionally(throwable);
  }

  @Override
  public final void onComplete() {
    upstreamDone.set(true);
    if (inFlight.get() == 0L) {
      finish();
    }
  }

  /**
   * Pushes an element downstream, blocking if the downstream buffer is saturated (backpressure).
   */
  protected final void emit(final OUT out) {
    metrics.out();
    downstream.submit(out);
  }

  /**
   * Cancels the upstream subscription: no further elements are consumed or processed, and the
   * stage completes as soon as its in-flight work has drained.
   */
  protected final void cancelUpstream() {
    if (cancelled.compareAndSet(false, true)) {
      metrics.earlyTerminated();
      final var s = subscription;
      if (s != null) {
        s.cancel();
      }
      if (inFlight.get() == 0L) {
        finish();
      }
    }
  }

  private void finish() {
    if (!finished.compareAndSet(false, true)) {
      return;
    }

    if (downstream.isClosed()) {
      // the stage already failed through the onError channel; there is nothing to flush:
      return;
    }

    try {
      onUpstreamComplete();
    } catch (final Throwable t) {
      // Throwable, not Exception: an OutOfMemoryError while flushing an unbounded sort buffer is
      // the single likeliest systemic failure of a huge query - it MUST reach the collector, or
      // the caller blocks forever on a result that never comes:
      log.error(t.getMessage(), t);
      downstream.closeExceptionally(t);
      if (t instanceof Error error) {
        throw error;
      }
      return;
    }
    metrics.completed();
    downstream.close();
  }

  /**
   * Per-element work; invoked concurrently. Implementations {@link #emit(Object)} zero or more
   * elements per invocation.
   */
  protected abstract void process(final IN item) throws Exception;

  /**
   * Invoked exactly once, after the upstream completed (or was cancelled) and all in-flight work
   * has drained, before downstream completion is signalled. Buffering stages flush here.
   */
  protected void onUpstreamComplete() throws Exception {}

}

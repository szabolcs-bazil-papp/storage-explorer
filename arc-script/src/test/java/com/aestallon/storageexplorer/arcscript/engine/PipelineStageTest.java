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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Drives {@link PipelineStage} directly to pin its failure semantics: per-element failures must
 * drop the element and let the pipeline complete, while systemic failures (a flush failure in
 * {@code onUpstreamComplete}, or a downstream subscriber throwing out of its callbacks) must fail
 * the pipeline with a terminal signal - never hang it.
 */
class PipelineStageTest {

  private static final long TIMEOUT_S = 10L;

  private ExecutorService executor;

  @BeforeEach
  void setUp() {
    executor = Executors.newVirtualThreadPerTaskExecutor();
  }

  @AfterEach
  void tearDown() {
    executor.shutdownNow();
  }

  private PipelineStage<Integer, Integer> passthroughStage(final Consumer<Integer> onProcess,
                                                           final Runnable onFlush) {
    return new PipelineStage<>(executor, 16, 4, 8, new PipelineMetrics().register("test")) {

      @Override
      protected void process(final Integer item) {
        onProcess.accept(item);
        emit(item);
      }

      @Override
      protected void onUpstreamComplete() {
        onFlush.run();
      }

    };
  }

  private static final class CollectingSubscriber implements Flow.Subscriber<Integer> {

    private final List<Integer> received = new ArrayList<>();
    private final CompletableFuture<List<Integer>> future = new CompletableFuture<>();

    @Override
    public void onSubscribe(final Flow.Subscription subscription) {
      subscription.request(Long.MAX_VALUE);
    }

    @Override
    public void onNext(final Integer item) {
      received.add(item);
    }

    @Override
    public void onError(final Throwable throwable) {
      future.completeExceptionally(throwable);
    }

    @Override
    public void onComplete() {
      future.complete(received);
    }

  }

  private CollectingSubscriber drive(final PipelineStage<Integer, Integer> stage,
                                     final int items) {
    final var collector = new CollectingSubscriber();
    try (final var source = new SubmissionPublisher<Integer>(executor, 16)) {
      source.subscribe(stage);
      stage.subscribe(collector);
      for (int i = 0; i < items; i++) {
        source.submit(i);
      }
    }
    return collector;
  }

  @Test
  void perElementFailures_dropTheElementAndCompleteThePipeline() throws Exception {
    final var stage = passthroughStage(
        i -> {
          if (i % 2 == 0) {
            throw new IllegalStateException("even numbers are unacceptable");
          }
        },
        () -> {});

    final var collector = drive(stage, 10);
    final List<Integer> received = collector.future.get(TIMEOUT_S, TimeUnit.SECONDS);
    assertThat(received).containsExactlyInAnyOrder(1, 3, 5, 7, 9);
  }

  @Test
  void flushFailure_failsThePipelineWithTheCause_insteadOfHanging() {
    final var stage = passthroughStage(
        i -> {},
        () -> {
          throw new IllegalStateException("flush failure");
        });

    final var collector = drive(stage, 5);
    assertThatThrownBy(() -> collector.future.get(TIMEOUT_S, TimeUnit.SECONDS))
        .isInstanceOf(ExecutionException.class)
        .cause()
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("flush failure");
  }

  @Test
  void throwingDownstreamSubscriber_failsThePipeline_insteadOfSilentDetachment() {
    final var stage = passthroughStage(i -> {}, () -> {});
    final var future = new CompletableFuture<List<Integer>>();
    final var throwingSubscriber = new Flow.Subscriber<Integer>() {

      @Override
      public void onSubscribe(final Flow.Subscription subscription) {
        subscription.request(Long.MAX_VALUE);
      }

      @Override
      public void onNext(final Integer item) {
        throw new UnsupportedOperationException("cannot handle " + item);
      }

      @Override
      public void onError(final Throwable throwable) {
        future.completeExceptionally(throwable);
      }

      @Override
      public void onComplete() {
        future.complete(List.of());
      }

    };

    try (final var source = new SubmissionPublisher<Integer>(executor, 16)) {
      source.subscribe(stage);
      stage.subscribe(throwingSubscriber);
      for (int i = 0; i < 5; i++) {
        source.submit(i);
      }
    }

    assertThatThrownBy(() -> future.get(TIMEOUT_S, TimeUnit.SECONDS))
        .isInstanceOf(ExecutionException.class)
        .cause()
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void cancellingUpstreamMidStream_completesThePipelineWithTheElementsEmittedSoFar()
      throws Exception {
    final var stage = new PipelineStage<Integer, Integer>(
        executor, 16, 1, 1, new PipelineMetrics().register("test")) {

      @Override
      protected void process(final Integer item) {
        emit(item);
        if (item >= 2) {
          cancelUpstream();
        }
      }

    };

    final var collector = drive(stage, 1_000);
    final List<Integer> received = collector.future.get(TIMEOUT_S, TimeUnit.SECONDS);
    assertThat(received).isNotEmpty().hasSizeLessThan(1_000);
  }

}

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
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;

/**
 * Terminal subscriber of the query pipeline: gathers fully projected rows and reassembles them into
 * their tagged order once the pipeline completes.
 */
final class ResultCollector implements Flow.Subscriber<IndexedRow> {

  private final List<IndexedRow> rows = new ArrayList<>();
  private final CompletableFuture<List<ArcScriptResult.QueryResultRow>> future =
      new CompletableFuture<>();

  CompletableFuture<List<ArcScriptResult.QueryResultRow>> future() {
    return future;
  }

  @Override
  public void onSubscribe(final Flow.Subscription subscription) {
    // collection is O(1) per element - no backpressure needed at the very end of the pipeline:
    subscription.request(Long.MAX_VALUE);
  }

  @Override
  public void onNext(final IndexedRow item) {
    // onNext invocations are serialized per subscriber, no synchronization required:
    rows.add(item);
  }

  @Override
  public void onError(final Throwable throwable) {
    future.completeExceptionally(throwable);
  }

  @Override
  public void onComplete() {
    rows.sort(Comparator.comparingLong(IndexedRow::seq));
    future.complete(rows.stream().map(IndexedRow::row).toList());
  }

}

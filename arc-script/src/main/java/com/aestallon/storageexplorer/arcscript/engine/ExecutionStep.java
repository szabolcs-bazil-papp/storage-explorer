package com.aestallon.storageexplorer.arcscript.engine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import com.aestallon.storageexplorer.arcscript.internal.query.QueryConditionImpl;
import com.aestallon.storageexplorer.core.service.StorageInstanceExaminer;

abstract class ExecutionStep {

  protected static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(Runtime
      .getRuntime()
      .availableProcessors());

  protected final ExecutionContext ctx;
  protected final DataStream input;

  public ExecutionStep(ExecutionContext ctx, DataStream input) {
    this.ctx = ctx;
    this.input = input;
  }

  abstract DataStream execute() throws InterruptedException;

}


abstract class MappingExecutionStep extends ExecutionStep {


  public MappingExecutionStep(ExecutionContext ctx, DataStream input) {
    super(ctx, input);
  }

  @Override
  DataStream execute() {
    final var buffer = new LinkedBlockingQueue<DataRow>();
    final var terminator =
        new CompletableFuture<Void>().thenRun(() -> buffer.offer(Terminal.TERMINAL));
    EXECUTOR.submit(() -> {
      DataRow row;
      int seen = 0;
      AtomicInteger size = new AtomicInteger(Integer.MAX_VALUE);
      AtomicInteger processed = new AtomicInteger(0);
      while (true) {
        try {
          if ((row = input.next()) == Terminal.TERMINAL) {
            size.set(seen);
            break;
          }

        } catch (InterruptedException e) {
          size.set(seen);
          throw new RuntimeException(e);
        }

        final var curr = row;
        seen++;
        Thread.ofVirtual().start(() -> {
          final var out = doMap(curr);
          if (out != null) {
            buffer.offer(out);
          }

          if (processed.incrementAndGet() >= size.get()) {
            terminator.complete(null);
          }

        });
      }
    });

    return downstream(buffer);
  }

  protected abstract DataRow doMap(DataRow row);

  protected abstract DataStream downstream(LinkedBlockingQueue<DataRow> buffer);

}


final class FilterExecutionStep extends MappingExecutionStep {

  private final QueryConditionImpl condition;
  private final ProjectionDef projectionDef;

  public FilterExecutionStep(ExecutionContext ctx, DataStream input, QueryConditionImpl condition) {
    super(ctx, input);
    this.condition = condition;
    projectionDef = input instanceof ProjectionStream pStream ? pStream.projectionDef() : null;

  }

  @Override
  protected DataRow doMap(DataRow row) {
    final var evaluator = new ConditionEvaluator(
        ctx.cachedExaminer(),
        row,
        condition,
        (projectionDef != null && row instanceof ProjectionRow p)
            ? arc -> p.col(projectionDef.indexOf(arc))
            : null);
    return evaluator.evaluate() ? row : null;
  }

  @Override
  protected DataStream downstream(LinkedBlockingQueue<DataRow> buffer) {
    if (input instanceof ProjectionStream pStream) {
      return new ProjectionStream(buffer, pStream.projectionDef());
    } else {
      return new BufferedDataStream(buffer);
    }
  }

}


final class ProjectionExecutionStep extends MappingExecutionStep {

  private final ProjectionDef projectionDef;
  private final UnaryOperator<DataRow> mapper;
  public ProjectionExecutionStep(ExecutionContext ctx,
                                 DataStream input,
                                 ProjectionDef projectionDef) {
    super(ctx, input);
    this.projectionDef = projectionDef;
    if (input instanceof ProjectionStream pStream) {
      // Input is already a projection: in the best case scenario we just have to shuffle the column
      // definition indices; in the worse case we may have to discover the missing ones (if an entry
      // is backing the row).
      final var missingColumns = new ArrayList<ColumnDef>();
      final var remapping = new int[projectionDef.cols().length];
      Arrays.fill(remapping, -1);
      for (int i = 0; i < remapping.length; i++) {
        final var col = projectionDef.cols()[i];
        final int oldIdx = pStream.projectionDef().indexOf(col.arc());
        if (oldIdx < 0) {
          missingColumns.add(col);
        } else  {
          remapping[i] = oldIdx;
        }
      }
      final Function<EntryRow, StorageInstanceExaminer.PropertyDiscoveryResult[]> d;
      if (!missingColumns.isEmpty()) {
        final var missingProjDef = new ProjectionDef(missingColumns.toArray(new ColumnDef[0]));
        d = eRow -> ctx.cachedExaminer().examine(eRow.entry(), missingProjDef);
      } else {
        d = null;
      }
      mapper = row -> {
        int nuIdx = 0;
        final StorageInstanceExaminer.PropertyDiscoveryResult[] nuCols;
        if (d != null) {
          if (!(row instanceof EntryRow entryRow)) {
            throw new IllegalStateException("Trying to fill missing rows on non-empty row");
          }
          nuCols = d.apply(entryRow);
        } else  {
          nuCols = new StorageInstanceExaminer.PropertyDiscoveryResult[0];
        }
        final var cols = new StorageInstanceExaminer.PropertyDiscoveryResult[remapping.length];
        for (int i = 0; i < remapping.length; i++) {
          final int oldIdx = remapping[i];
          if (oldIdx >= 0) {
            cols[i] = ((ProjectionRow) row).col(oldIdx);
          } else {
            cols[i] = nuCols[nuIdx++];
          }
        }
        if (row instanceof EntryRow entryRow) {
          return new EntryBasedProjectionRow(entryRow.entry(),  cols);
        } else {
          return new SimpleProjectionRow(cols);
        }
      };
    } else {
      // Input must be an entry row (there is no projection):
      mapper = row -> new EntryBasedProjectionRow(
          ((EntryRow) row).entry(),
          ctx.cachedExaminer(),
          projectionDef);
    }
  }

  @Override
  protected DataRow doMap(DataRow row) {
    return mapper.apply(row);
  }

  @Override
  protected DataStream downstream(LinkedBlockingQueue<DataRow> buffer) {
    return new ProjectionStream(buffer, projectionDef);
  }

}

enum SortDirection { ASC, DESC }

record SortDef(String identifier, SortDirection direction) {}

abstract class AggregatingExecutionStep<T> extends ExecutionStep {

  public AggregatingExecutionStep(ExecutionContext ctx, DataStream input) {
    super(ctx, input);
  }

  @Override
  DataStream execute() throws InterruptedException {
    final T aggregation = prepare();
    DataRow row;
    while ((row = input.next()) != Terminal.TERMINAL) {
      accept(row, aggregation);
    }
    return transform(aggregation);
  }

  protected abstract T prepare();

  protected abstract void accept(DataRow row, T aggregation);

  protected abstract DataStream transform(T aggregation);

}

final class SortExecutionStep extends AggregatingExecutionStep<List<DataRow>> {

  private final SortDef[] sortDefs;
  private final ProjectionDef projectionDef;

  public SortExecutionStep(ExecutionContext ctx, DataStream input, SortDef[] sortDefs) {
    super(ctx, input);
    if (!(input instanceof ProjectionStream pStream)) {
      throw new IllegalArgumentException("Input must be a ProjectionStream");
    }

    this.projectionDef = pStream.projectionDef();
    this.sortDefs = sortDefs;
  }

  @Override
  protected List<DataRow> prepare() {
    return new ArrayList<>();
  }

  @Override
  protected void accept(DataRow row, List<DataRow> aggregation) {
    aggregation.add(row);
  }

  @Override
  protected DataStream transform(List<DataRow> aggregation) {
    aggregation.sort(byDefinition());
    return new ProjectionStream(new LinkedBlockingQueue<>(aggregation), projectionDef);
  }

  private Comparator<DataRow> byDefinition() {
    return (a, b) -> 0;
  }

}

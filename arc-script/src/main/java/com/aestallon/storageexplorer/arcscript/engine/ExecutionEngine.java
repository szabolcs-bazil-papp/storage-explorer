package com.aestallon.storageexplorer.arcscript.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;
import com.aestallon.storageexplorer.arcscript.internal.query.QueryConditionImpl;
import com.aestallon.storageexplorer.core.model.entry.ObjectEntry;
import com.aestallon.storageexplorer.core.model.instance.StorageInstance;

class ExecutionEngine {

  private final ExecutionContext ctx;
  private final List<Function<DataStream, ExecutionStep>> steps = new ArrayList<>();

  ExecutionEngine(StorageInstance instance) {
    final var examiner = instance.examiner();
    ctx = new ExecutionContext(new CachedExaminer(examiner), true);
  }

  ExecutionEngine project(ProjectionDef projectionDef) {
    steps.add(data -> new ProjectionExecutionStep(ctx, data, projectionDef));
    return this;
  }

  ExecutionEngine filter(QueryConditionImpl condition) {
    steps.add(data -> new FilterExecutionStep(ctx, data, condition));
    return this;
  }

  ExecutionEngine sort(SortDef[] sortDefs) {
    steps.add(data -> new SortExecutionStep(ctx, data, sortDefs));
    return this;
  }

  List<DataRow> execute(Stream<ObjectEntry> entryStream) throws InterruptedException {
    DataStream data = new ScanSourcedDataStream(entryStream, ExecutionStep.EXECUTOR);
    for (final var step : steps) {
      data = step.apply(data).execute();
    }

    final List<DataRow> rows = new ArrayList<>();
    DataRow row;
    while ((row = data.next()) != Terminal.TERMINAL) {
      rows.add(row);
    }
    return rows;
  }

}

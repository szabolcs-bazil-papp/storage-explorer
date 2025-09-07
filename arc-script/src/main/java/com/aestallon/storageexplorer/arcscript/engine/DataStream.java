package com.aestallon.storageexplorer.arcscript.engine;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Stream;
import com.aestallon.storageexplorer.core.model.entry.ObjectEntry;

sealed interface DataStream {

  DataRow next() throws InterruptedException;

}

final class ScanSourcedDataStream implements DataStream {

  private final LinkedBlockingQueue<DataRow> buffer = new LinkedBlockingQueue<>();

  ScanSourcedDataStream(Stream<ObjectEntry> input, ExecutorService executor) {
    executor.submit(() -> {
      try (input) {
        input.map(SimpleEntryRow::new).forEach(buffer::add);
      } finally {
        buffer.add(Terminal.TERMINAL);
      }
    });
  }

  @Override
  public DataRow next() throws InterruptedException {
    return buffer.take();
  }

}

record ColumnDef(ColumnType columnType, String arc, String alias) {}

record ProjectionDef(ColumnDef[] cols) {

  int indexOf(String arc) {
    for (int i = 0; i < cols.length; i++) {
      if (cols[i].arc().equals(arc)) {
        return i;
      }
    }
    return -1;
  }

}

sealed class BufferedDataStream implements DataStream {
  private final BlockingQueue<DataRow> buffer;

  BufferedDataStream(BlockingQueue<DataRow> buffer) {
    this.buffer = buffer;
  }

  @Override
  public DataRow next() throws InterruptedException {
    return buffer.take();
  }

}


final class ProjectionStream extends BufferedDataStream {

  private final ProjectionDef projectionDef;

  ProjectionStream(BlockingQueue<DataRow> buffer,
                   ProjectionDef projectionDef) {
    super(buffer);
    this.projectionDef = projectionDef;
  }

  public ProjectionDef projectionDef() {
    return projectionDef;
  }

}

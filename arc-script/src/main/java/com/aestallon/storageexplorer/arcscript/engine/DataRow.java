package com.aestallon.storageexplorer.arcscript.engine;

import java.util.Iterator;
import com.aestallon.storageexplorer.core.model.entry.ObjectEntry;
import com.aestallon.storageexplorer.core.service.StorageInstanceExaminer;

sealed interface DataRow {}

record Terminal() implements DataRow {
  static final Terminal TERMINAL = new Terminal();
}

sealed interface EntryRow extends DataRow {

  ObjectEntry entry();

}

record SimpleEntryRow(ObjectEntry entry) implements EntryRow {}

enum ColumnType {
  STRING {
    @Override
    boolean matches(StorageInstanceExaminer.PropertyDiscoveryResult result) {
      return result instanceof StorageInstanceExaminer.None || result instanceof StorageInstanceExaminer.StringFound;
    }
  },
  NUMBER {
    @Override
    boolean matches(StorageInstanceExaminer.PropertyDiscoveryResult result) {
      return result instanceof StorageInstanceExaminer.None || result instanceof StorageInstanceExaminer.NumberFound;
    }
  },
  BOOLEAN {
    @Override
    boolean matches(StorageInstanceExaminer.PropertyDiscoveryResult result) {
      return result instanceof StorageInstanceExaminer.None || result instanceof StorageInstanceExaminer.BooleanFound;
    }
  },
  TEMPORAL {
    @Override
    boolean matches(StorageInstanceExaminer.PropertyDiscoveryResult result) {
      return false; // TODO: Implement!
    }
  },
  LIST {
    @Override
    boolean matches(StorageInstanceExaminer.PropertyDiscoveryResult result) {
      return result instanceof StorageInstanceExaminer.None || result instanceof StorageInstanceExaminer.ListFound;
    }
  },
  OBJECT {
    @Override
    boolean matches(StorageInstanceExaminer.PropertyDiscoveryResult result) {
      return result instanceof StorageInstanceExaminer.None || result instanceof StorageInstanceExaminer.ComplexFound;
    }
  },
  ANY {
    @Override
    boolean matches(StorageInstanceExaminer.PropertyDiscoveryResult result) {
      return true;
    }
  };

  abstract boolean matches(StorageInstanceExaminer.PropertyDiscoveryResult result);
}

sealed interface ProjectionRow extends DataRow,Iterable<StorageInstanceExaminer.PropertyDiscoveryResult> {

  StorageInstanceExaminer.PropertyDiscoveryResult col(int idx);

}


record EntryBasedProjectionRow(
    ObjectEntry entry,
    StorageInstanceExaminer.PropertyDiscoveryResult[] cols) implements ProjectionRow, EntryRow {

  EntryBasedProjectionRow(ObjectEntry entry, CachedExaminer examiner, ProjectionDef projectionDef) {
    this(entry, examiner.examine(entry, projectionDef));
  }

  @Override
  public StorageInstanceExaminer.PropertyDiscoveryResult col(int idx) {
    return cols[idx];
  }

  @Override
  public Iterator<StorageInstanceExaminer.PropertyDiscoveryResult> iterator() {
    return new Iterator<>() {
      int i = 0;

      @Override
      public boolean hasNext() {
        return i < cols.length;
      }

      @Override
      public StorageInstanceExaminer.PropertyDiscoveryResult next() {
        return cols[i++];
      }
    };
  }
}

record SimpleProjectionRow(StorageInstanceExaminer.PropertyDiscoveryResult[] cols) implements ProjectionRow {

  @Override
  public StorageInstanceExaminer.PropertyDiscoveryResult col(int idx) {
    return cols[idx];
  }

  @Override
  public Iterator<StorageInstanceExaminer.PropertyDiscoveryResult> iterator() {
    return new Iterator<>() {
      int i = 0;
      @Override
      public boolean hasNext() {
        return i < cols.length;
      }

      @Override
      public StorageInstanceExaminer.PropertyDiscoveryResult next() {
        return cols[i++];
      }
    };
  }

}



package com.aestallon.storageexplorer.core.engine;

public final class StorageEngineException extends RuntimeException {

  private final StorageEngineErr err;

  public StorageEngineException(StorageEngineErr err) {
    super(err.msg());
    this.err = err;
  }

  public StorageEngineErr getErr() {
    return err;
  }

}

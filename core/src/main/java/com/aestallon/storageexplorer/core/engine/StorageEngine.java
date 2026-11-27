package com.aestallon.storageexplorer.core.engine;

public interface StorageEngine extends AutoCloseable {

  @Override
  void close();


}

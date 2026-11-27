package com.aestallon.storageexplorer.core.engine;

public sealed interface StorageEngineErr {

  int code();

  String msg();

  record InvalidUri(int code, String msg, String uri) implements StorageEngineErr {}
  record NotFound(int code, String msg) implements StorageEngineErr {}
  record FormatError(int code, String msg) implements StorageEngineErr {}
  record NoData(int code, String msg) implements StorageEngineErr {}
  record MissingEncriptionKey(int code, String msg) implements StorageEngineErr {}
  record Unsupported(int code, String msg) implements StorageEngineErr {}
  record SqlErr(int code, String msg, int sqlCode) implements StorageEngineErr {}
  record InDoubt(int code, String msg) implements StorageEngineErr {}
  record Internal(int code, String msg) implements StorageEngineErr {}

}

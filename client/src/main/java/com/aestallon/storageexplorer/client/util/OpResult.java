package com.aestallon.storageexplorer.client.util;

public sealed interface OpResult {

  static OpResult ok(String title, String msg) {
    return new Ok(title, msg);
  }

  static OpResult err(String title, String msg) {
    return new Err.Generic(title, msg);
  }

  static OpResult err(String title, Exception e) {
    return new Err.Exc(title, e);
  }

  record Ok(String title, String msg) implements OpResult {}


  sealed interface Err extends OpResult {

    String title();

    String msg();

    record Generic(String title, String msg) implements Err {}


    record Exc(String title, Exception e) implements Err {

      @Override
      public String msg() {
        return e.getMessage();
      }

    }

  }

}

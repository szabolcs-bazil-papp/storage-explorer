package com.aestallon.storageexplorer.client.util;

public sealed interface OpResult {

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

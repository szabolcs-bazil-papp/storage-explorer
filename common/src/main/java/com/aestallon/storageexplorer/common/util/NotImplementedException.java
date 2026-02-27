package com.aestallon.storageexplorer.common.util;

public class NotImplementedException extends RuntimeException {

  public static NotImplementedException ofMethod(String methodName) {
    return new NotImplementedException(String.format("Method %s is not implemented!", methodName));
  }

  public NotImplementedException(String message) {
    super(message);
  }
  
}

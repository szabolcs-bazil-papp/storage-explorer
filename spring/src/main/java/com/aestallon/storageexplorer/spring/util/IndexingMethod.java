package com.aestallon.storageexplorer.spring.util;

import com.google.common.base.Strings;

public enum IndexingMethod {
  FULL, SURFACE, NONE;

  public static IndexingMethod parse(final String s) {
    if (Strings.isNullOrEmpty(s))
      return NONE;
    if (s.equalsIgnoreCase("FULL"))
      return FULL;
    if (s.equalsIgnoreCase("SURFACE"))
      return SURFACE;
    return NONE;
  }
}

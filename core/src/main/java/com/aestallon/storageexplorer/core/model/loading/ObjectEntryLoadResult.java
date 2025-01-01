package com.aestallon.storageexplorer.core.model.loading;

import java.util.List;
import java.util.Map;

public sealed interface ObjectEntryLoadResult permits
    ObjectEntryLoadResult.Err,
    ObjectEntryLoadResult.SingleVersion,
    ObjectEntryLoadResult.MultiVersion {

  boolean isOk();

  default boolean isErr() {return !isOk();}

  record Err(String msg) implements ObjectEntryLoadResult {

    @Override
    public boolean isOk() {
      return false;
    }

  }


  record SingleVersion(ObjectEntryMeta meta, Map<String, Object> objectAsMap, String oamStr)
      implements ObjectEntryLoadResult {

    @Override
    public boolean isOk() {
      return true;
    }

  }


  record MultiVersion(List<SingleVersion> versions) implements ObjectEntryLoadResult {

    @Override
    public boolean isOk() {
      return true;
    }

  }
}

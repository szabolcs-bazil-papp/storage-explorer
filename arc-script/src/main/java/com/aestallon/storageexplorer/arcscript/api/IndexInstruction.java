package com.aestallon.storageexplorer.arcscript.api;

import com.aestallon.storageexplorer.core.model.instance.dto.IndexingStrategyType;

public interface IndexInstruction extends Instruction {
  
  void schemas(String... schemas);
  
  void types(String... types);
  
  void method(IndexingStrategyType method);
  
}

package com.aestallon.storageexplorer.arcscript.api;

import com.aestallon.storageexplorer.arcscript.internal.Instruction;
import com.aestallon.storageexplorer.core.model.instance.dto.IndexingStrategyType;

public interface IndexInstruction {
  
  void schemas(String... schemas);
  
  void types(String... types);
  
  void method(IndexingStrategyType method);
  
}

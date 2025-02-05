package com.aestallon.storageexplorer.arcscript.api;

import groovy.lang.Closure;

public interface ArcScript {

  QueryInstruction query(Closure closure);
  
  IndexInstruction index(Closure closure);
  
  UpdateInstruction update(Closure closure);
  
}

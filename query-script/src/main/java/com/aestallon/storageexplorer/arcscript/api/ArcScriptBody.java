package com.aestallon.storageexplorer.arcscript.api;

import groovy.lang.Closure;

public interface ArcScriptBody {

  QueryInstruction query(Closure closure);
}
